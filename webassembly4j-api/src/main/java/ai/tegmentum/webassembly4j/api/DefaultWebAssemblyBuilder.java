package ai.tegmentum.webassembly4j.api;

import ai.tegmentum.webassembly4j.api.config.EngineConfig;
import ai.tegmentum.webassembly4j.api.config.WebAssemblyConfig;
import ai.tegmentum.webassembly4j.api.exception.ProviderUnavailableException;
import ai.tegmentum.webassembly4j.api.spi.WebAssemblyProviderBootstrap;

import java.util.ServiceLoader;

final class DefaultWebAssemblyBuilder implements WebAssemblyBuilder {

    private static volatile WebAssemblyProviderBootstrap cachedBootstrap;

    private String engineId;
    private String providerId;
    private WebAssemblyConfig config;
    private EngineConfig engineConfig;

    @Override
    public WebAssemblyBuilder engine(String engineId) {
        this.engineId = engineId;
        return this;
    }

    @Override
    public WebAssemblyBuilder provider(String providerId) {
        this.providerId = providerId;
        return this;
    }

    @Override
    public WebAssemblyBuilder config(WebAssemblyConfig config) {
        this.config = config;
        return this;
    }

    @Override
    public WebAssemblyBuilder engineConfig(EngineConfig config) {
        this.engineConfig = config;
        return this;
    }

    @Override
    public Engine build() {
        WebAssemblyProviderBootstrap bootstrap = cachedBootstrap;
        if (bootstrap == null) {
            bootstrap = findBootstrap();
        }
        return bootstrap.createEngine(config, engineConfig, engineId, providerId);
    }

    private static synchronized WebAssemblyProviderBootstrap findBootstrap() {
        if (cachedBootstrap != null) {
            return cachedBootstrap;
        }

        ServiceLoader<WebAssemblyProviderBootstrap> loader =
                ServiceLoader.load(WebAssemblyProviderBootstrap.class);

        for (WebAssemblyProviderBootstrap bootstrap : loader) {
            cachedBootstrap = bootstrap;
            return bootstrap;
        }

        throw new ProviderUnavailableException(
                "No WebAssembly engine provider found. "
                + "Add a provider artifact (e.g. wasmtime4j-provider-ffm) to the classpath "
                + "along with webassembly4j-spi.");
    }
}
