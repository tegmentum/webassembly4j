package ai.tegmentum.webassembly4j.provider.endive;

import ai.tegmentum.webassembly4j.api.config.WebAssemblyConfig;
import ai.tegmentum.webassembly4j.api.config.WebAssemblyConfigBuilder;
import ai.tegmentum.webassembly4j.provider.endive.config.EndiveConfig;

import java.util.function.Consumer;

/**
 * Convenience entry point for building a {@link WebAssemblyConfig} pre-wired for the
 * Endive pure-Java engine.
 *
 * <h2>Examples</h2>
 * <pre>{@code
 * // Just Endive tunings:
 * WebAssemblyConfig cfg = EndiveConfigs.of(b -> b
 *         .executionMode(EndiveConfig.ExecutionMode.COMPILE));
 *
 * // Default interpreter + cross-engine settings:
 * WebAssemblyConfig cfg = EndiveConfigs.builder(b -> {})
 *         .debug(true)
 *         .build();
 * }</pre>
 */
public final class EndiveConfigs {

    public static final String ENGINE_ID = "endive";

    private EndiveConfigs() {}

    public static WebAssemblyConfig of(Consumer<EndiveConfig.Builder> configurator) {
        return builder(configurator).build();
    }

    public static WebAssemblyConfigBuilder builder(Consumer<EndiveConfig.Builder> configurator) {
        EndiveConfig.Builder cb = EndiveConfig.builder();
        configurator.accept(cb);
        return WebAssemblyConfig.builder()
                .engine(ENGINE_ID)
                .engineConfig(cb.build());
    }
}
