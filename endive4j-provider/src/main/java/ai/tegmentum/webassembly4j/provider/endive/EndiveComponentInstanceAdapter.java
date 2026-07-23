package ai.tegmentum.webassembly4j.provider.endive;

import ai.tegmentum.webassembly4j.api.ComponentInstance;
import ai.tegmentum.webassembly4j.api.Function;
import ai.tegmentum.webassembly4j.api.Global;
import ai.tegmentum.webassembly4j.api.Memory;
import ai.tegmentum.webassembly4j.api.Table;
import ai.tegmentum.webassembly4j.api.exception.ExecutionException;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import run.endive.runtime.ExportFunction;
import run.tegmentum.wasmcm.endive.WasmcmRuntimeGuestLoader;

/**
 * SPI-facing {@link ComponentInstance} whose entire concrete implementation is
 * layered on top of {@code wasmcm_runtime_guest.wasm} running inside an Endive
 * companion instance. All Component Model semantics — export dispatch, canon
 * lift/lower, resource tables — live in the guest blob; the adapter only
 * marshals values across the wire and dispatches guest exports.
 *
 * <p>Instances are minted by {@link EndiveComponentAdapter#instantiate()}
 * / {@link EndiveComponentAdapter#instantiate(ai.tegmentum.webassembly4j.api.LinkingContext)}
 * and hold the u32 instance handle the guest returned. Multiple instances of
 * the same {@code Component} share the same {@link WasmcmRuntimeGuestLoader}.
 */
final class EndiveComponentInstanceAdapter implements ComponentInstance {

    private final WasmcmRuntimeGuestLoader loader;
    private final long instanceHandle;

    EndiveComponentInstanceAdapter(WasmcmRuntimeGuestLoader loader, long instanceHandle) {
        this.loader = loader;
        this.instanceHandle = instanceHandle;
    }

    @Override
    public Object invoke(String functionName, Object... args) {
        Object[] safeArgs = args == null ? new Object[0] : args;
        byte[] argFrame;
        try {
            argFrame = safeArgs.length == 0
                    ? new byte[0]
                    : WasmcmValueCodec.encodeFrame(safeArgs);
        } catch (RuntimeException e) {
            throw new ExecutionException(
                    "Failed to marshal arguments for component export '" + functionName + "'", e);
        }
        byte[] resultFrame;
        try {
            resultFrame = loader.callExport(instanceHandle, functionName, argFrame);
        } catch (IllegalStateException e) {
            throw new ExecutionException(
                    "Component export '" + functionName + "' failed: " + e.getMessage(), e);
        }
        List<Object> results;
        try {
            results = WasmcmValueCodec.decodeFrame(resultFrame);
        } catch (RuntimeException e) {
            throw new ExecutionException(
                    "Failed to decode results for component export '" + functionName + "'", e);
        }
        if (results.isEmpty()) {
            return null;
        }
        if (results.size() == 1) {
            return results.get(0);
        }
        return results;
    }

    @Override
    public boolean hasFunction(String name) {
        for (String exp : exportedFunctions()) {
            if (exp.equals(name)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public List<String> exportedFunctions() {
        // The runtime guest exposes the flattened export list of the instantiated
        // component through wasmcm_instance_export_count / wasmcm_instance_export_name.
        // These are string names as the component itself surfaces them (function names
        // for root-level exports; interface-qualified names for interface exports).
        ExportFunction countFn = loader.guest().exports().function("wasmcm_instance_export_count");
        ExportFunction nameFn = loader.guest().exports().function("wasmcm_instance_export_name");
        if (countFn == null || nameFn == null) {
            return Collections.emptyList();
        }
        run.endive.runtime.Memory mem = loader.guest().exports().memory("memory");
        ExportFunction alloc = loader.guest().exports().function("wasmcm_alloc");
        ExportFunction free = loader.guest().exports().function("wasmcm_free");
        long[] countResult = countFn.apply(instanceHandle);
        int count = (int) (countResult[0] & 0xFFFFFFFFL);
        if (count == 0) {
            return Collections.emptyList();
        }
        int bufCap = 512;
        long[] allocResult = alloc.apply((long) bufCap, 1L);
        int bufPtr = (int) (allocResult[0] & 0xFFFFFFFFL);
        if (bufPtr == 0) {
            return Collections.emptyList();
        }
        try {
            java.util.List<String> out = new java.util.ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                long[] r = nameFn.apply(instanceHandle, (long) i, (long) bufPtr, (long) bufCap);
                int n = (int) (r[0] & 0xFFFFFFFFL);
                if (n == 0) {
                    continue;
                }
                byte[] bytes = mem.readBytes(bufPtr, n);
                out.add(new String(bytes, StandardCharsets.UTF_8));
            }
            return Collections.unmodifiableList(out);
        } finally {
            free.apply((long) bufPtr, (long) bufCap);
        }
    }

    @Override
    public List<String> exportedInterfaces() {
        // The runtime guest's flattened export list mingles interface-qualified
        // function exports (e.g. "wasi:clocks/monotonic-clock@0.2.3#now") with
        // root-level ones. Extract the interface prefixes to give callers a
        // provider-agnostic view of "which interfaces does this instance surface".
        java.util.LinkedHashSet<String> ifaces = new java.util.LinkedHashSet<>();
        for (String name : exportedFunctions()) {
            int hash = name.indexOf('#');
            if (hash > 0) {
                ifaces.add(name.substring(0, hash));
            }
        }
        return java.util.Collections.unmodifiableList(new java.util.ArrayList<>(ifaces));
    }

    @Override
    public boolean exportsInterface(String name) {
        return exportedInterfaces().contains(name);
    }

    @Override
    public Optional<Function> function(String name) {
        // Component instances do not surface core-module exports; the SPI's
        // ComponentInstance javadoc documents this contract explicitly.
        return Optional.empty();
    }

    @Override
    public Optional<Memory> memory(String name) {
        return Optional.empty();
    }

    @Override
    public Optional<Table> table(String name) {
        return Optional.empty();
    }

    @Override
    public Optional<Global> global(String name) {
        return Optional.empty();
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> Optional<T> unwrap(Class<T> nativeType) {
        if (nativeType.isInstance(loader)) {
            return Optional.of((T) loader);
        }
        return Optional.empty();
    }
}
