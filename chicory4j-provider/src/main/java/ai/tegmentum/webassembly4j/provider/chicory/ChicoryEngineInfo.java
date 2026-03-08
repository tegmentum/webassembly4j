package ai.tegmentum.webassembly4j.provider.chicory;

import ai.tegmentum.webassembly4j.api.EngineInfo;

final class ChicoryEngineInfo implements EngineInfo {

    @Override
    public String engineId() {
        return "chicory";
    }

    @Override
    public String providerId() {
        return "chicory";
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
