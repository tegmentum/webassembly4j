package ai.tegmentum.webassembly4j.provider.graalwasm;

import ai.tegmentum.webassembly4j.api.EngineCapabilities;

final class GraalWasmEngineCapabilities implements EngineCapabilities {

    @Override
    public boolean supportsCoreModules() {
        return true;
    }

    @Override
    public boolean supportsComponents() {
        return false;
    }

    @Override
    public boolean supportsWasi() {
        return false;
    }

    @Override
    public boolean supportsFuel() {
        return false;
    }

    @Override
    public boolean supportsEpochInterruption() {
        return false;
    }

    @Override
    public boolean supportsThreads() {
        return false;
    }

    @Override
    public boolean supportsGc() {
        return false;
    }

    @Override
    public boolean supportsReferenceTypes() {
        return false;
    }

    @Override
    public boolean supportsMultiMemory() {
        return false;
    }

    @Override
    public boolean supportsNativeInterop() {
        return false;
    }
}
