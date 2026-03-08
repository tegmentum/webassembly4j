package ai.tegmentum.webassembly4j.api.spi;

import ai.tegmentum.webassembly4j.api.Engine;
import ai.tegmentum.webassembly4j.api.config.EngineConfig;
import ai.tegmentum.webassembly4j.api.config.WebAssemblyConfig;

public interface WebAssemblyProviderBootstrap {

    Engine createEngine(WebAssemblyConfig config, EngineConfig engineConfig,
                        String requestedEngineId, String requestedProviderId);
}
