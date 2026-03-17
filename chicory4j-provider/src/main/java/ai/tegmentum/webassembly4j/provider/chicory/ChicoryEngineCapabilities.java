package ai.tegmentum.webassembly4j.provider.chicory;

import ai.tegmentum.webassembly4j.api.EngineCapabilities;

final class ChicoryEngineCapabilities implements EngineCapabilities {

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
        return true;
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
        return true;
    }

    @Override
    public boolean supportsGc() {
        return true;
    }

    @Override
    public boolean supportsReferenceTypes() {
        return true;
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
