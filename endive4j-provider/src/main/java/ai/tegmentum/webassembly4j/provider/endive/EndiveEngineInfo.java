package ai.tegmentum.webassembly4j.provider.endive;

import ai.tegmentum.webassembly4j.api.EngineInfo;

final class EndiveEngineInfo implements EngineInfo {

    @Override
    public String engineId() {
        return "endive";
    }

    @Override
    public String providerId() {
        return "endive";
    }

    @Override
    public String providerVersion() {
        return "1.0.0-SNAPSHOT";
    }

    @Override
    public String engineVersion() {
        return "1.0.0";
    }

    @Override
    public int minimumJavaVersion() {
        return 11;
    }
}
