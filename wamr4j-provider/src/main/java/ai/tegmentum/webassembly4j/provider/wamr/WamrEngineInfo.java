package ai.tegmentum.webassembly4j.provider.wamr;

import ai.tegmentum.webassembly4j.api.EngineInfo;

final class WamrEngineInfo implements EngineInfo {

    private final String engineVersion;

    WamrEngineInfo(String engineVersion) {
        this.engineVersion = engineVersion;
    }

    @Override
    public String engineId() {
        return "wamr";
    }

    @Override
    public String providerId() {
        return "wamr";
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
