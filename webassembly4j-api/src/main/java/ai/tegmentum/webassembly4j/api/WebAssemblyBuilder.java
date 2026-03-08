package ai.tegmentum.webassembly4j.api;

import ai.tegmentum.webassembly4j.api.config.EngineConfig;
import ai.tegmentum.webassembly4j.api.config.WebAssemblyConfig;

public interface WebAssemblyBuilder {

    WebAssemblyBuilder engine(String engineId);

    WebAssemblyBuilder provider(String providerId);

    WebAssemblyBuilder config(WebAssemblyConfig config);

    WebAssemblyBuilder engineConfig(EngineConfig config);

    Engine build();
}
