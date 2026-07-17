package ai.tegmentum.webassembly4j.provider.wasmtime;

import ai.tegmentum.wasmtime4j.WasmRuntime;
import ai.tegmentum.wasmtime4j.component.ComponentEngine;
import ai.tegmentum.wasmtime4j.component.ComponentHostFunction;
import ai.tegmentum.wasmtime4j.component.ComponentLinker;
import ai.tegmentum.wasmtime4j.component.ComponentVal;
import ai.tegmentum.wasmtime4j.wasi.DirPerms;
import ai.tegmentum.wasmtime4j.wasi.FilePerms;
import ai.tegmentum.wasmtime4j.wasi.WasiPreview2Config;
import ai.tegmentum.webassembly4j.api.ComponentInstance;
import ai.tegmentum.webassembly4j.api.LinkingContext;
import ai.tegmentum.webassembly4j.api.WasiContext;
import ai.tegmentum.webassembly4j.api.WasiNnConfig;
import ai.tegmentum.webassembly4j.api.WitHostFunctionDefinition;
import ai.tegmentum.webassembly4j.api.config.ComponentConfig;
import ai.tegmentum.webassembly4j.api.exception.InstantiationException;
import ai.tegmentum.webassembly4j.api.exception.UnsupportedFeatureException;

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

final class WasmtimeComponentAdapter implements ai.tegmentum.webassembly4j.api.Component {

    private final WasmRuntime runtime;
    private final ai.tegmentum.wasmtime4j.Engine engine;
    private final ComponentEngine componentEngine;
    private final ai.tegmentum.wasmtime4j.component.Component nativeComponent;
    // Stores are created per instantiation (so ComponentConfig limits can be applied) and
    // closed together with the component.
    private final List<ai.tegmentum.wasmtime4j.Store> stores = new ArrayList<>();

    WasmtimeComponentAdapter(WasmRuntime runtime,
                             ai.tegmentum.wasmtime4j.Engine engine,
                             ComponentEngine componentEngine,
                             ai.tegmentum.wasmtime4j.component.Component nativeComponent) {
        this.runtime = runtime;
        this.engine = engine;
        this.componentEngine = componentEngine;
        this.nativeComponent = nativeComponent;
    }

    @Override
    public ComponentInstance instantiate() {
        return doInstantiate(null, null);
    }

    @Override
    public ComponentInstance instantiate(LinkingContext linkingContext) {
        return doInstantiate(linkingContext, null);
    }

    @Override
    public ComponentInstance instantiate(ComponentConfig config) {
        return doInstantiate(null, config);
    }

    @Override
    public ComponentInstance instantiate(LinkingContext linkingContext, ComponentConfig config) {
        return doInstantiate(linkingContext, config);
    }

    private ComponentInstance doInstantiate(LinkingContext linkingContext, ComponentConfig config) {
        if (linkingContext != null && !linkingContext.hostFunctions().isEmpty()) {
            throw new UnsupportedFeatureException(
                    "Core host functions are not supported for component instantiation. "
                    + "Use unwrap() to access the native ComponentLinker for WIT-level imports.");
        }
        try {
            ai.tegmentum.wasmtime4j.Store store = newStore(config);
            ComponentLinker<Object> linker = ComponentLinker.create(engine);

            // Memory cap + epoch deadline can't be read back from the store (unlike fuel), so carry
            // them to the instantiation explicitly. The component runs on wasmtime4j's shared
            // component engine (metered for fuel + epoch), so these apply regardless of this
            // engine's own config. -1 = unlimited.
            if (config != null) {
                long maxMemory = config.maxMemoryBytes().isPresent()
                        ? config.maxMemoryBytes().getAsLong() : -1L;
                long epoch = config.epochDeadline().isPresent()
                        ? config.epochDeadline().getAsLong() : -1L;
                linker.setComponentResourceLimits(maxMemory, epoch);
            }

            WasiContext wasi = linkingContext != null ? linkingContext.wasiContext() : null;
            // Always route through the (config) overload — the no-arg variant leaves the
            // linker's stored wasiConfig null, and JniComponentLinker.instantiate falls back to
            // the stub linker path (Fix 12 tracking) which returns a phantom instance ID and the
            // subsequent invoke fails with "Instance ID N not found in engine". A default config
            // (no preopens, no env, deny-network) yields a WASI context that resolves the guest's
            // imports without granting anything.
            WasiPreview2Config p2 = (wasi != null)
                    ? toWasiPreview2Config(wasi)
                    : WasiPreview2Config.builder().build();
            linker.enableWasiPreview2(p2);

            // WASI-NN opt-in: translate the framework-neutral WasiNnConfig into
            // wasmtime4j's native WasiNnConfig and register wasi:nn on the linker
            // before instantiation. Providers without wasi:nn surface via the
            // native linker throwing WasmException; we let that propagate as
            // InstantiationException below.
            WasiNnConfig nn = linkingContext != null ? linkingContext.wasiNnConfig() : null;
            if (nn != null) {
                linker.enableWasiNn(toNativeWasiNnConfig(nn));
            }

            // Register any WIT-typed host imports declared on the linking context. Each is
            // wrapped in a native ComponentHostFunction that just delegates — the caller
            // (whose api-level function takes/returns Object[]) already speaks the provider's
            // native value type (ComponentVal here); a cross-provider WIT value abstraction
            // is intentionally out of scope for this version.
            if (linkingContext != null) {
                for (WitHostFunctionDefinition def : linkingContext.witHostFunctions()) {
                    final WitHostFunctionDefinition d = def;
                    ComponentHostFunction impl = args -> {
                        Object[] argsArr = new Object[args.size()];
                        for (int i = 0; i < args.size(); i++) argsArr[i] = args.get(i);
                        Object[] results = d.function().execute(argsArr);
                        java.util.List<ComponentVal> out = new java.util.ArrayList<>(results.length);
                        for (Object r : results) out.add((ComponentVal) r);
                        return out;
                    };
                    linker.defineFunction(def.witPath(), impl);
                }
            }

            ai.tegmentum.wasmtime4j.component.ComponentInstance nativeInstance =
                    linker.instantiate(store, nativeComponent);
            return new WasmtimeComponentInstanceAdapter(nativeInstance);
        } catch (ai.tegmentum.wasmtime4j.exception.WasmException e) {
            throw new InstantiationException(
                    "Failed to instantiate WebAssembly component", e);
        }
    }

    /** Create a store, applying ComponentConfig resource limits (memory/table/instances + fuel/epoch). */
    private ai.tegmentum.wasmtime4j.Store newStore(ComponentConfig config)
            throws ai.tegmentum.wasmtime4j.exception.WasmException {
        ai.tegmentum.wasmtime4j.Store store;
        if (config != null && hasStoreLimits(config)) {
            ai.tegmentum.wasmtime4j.config.StoreLimits.Builder lim =
                    ai.tegmentum.wasmtime4j.config.StoreLimits.builder();
            config.maxMemoryBytes().ifPresent(lim::memorySize);
            config.maxTableElements().ifPresent(lim::tableElements);
            config.maxInstances().ifPresent(lim::instances);
            config.maxTables().ifPresent(lim::tables);
            config.maxMemories().ifPresent(lim::memories);
            lim.trapOnGrowFailure(config.trapOnGrowFailure());
            store = runtime.createStore(engine, lim.build());
        } else {
            store = engine.createStore();
        }
        // Fuel: when the engine meters fuel, the store's remaining fuel is what the component
        // provider carries down as the per-instance compute cap. Set the requested cap, or
        // MAX_VALUE ("unlimited") when none is given — otherwise a metered store defaults to 0 fuel
        // and the component would trap on its first instruction.
        if (engine.isFuelEnabled()) {
            long fuel = (config != null && config.fuelLimit().isPresent())
                    ? config.fuelLimit().getAsLong()
                    : Long.MAX_VALUE;
            store.setFuel(fuel);
        }
        if (config != null && config.epochDeadline().isPresent()
                && engine.isEpochInterruptionEnabled()) {
            store.setEpochDeadline(config.epochDeadline().getAsLong());
        }
        stores.add(store);
        return store;
    }

    private static boolean hasStoreLimits(ComponentConfig c) {
        return c.maxMemoryBytes().isPresent() || c.maxTableElements().isPresent()
                || c.maxInstances().isPresent() || c.maxTables().isPresent()
                || c.maxMemories().isPresent();
    }

    /**
     * Translate the provider-agnostic WasiContext into wasmtime4j's WasiPreview2Config.
     * Package-private for unit testing the mapping.
     */
    static WasiPreview2Config toWasiPreview2Config(WasiContext wasi) {
        WasiPreview2Config.Builder b = WasiPreview2Config.builder();
        if (wasi.args() != null && !wasi.args().isEmpty()) {
            b.args(wasi.args().toArray(new String[0]));
        }
        if (wasi.env() != null && !wasi.env().isEmpty()) {
            b.env(wasi.env());
        }
        if (wasi.inheritStdin()) {
            b.inheritStdin();
        }
        if (wasi.inheritStdout()) {
            b.inheritStdout();
        }
        if (wasi.inheritStderr()) {
            b.inheritStderr();
        }
        // Preopens: host path == guest path. A dir listed in readOnlyPreopenDirs is granted read
        // permissions only (the guest cannot create/write/delete within it); all others are
        // read-write. (Guest-path remap remains a follow-on.)
        if (wasi.preopenDirs() != null) {
            java.util.Set<String> readOnly = wasi.readOnlyPreopenDirs() == null
                    ? java.util.Collections.emptySet()
                    : new java.util.HashSet<>(wasi.readOnlyPreopenDirs());
            java.util.Map<String, String> guestPaths = wasi.preopenGuestPaths() == null
                    ? java.util.Collections.emptyMap()
                    : wasi.preopenGuestPaths();
            for (String dir : wasi.preopenDirs()) {
                String guest = guestPaths.getOrDefault(dir, dir); // default: guest path == host path
                if (readOnly.contains(dir)) {
                    b.preopenDir(Paths.get(dir), guest, DirPerms.readOnly(), FilePerms.readOnly());
                } else {
                    b.preopenDir(Paths.get(dir), guest, DirPerms.all(), FilePerms.all());
                }
            }
        }
        // Network egress: deny-by-default unless the policy grants it. When granted, allow TCP/UDP but
        // gate every connection through a SocketAddrCheck that permits ONLY egress (connect / outgoing
        // datagram) to an allow-listed endpoint and denies every bind/listen use — no ingress, ever.
        if (wasi.allowNetwork() && wasi.egressRules() != null && !wasi.egressRules().isEmpty()) {
            final java.util.List<ai.tegmentum.webassembly4j.api.NetworkEgressRule> rules =
                    new ArrayList<>(wasi.egressRules());
            b.allowNetwork(true).allowTcp(true).allowUdp(true);
            b.socketAddrCheck((address, use) -> {
                final boolean tcp;
                switch (use) {
                    case TCP_CONNECT:
                        tcp = true;
                        break;
                    case UDP_CONNECT:
                    case UDP_OUTGOING_DATAGRAM:
                        tcp = false;
                        break;
                    default:
                        return false; // deny all *_BIND — egress only
                }
                for (ai.tegmentum.webassembly4j.api.NetworkEgressRule r : rules) {
                    if (r.matches(address, tcp)) {
                        return true;
                    }
                }
                return false;
            });
        }
        // Filesystem-denial observability: if the policy supplied an engine-neutral FsAccessObserver,
        // bridge it to wasmtime4j's own observer so a denied component open-at/stat-at on the preopen
        // path surfaces the raw guest path + classified reason. Observe-only — it cannot change the
        // enforcement outcome (wasmtime has already refused the open by the time this fires).
        if (wasi.fsAccessObserver() != null && wasi.fsAccessObserver().isPresent()) {
            final ai.tegmentum.webassembly4j.api.FsAccessObserver neutral = wasi.fsAccessObserver().get();
            b.fsAccessObserver(
                    (path, operation, reason, errorCode) ->
                            neutral.onDenied(path, operation, reason, errorCode));
        }
        return b.build();
    }

    /**
     * Translate the provider-neutral WasiNnConfig into wasmtime4j's native
     * WasiNnConfig. Named-model entries are copied via the native builder
     * (which itself clones the byte array). Package-private for unit test.
     */
    static ai.tegmentum.wasmtime4j.wasi.nn.WasiNnConfig toNativeWasiNnConfig(WasiNnConfig config) {
        if (config == null || config.namedModels().isEmpty()) {
            return ai.tegmentum.wasmtime4j.wasi.nn.WasiNnConfig.defaults();
        }
        ai.tegmentum.wasmtime4j.wasi.nn.WasiNnConfig.Builder b =
                ai.tegmentum.wasmtime4j.wasi.nn.WasiNnConfig.builder();
        for (java.util.Map.Entry<String, byte[]> e : config.namedModels().entrySet()) {
            b.registerModel(e.getKey(), e.getValue());
        }
        return b.build();
    }

    @Override
    public List<String> exportedInterfaces() {
        try {
            Set<String> interfaces = nativeComponent.getExportedInterfaces();
            return Collections.unmodifiableList(new ArrayList<>(interfaces));
        } catch (ai.tegmentum.wasmtime4j.exception.WasmException e) {
            return Collections.emptyList();
        }
    }

    @Override
    public List<String> importedInterfaces() {
        try {
            Set<String> interfaces = nativeComponent.getImportedInterfaces();
            return Collections.unmodifiableList(new ArrayList<>(interfaces));
        } catch (ai.tegmentum.wasmtime4j.exception.WasmException e) {
            return Collections.emptyList();
        }
    }

    @Override
    public boolean exportsInterface(String name) {
        try {
            return nativeComponent.exportsInterface(name);
        } catch (ai.tegmentum.wasmtime4j.exception.WasmException e) {
            return false;
        }
    }

    @Override
    public boolean importsInterface(String name) {
        try {
            return nativeComponent.importsInterface(name);
        } catch (ai.tegmentum.wasmtime4j.exception.WasmException e) {
            return false;
        }
    }

    @Override
    public byte[] serialize() {
        try {
            return nativeComponent.serialize();
        } catch (ai.tegmentum.wasmtime4j.exception.WasmException e) {
            throw new ai.tegmentum.webassembly4j.api.exception.WebAssemblyException(
                    "Failed to serialize component", e);
        }
    }

    /**
     * Reports whether the loaded wasmtime4j native library was compiled with the
     * {@code wasi-nn} cargo feature. Delegates via reflection to wasmtime4j's
     * static probe (which itself queries a compile-time constant baked into the
     * native), so the answer is cheap and stable for the process lifetime — no
     * exception-catching semantics required. When {@code false}, callers must
     * not pass a {@link WasiNnConfig} through
     * {@link ai.tegmentum.webassembly4j.api.DefaultLinkingContext.Builder#enableWasiNn}
     * — the native enable-nn path would throw "WASI-NN support not compiled in"
     * during {@link #instantiate(ai.tegmentum.webassembly4j.api.LinkingContext)}.
     *
     * <p>Reflection is used to keep {@code wasmtime4j-jni} at runtime scope in
     * this provider's pom, matching pre-2.4.1 layering (Panama backend users
     * can substitute the JNI implementation without a compile-time coupling).
     */
    @Override
    public boolean supportsWasiNn() {
        return WasiNnProbeHolder.AVAILABLE;
    }

    /** Reflection-lookup holder — computed once, then a constant boolean read. */
    private static final class WasiNnProbeHolder {
        private static final boolean AVAILABLE = probeNative();

        private static boolean probeNative() {
            try {
                Class<?> cls = Class.forName("ai.tegmentum.wasmtime4j.jni.JniComponentLinker");
                Object result = cls.getMethod("wasiNnAvailable").invoke(null);
                return Boolean.TRUE.equals(result);
            } catch (ReflectiveOperationException | LinkageError e) {
                // JNI class missing (Panama-only classpath) or older wasmtime4j
                // without the probe method — treat as no wasi:nn support so
                // callers stay on the safe path.
                return false;
            }
        }
    }

    @Override
    public void close() {
        for (ai.tegmentum.wasmtime4j.Store s : stores) {
            try {
                s.close();
            } catch (RuntimeException ignored) {
                // best effort
            }
        }
        nativeComponent.close();
        try {
            componentEngine.close();
        } catch (java.io.IOException e) {
            throw new ai.tegmentum.webassembly4j.api.exception.WebAssemblyException(
                    "Failed to close component engine", e);
        }
    }
}
