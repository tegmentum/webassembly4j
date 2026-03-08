package ai.tegmentum.webassembly4j.provider.wasmtime;

import ai.tegmentum.wasmtime4j.WasmFeature;
import ai.tegmentum.webassembly4j.api.EngineCapabilities;

final class WasmtimeEngineCapabilities implements EngineCapabilities {

    private final ai.tegmentum.wasmtime4j.Engine engine;

    WasmtimeEngineCapabilities(ai.tegmentum.wasmtime4j.Engine engine) {
        this.engine = engine;
    }

    @Override
    public boolean supportsCoreModules() {
        return true;
    }

    @Override
    public boolean supportsComponents() {
        return engine.supportsFeature(WasmFeature.COMPONENT_MODEL);
    }

    @Override
    public boolean supportsWasi() {
        return true;
    }

    @Override
    public boolean supportsFuel() {
        return engine.isFuelEnabled();
    }

    @Override
    public boolean supportsEpochInterruption() {
        return engine.isEpochInterruptionEnabled();
    }

    @Override
    public boolean supportsThreads() {
        return engine.supportsFeature(WasmFeature.THREADS);
    }

    @Override
    public boolean supportsGc() {
        return engine.supportsFeature(WasmFeature.GC);
    }

    @Override
    public boolean supportsReferenceTypes() {
        return engine.supportsFeature(WasmFeature.REFERENCE_TYPES);
    }

    @Override
    public boolean supportsMultiMemory() {
        return engine.supportsFeature(WasmFeature.MULTI_MEMORY);
    }

    @Override
    public boolean supportsNativeInterop() {
        return true;
    }
}
