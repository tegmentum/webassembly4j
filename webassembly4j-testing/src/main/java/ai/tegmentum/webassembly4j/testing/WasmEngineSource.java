package ai.tegmentum.webassembly4j.testing;

import ai.tegmentum.webassembly4j.api.Engine;
import ai.tegmentum.webassembly4j.api.WebAssembly;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.ServiceLoader;

/**
 * Discovers available WebAssembly engines on the classpath via ServiceLoader.
 */
final class WasmEngineSource {

    private static volatile List<EngineFactory> cachedFactories;

    private WasmEngineSource() {}

    static List<EngineFactory> discoverEngines() {
        List<EngineFactory> factories = cachedFactories;
        if (factories != null) {
            return factories;
        }
        factories = new ArrayList<>();
        ServiceLoader<ai.tegmentum.webassembly4j.api.spi.WebAssemblyProviderBootstrap> loader =
                ServiceLoader.load(ai.tegmentum.webassembly4j.api.spi.WebAssemblyProviderBootstrap.class);
        for (ai.tegmentum.webassembly4j.api.spi.WebAssemblyProviderBootstrap bootstrap : loader) {
            try {
                Engine probe = bootstrap.createEngine(null, null, null, null);
                String engineId = probe.info().engineId();
                String providerId = probe.info().providerId();
                probe.close();
                factories.add(new EngineFactory(engineId, providerId));
            } catch (Exception ignored) {
                // Skip engines that fail to initialize
            }
        }
        if (factories.isEmpty()) {
            // Fallback: try the default engine
            try {
                Engine probe = WebAssembly.builder().build();
                String engineId = probe.info().engineId();
                String providerId = probe.info().providerId();
                probe.close();
                factories.add(new EngineFactory(engineId, providerId));
            } catch (Exception ignored) {
                // No engines available
            }
        }
        cachedFactories = Collections.unmodifiableList(factories);
        return cachedFactories;
    }

    static final class EngineFactory {
        private final String engineId;
        private final String providerId;

        EngineFactory(String engineId, String providerId) {
            this.engineId = engineId;
            this.providerId = providerId;
        }

        Engine create() {
            return WebAssembly.builder()
                    .engine(engineId)
                    .provider(providerId)
                    .build();
        }

        String displayName() {
            return engineId + " (" + providerId + ")";
        }

        String engineId() {
            return engineId;
        }
    }
}
