package ai.tegmentum.webassembly4j.provider.wasmtime;

import ai.tegmentum.webassembly4j.api.EngineInfo;

final class WasmtimeEngineInfo implements EngineInfo {

    @Override
    public String engineId() {
        return "wasmtime";
    }

    @Override
    public String providerId() {
        return "wasmtime";
    }

    @Override
    public String providerVersion() {
        return "1.0.0-SNAPSHOT";
    }

    @Override
    public String engineVersion() {
        return "42.0.1";
    }

    @Override
    public int minimumJavaVersion() {
        return 11;
    }
}
