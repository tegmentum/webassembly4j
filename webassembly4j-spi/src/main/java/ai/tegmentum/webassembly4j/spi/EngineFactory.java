package ai.tegmentum.webassembly4j.spi;

import ai.tegmentum.webassembly4j.api.Engine;
import ai.tegmentum.webassembly4j.api.config.WebAssemblyConfig;

public interface EngineFactory {

    Engine create(WebAssemblyConfig config);
}
