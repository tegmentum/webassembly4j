package ai.tegmentum.webassembly4j.provider.wasmtime;

import ai.tegmentum.wasmtime4j.WasmRuntime;
import ai.tegmentum.webassembly4j.api.Function;
import ai.tegmentum.webassembly4j.api.Global;
import ai.tegmentum.webassembly4j.api.Instance;
import ai.tegmentum.webassembly4j.api.Memory;
import ai.tegmentum.webassembly4j.api.Table;
import ai.tegmentum.webassembly4j.api.gc.GcExtension;

import java.util.Optional;

final class WasmtimeInstanceAdapter implements Instance {

    private final ai.tegmentum.wasmtime4j.Instance nativeInstance;
    private final WasmRuntime runtime;
    private final ai.tegmentum.wasmtime4j.Engine engine;

    WasmtimeInstanceAdapter(ai.tegmentum.wasmtime4j.Instance nativeInstance,
                            WasmRuntime runtime,
                            ai.tegmentum.wasmtime4j.Engine engine) {
        this.nativeInstance = nativeInstance;
        this.runtime = runtime;
        this.engine = engine;
    }

    @Override
    public Optional<Function> function(String name) {
        return nativeInstance.getFunction(name)
                .map(WasmtimeFunctionAdapter::new);
    }

    @Override
    public Optional<Memory> memory(String name) {
        return nativeInstance.getMemory(name)
                .map(WasmtimeMemoryAdapter::new);
    }

    @Override
    public Optional<Table> table(String name) {
        return nativeInstance.getTable(name)
                .map(WasmtimeTableAdapter::new);
    }

    @Override
    public Optional<Global> global(String name) {
        return nativeInstance.getGlobal(name)
                .map(WasmtimeGlobalAdapter::new);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> Optional<T> extension(Class<T> extensionType) {
        if (extensionType == GcExtension.class && engine.supportsFeature(
                ai.tegmentum.wasmtime4j.WasmFeature.GC)) {
            return Optional.of((T) new WasmtimeGcExtension(runtime));
        }
        return Optional.empty();
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> Optional<T> unwrap(Class<T> nativeType) {
        if (nativeType.isInstance(nativeInstance)) {
            return Optional.of((T) nativeInstance);
        }
        return Optional.empty();
    }
}
