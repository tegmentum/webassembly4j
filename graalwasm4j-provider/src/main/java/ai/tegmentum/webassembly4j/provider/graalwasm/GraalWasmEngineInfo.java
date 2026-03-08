package ai.tegmentum.webassembly4j.provider.graalwasm;

import ai.tegmentum.webassembly4j.api.EngineInfo;

final class GraalWasmEngineInfo implements EngineInfo {

    @Override
    public String engineId() {
        return "graalwasm";
    }

    @Override
    public String providerId() {
        return "graalwasm";
    }

    @Override
    public String providerVersion() {
        return "1.0.0-SNAPSHOT";
    }

    @Override
    public String engineVersion() {
        return "24.1.1";
    }

    @Override
    public int minimumJavaVersion() {
        return 17;
    }
}
