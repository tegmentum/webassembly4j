package ai.tegmentum.webassembly4j.provider.wasmtime;

import ai.tegmentum.wasmtime4j.Linker;
import ai.tegmentum.wasmtime4j.WasmRuntime;
import ai.tegmentum.webassembly4j.api.Instance;
import ai.tegmentum.webassembly4j.api.LinkingContext;
import ai.tegmentum.webassembly4j.api.Module;

final class WasmtimeModuleAdapter implements Module {

    private final WasmRuntime runtime;
    private final ai.tegmentum.wasmtime4j.Engine engine;
    private final ai.tegmentum.wasmtime4j.Module nativeModule;
    private final ai.tegmentum.wasmtime4j.Store store;
    private final ai.tegmentum.wasmtime4j.config.EngineConfig engineConfig;

    WasmtimeModuleAdapter(WasmRuntime runtime,
                          ai.tegmentum.wasmtime4j.Engine engine,
                          ai.tegmentum.wasmtime4j.Module nativeModule,
                          ai.tegmentum.wasmtime4j.Store store,
                          ai.tegmentum.wasmtime4j.config.EngineConfig engineConfig) {
        this.runtime = runtime;
        this.engine = engine;
        this.nativeModule = nativeModule;
        this.store = store;
        this.engineConfig = engineConfig;
    }

    @Override
    public Instance instantiate() {
        try {
            Linker<Object> linker = runtime.createLinker(engine);
            ai.tegmentum.wasmtime4j.Instance nativeInstance =
                    linker.instantiate(store, nativeModule);
            return new WasmtimeInstanceAdapter(nativeInstance);
        } catch (ai.tegmentum.wasmtime4j.exception.WasmException e) {
            throw new ai.tegmentum.webassembly4j.api.exception.InstantiationException(
                    "Failed to instantiate WebAssembly module", e);
        }
    }

    @Override
    public Instance instantiate(LinkingContext linkingContext) {
        // For now, delegate to simple instantiation.
        // Future: use LinkingContext to configure imports, WASI, etc.
        return instantiate();
    }

    @Override
    public void close() {
        nativeModule.close();
        store.close();
    }
}
