package ai.tegmentum.webassembly4j.provider.chicory;

import ai.tegmentum.webassembly4j.api.config.WebAssemblyConfig;
import ai.tegmentum.webassembly4j.api.config.WebAssemblyConfigBuilder;
import ai.tegmentum.webassembly4j.provider.chicory.config.ChicoryConfig;

import java.util.function.Consumer;

/**
 * Convenience entry point for building a {@link WebAssemblyConfig} pre-wired for the
 * Chicory pure-Java engine.
 *
 * <h2>Examples</h2>
 * <pre>{@code
 * // Just Chicory tunings:
 * WebAssemblyConfig cfg = ChicoryConfigs.of(b -> b
 *         .executionMode(ChicoryConfig.ExecutionMode.COMPILE));
 *
 * // Default interpreter + cross-engine settings:
 * WebAssemblyConfig cfg = ChicoryConfigs.builder(b -> {})
 *         .debug(true)
 *         .build();
 * }</pre>
 */
public final class ChicoryConfigs {

    public static final String ENGINE_ID = "chicory";

    private ChicoryConfigs() {}

    public static WebAssemblyConfig of(Consumer<ChicoryConfig.Builder> configurator) {
        return builder(configurator).build();
    }

    public static WebAssemblyConfigBuilder builder(Consumer<ChicoryConfig.Builder> configurator) {
        ChicoryConfig.Builder cb = ChicoryConfig.builder();
        configurator.accept(cb);
        return WebAssemblyConfig.builder()
                .engine(ENGINE_ID)
                .engineConfig(cb.build());
    }
}
