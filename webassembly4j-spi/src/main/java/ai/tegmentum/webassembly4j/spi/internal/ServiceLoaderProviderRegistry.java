package ai.tegmentum.webassembly4j.spi.internal;

import ai.tegmentum.webassembly4j.api.Engine;
import ai.tegmentum.webassembly4j.api.config.EngineConfig;
import ai.tegmentum.webassembly4j.api.config.WebAssemblyConfig;
import ai.tegmentum.webassembly4j.api.exception.ConfigurationException;
import ai.tegmentum.webassembly4j.api.exception.ProviderUnavailableException;
import ai.tegmentum.webassembly4j.api.spi.WebAssemblyProviderBootstrap;
import ai.tegmentum.webassembly4j.spi.EngineProvider;
import ai.tegmentum.webassembly4j.spi.ProviderBootstrap;
import ai.tegmentum.webassembly4j.spi.ProviderContext;
import ai.tegmentum.webassembly4j.spi.ProviderSelectionResult;
import ai.tegmentum.webassembly4j.spi.ProviderSelector;
import ai.tegmentum.webassembly4j.spi.ValidationResult;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.ServiceLoader;

public final class ServiceLoaderProviderRegistry
        implements ProviderBootstrap, WebAssemblyProviderBootstrap {

    private static volatile List<EngineProvider> cachedProviders;

    private final ProviderSelector selector;

    public ServiceLoaderProviderRegistry() {
        this(new DefaultProviderSelector());
    }

    public ServiceLoaderProviderRegistry(ProviderSelector selector) {
        this.selector = selector;
    }

    @Override
    public Engine createEngine(WebAssemblyConfig config, EngineConfig engineConfig,
                               String requestedEngineId, String requestedProviderId) {
        WebAssemblyConfig effectiveConfig = config;
        if (engineConfig != null) {
            if (effectiveConfig == null) {
                effectiveConfig = WebAssemblyConfig.builder()
                        .engineConfig(engineConfig)
                        .build();
            } else if (!effectiveConfig.engineConfig().isPresent()) {
                effectiveConfig = WebAssemblyConfig.builder()
                        .engineConfig(engineConfig)
                        .build();
            }
        }
        return createEngine(effectiveConfig, requestedEngineId, requestedProviderId);
    }

    @Override
    public Engine createEngine(WebAssemblyConfig config, String requestedEngineId,
                               String requestedProviderId) {
        List<EngineProvider> providers = getProviders();

        ProviderContext context = new DefaultProviderContext(
                RuntimeVersion.currentJavaVersion(),
                config,
                requestedEngineId,
                requestedProviderId);

        ProviderSelectionResult result = selector.select(providers, context);

        EngineProvider provider = result.selectedProvider()
                .orElseThrow(() -> new ProviderUnavailableException(result.explanation()));

        if (config != null) {
            ValidationResult validation = provider.validate(config);
            if (!validation.valid()) {
                throw new ConfigurationException(
                        "Configuration validation failed: " + String.join("; ", validation.errors()));
            }
        }

        return provider.create(config);
    }

    private static List<EngineProvider> getProviders() {
        List<EngineProvider> providers = cachedProviders;
        if (providers == null) {
            providers = discoverProviders();
        }
        return providers;
    }

    private static synchronized List<EngineProvider> discoverProviders() {
        if (cachedProviders != null) {
            return cachedProviders;
        }
        List<EngineProvider> providers = new ArrayList<>();
        ServiceLoader<EngineProvider> loader = ServiceLoader.load(EngineProvider.class);
        for (EngineProvider provider : loader) {
            providers.add(provider);
        }
        cachedProviders = Collections.unmodifiableList(providers);
        return cachedProviders;
    }
}
