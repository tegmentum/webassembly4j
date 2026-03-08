package ai.tegmentum.webassembly4j.spi.config;

import ai.tegmentum.webassembly4j.api.config.WebAssemblyConfig;

public interface ConfigTranslator<T> {

    T translate(WebAssemblyConfig config);
}
