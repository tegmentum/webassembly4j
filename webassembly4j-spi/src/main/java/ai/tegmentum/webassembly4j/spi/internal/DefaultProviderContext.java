package ai.tegmentum.webassembly4j.spi.internal;

import ai.tegmentum.webassembly4j.api.config.WebAssemblyConfig;
import ai.tegmentum.webassembly4j.spi.ProviderContext;

public final class DefaultProviderContext implements ProviderContext {

    private final int currentJavaVersion;
    private final WebAssemblyConfig config;
    private final String requestedEngineId;
    private final String requestedProviderId;

    public DefaultProviderContext(int currentJavaVersion, WebAssemblyConfig config,
                                 String requestedEngineId, String requestedProviderId) {
        this.currentJavaVersion = currentJavaVersion;
        this.config = config;
        this.requestedEngineId = requestedEngineId;
        this.requestedProviderId = requestedProviderId;
    }

    @Override
    public int currentJavaVersion() {
        return currentJavaVersion;
    }

    @Override
    public WebAssemblyConfig config() {
        return config;
    }

    @Override
    public String requestedEngineId() {
        return requestedEngineId;
    }

    @Override
    public String requestedProviderId() {
        return requestedProviderId;
    }
}
