package ai.tegmentum.webassembly4j.provider.wasm3;

import ai.tegmentum.webassembly4j.api.EngineInfo;

/** {@link EngineInfo} for the wasm3 provider. */
final class Wasm3EngineInfo implements EngineInfo {

    private final String engineVersion;

    Wasm3EngineInfo(final String engineVersion) {
        this.engineVersion = engineVersion;
    }

    @Override
    public String engineId() {
        return "wasm3";
    }

    @Override
    public String providerId() {
        return "wasm3";
    }

    @Override
    public String providerVersion() {
        return "1.0.0-SNAPSHOT";
    }

    @Override
    public String engineVersion() {
        return engineVersion;
    }

    @Override
    public int minimumJavaVersion() {
        return 17;
    }
}
