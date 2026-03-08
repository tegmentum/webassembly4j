package ai.tegmentum.webassembly4j.spi;

import ai.tegmentum.webassembly4j.api.config.WebAssemblyConfig;

public interface ProviderContext {

    int currentJavaVersion();

    WebAssemblyConfig config();

    String requestedEngineId();

    String requestedProviderId();
}
