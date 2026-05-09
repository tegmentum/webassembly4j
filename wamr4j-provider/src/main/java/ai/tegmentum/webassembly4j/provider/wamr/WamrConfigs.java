package ai.tegmentum.webassembly4j.provider.wamr;

import ai.tegmentum.webassembly4j.api.config.WebAssemblyConfig;
import ai.tegmentum.webassembly4j.api.config.WebAssemblyConfigBuilder;
import ai.tegmentum.webassembly4j.provider.wamr.config.WamrConfig;

import java.util.function.Consumer;

/**
 * Convenience entry point for building a {@link WebAssemblyConfig} pre-wired for the
 * WAMR engine. Avoids hand-typing {@code "wamr"} as the engine id and the two-step
 * dance of constructing a {@link WamrConfig} separately.
 *
 * <h2>Examples</h2>
 * <pre>{@code
 * // Just WAMR tunings:
 * WebAssemblyConfig cfg = WamrConfigs.of(b -> b
 *         .runningMode(WamrRunningMode.INTERP)
 *         .defaultStackSize(64 * 1024));
 *
 * // WAMR tunings + cross-engine settings:
 * WebAssemblyConfig cfg = WamrConfigs.builder(b -> b.runningMode(WamrRunningMode.AOT))
 *         .debug(true)
 *         .build();
 * }</pre>
 */
public final class WamrConfigs {

    public static final String ENGINE_ID = "wamr";

    private WamrConfigs() {}

    public static WebAssemblyConfig of(Consumer<WamrConfig.Builder> configurator) {
        return builder(configurator).build();
    }

    public static WebAssemblyConfigBuilder builder(Consumer<WamrConfig.Builder> configurator) {
        WamrConfig.Builder cb = WamrConfig.builder();
        configurator.accept(cb);
        return WebAssemblyConfig.builder()
                .engine(ENGINE_ID)
                .engineConfig(cb.build());
    }
}
