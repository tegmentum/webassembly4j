package ai.tegmentum.webassembly4j.provider.endive;

import ai.tegmentum.webassembly4j.api.Component;
import ai.tegmentum.webassembly4j.api.ComponentInstance;
import ai.tegmentum.webassembly4j.api.LinkingContext;
import ai.tegmentum.webassembly4j.api.WitHostFunctionDefinition;
import ai.tegmentum.webassembly4j.api.exception.InstantiationException;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import run.tegmentum.wasmcm.endive.HostCallbackDispatcher;
import run.tegmentum.wasmcm.endive.HostCallbackException;
import run.tegmentum.wasmcm.endive.WasmcmRuntimeGuestLoader;

/**
 * SPI-facing {@link Component} that layers the Component Model on top of the
 * unmodified Endive core-Wasm engine via the {@code wasmcm_runtime_guest.wasm}
 * portable Rust runtime.
 *
 * <p>The heavy lifting happens in {@link WasmcmRuntimeGuestLoader}: the CM
 * component bytes have already been parsed through the guest by the time this
 * adapter is constructed (see {@link EndiveEngineAdapter#loadComponent(byte[])})
 * — this class just adapts the {@link Component#instantiate(LinkingContext)}
 * surface onto {@code wasmcm_instantiate_v2} /
 * {@code wasmcm_instantiate_with_imports_v2} and hands back an
 * {@link EndiveComponentInstanceAdapter}.
 *
 * <h2>Linking context translation</h2>
 *
 * <p>{@link LinkingContext#witHostFunctions()} entries are grouped by their
 * WIT-path interface prefix (everything before the {@code '#'}), each group
 * registered as a host provider via
 * {@link WasmcmRuntimeGuestLoader#registerHostProvider(String, int)}. A single
 * {@link HostCallbackDispatcher} per provider routes the guest's
 * {@code wasmcm_host_call} extern into the caller's Java implementations by
 * function index.
 *
 * <p>Function-index order matches the order in which the caller added the
 * {@link WitHostFunctionDefinition}s to the linking context — the WIT interface
 * is not read back from the component here, so the caller controls the mapping.
 * This is stable and deterministic; callers building against a specific WIT
 * world should add their host functions in the WIT interface's declared order.
 *
 * <h2>Note on runtime-guest gaps at this milestone</h2>
 *
 * <p>Registering a host provider only records intent inside the runtime guest;
 * the Wall 2 chain that routes a {@code canon lower} against a component-level
 * import through {@code wasmcm_host_call} is a work in progress on the wasm-cm
 * Rust side (see {@code HostCallbackDispatcher} javadoc for the specific gap).
 * When a component that imports interfaces the runtime cannot yet route is
 * instantiated, {@code wasmcm_instantiate_with_imports_v2} surfaces
 * {@code HOST_ERR_UNLINKABLE} — this adapter re-throws as
 * {@link InstantiationException} with the guest's diagnostic message, matching
 * the "no fake success" contract.
 */
final class EndiveComponentAdapter implements Component {

    private final WasmcmRuntimeGuestLoader loader;
    private final long componentHandle;
    private final boolean ownsLoader;
    private volatile boolean closed;

    EndiveComponentAdapter(
            WasmcmRuntimeGuestLoader loader, long componentHandle, boolean ownsLoader) {
        this.loader = loader;
        this.componentHandle = componentHandle;
        this.ownsLoader = ownsLoader;
    }

    @Override
    public ComponentInstance instantiate() {
        return instantiate((LinkingContext) null);
    }

    @Override
    public ComponentInstance instantiate(LinkingContext linkingContext) {
        checkOpen();
        try {
            long instanceHandle;
            if (linkingContext == null) {
                instanceHandle = loader.instantiate(componentHandle);
            } else {
                Map<String, Long> imports = registerImports(linkingContext);
                if (imports.isEmpty()) {
                    instanceHandle = loader.instantiate(componentHandle);
                } else {
                    instanceHandle = loader.instantiateWithImports(componentHandle, imports);
                }
            }
            return new EndiveComponentInstanceAdapter(loader, instanceHandle);
        } catch (IllegalStateException e) {
            throw new InstantiationException(
                    "Failed to instantiate Component Model component via wasmcm runtime guest: "
                            + e.getMessage(),
                    e);
        }
    }

    /**
     * Bind each unique WIT-path interface prefix declared by the linking
     * context's {@link LinkingContext#witHostFunctions()} to a provider handle
     * and route the guest's {@code wasmcm_host_call} extern to the caller's Java
     * implementations. Function index within a provider is the caller's
     * insertion order.
     */
    private Map<String, Long> registerImports(LinkingContext linkingContext) {
        List<WitHostFunctionDefinition> defs = linkingContext.witHostFunctions();
        // Group by interface prefix (the segment before the final '#').
        Map<String, java.util.List<WitHostFunctionDefinition>> byInterface = new LinkedHashMap<>();
        for (WitHostFunctionDefinition def : defs) {
            String iface = interfacePrefix(def.witPath());
            byInterface.computeIfAbsent(iface, k -> new java.util.ArrayList<>()).add(def);
        }
        Map<String, Long> providers = new LinkedHashMap<>();
        for (Map.Entry<String, java.util.List<WitHostFunctionDefinition>> e : byInterface.entrySet()) {
            String iface = e.getKey();
            java.util.List<WitHostFunctionDefinition> funcs = e.getValue();
            long providerHandle = loader.registerHostProvider(iface, funcs.size());
            loader.registerHostCallback(providerHandle, new WitDispatcher(funcs));
            providers.put(iface, providerHandle);
        }
        return providers;
    }

    private static String interfacePrefix(String witPath) {
        int hash = witPath.indexOf('#');
        return hash < 0 ? "" : witPath.substring(0, hash);
    }

    /**
     * Routes a {@code wasmcm_host_call} for a given interface into the WIT host
     * function at the guest-supplied {@code funcIdx}. Argument / result codec
     * matches {@code wasmcm_runtime_guest::codec}.
     */
    private static final class WitDispatcher implements HostCallbackDispatcher {

        private final List<WitHostFunctionDefinition> funcs;

        WitDispatcher(List<WitHostFunctionDefinition> funcs) {
            this.funcs = Collections.unmodifiableList(new java.util.ArrayList<>(funcs));
        }

        @Override
        public byte[] dispatch(int funcIdx, byte[] argFrame) throws HostCallbackException {
            if (funcIdx < 0 || funcIdx >= funcs.size()) {
                throw new HostCallbackException(
                        "no WIT host function registered at index " + funcIdx
                                + " (interface has " + funcs.size() + " slots)");
            }
            WitHostFunctionDefinition def = funcs.get(funcIdx);
            List<Object> decoded = WasmcmValueCodec.decodeFrame(argFrame);
            Object[] argsArr = decoded.toArray(new Object[0]);
            Object[] results;
            try {
                results = def.function().execute(argsArr);
            } catch (RuntimeException e) {
                throw new HostCallbackException(
                        "WIT host function '" + def.witPath() + "' threw: " + e.getMessage());
            }
            if (results == null) {
                results = new Object[0];
            }
            return WasmcmValueCodec.encodeFrame(results);
        }
    }

    @Override
    public List<String> exportedInterfaces() {
        // The runtime guest surfaces exports only after instantiation (they are
        // properties of the instance, not the parsed component handle at this
        // milestone); return empty at the pre-instantiation Component level so
        // callers know to instantiate first.
        return Collections.emptyList();
    }

    @Override
    public List<String> importedInterfaces() {
        return Collections.emptyList();
    }

    private void checkOpen() {
        if (closed) {
            throw new IllegalStateException("EndiveComponentAdapter already closed");
        }
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        if (ownsLoader) {
            // The loader owns an Endive Instance running the runtime guest;
            // Endive Instances currently do not expose a close() (they release
            // when GC'd), so the ownership boundary is intentionally soft here.
            // Left as a no-op — the JVM will collect the companion instance
            // with the loader.
        }
    }
}
