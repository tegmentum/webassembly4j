package ai.tegmentum.webassembly4j.provider.endive;

import ai.tegmentum.webassembly4j.api.EngineCapabilities;

final class EndiveEngineCapabilities implements EngineCapabilities {

    @Override
    public boolean supportsCoreModules() {
        return true;
    }

    @Override
    public boolean supportsComponents() {
        // Component Model is layered in via wasmcm_runtime_guest.wasm; concretely
        // available whenever the guest blob is resolvable at loadComponent time.
        return WasmcmGuestBlobLocator.locateOrNull() != null;
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
