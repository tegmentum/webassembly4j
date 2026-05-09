package ai.tegmentum.webassembly4j.provider.wasmtime;

import ai.tegmentum.webassembly4j.api.config.WebAssemblyConfig;
import ai.tegmentum.webassembly4j.api.config.WebAssemblyConfigBuilder;
import ai.tegmentum.webassembly4j.provider.wasmtime.config.WasmtimeConfig;

import java.util.function.Consumer;

/**
 * Convenience entry point for building a {@link WebAssemblyConfig} pre-wired for the
 * Wasmtime engine. Avoids the two-step dance of constructing a {@link WasmtimeConfig}
 * and threading it through {@link WebAssemblyConfig#builder()} with a hand-typed
 * {@code "wasmtime"} engine id.
 *
 * <p>Lambda-style configurator means new tunables on {@link WasmtimeConfig.Builder}
 * propagate here for free — nothing to mirror.</p>
 *
 * <h2>Examples</h2>
 * <pre>{@code
 * // Just wasmtime tunings:
 * WebAssemblyConfig cfg = WasmtimeConfigs.of(b -> b
 *         .wasmGc(true)
 *         .consumeFuel(true));
 *
 * // Wasmtime tunings + cross-engine settings:
 * WebAssemblyConfig cfg = WasmtimeConfigs.builder(b -> b.wasmGc(true))
 *         .debug(true)
 *         .fuelLimit(1_000_000L)
 *         .build();
 *
 * // No wasmtime tunings, just engine selection + common config:
 * WebAssemblyConfig cfg = WasmtimeConfigs.builder(b -> {})
 *         .debug(true)
 *         .build();
 * }</pre>
 *
 * <p>Users who already hold a {@code WasmtimeConfig} can keep using
 * {@link WebAssemblyConfigBuilder#engineConfig} directly; this class is purely
 * additive sugar.</p>
 */
public final class WasmtimeConfigs {

    public static final String ENGINE_ID = "wasmtime";

    private WasmtimeConfigs() {}

    /**
     * Build a fully-wired {@link WebAssemblyConfig} with no cross-engine settings.
     */
    public static WebAssemblyConfig of(Consumer<WasmtimeConfig.Builder> configurator) {
        return builder(configurator).build();
    }

    /**
     * Apply wasmtime-specific configuration and return the api-level builder so the
     * caller can layer on {@link WebAssemblyConfigBuilder#wasi}, {@code debug},
     * {@code fuelLimit}, {@code timeoutMillis}, etc.
     */
    public static WebAssemblyConfigBuilder builder(Consumer<WasmtimeConfig.Builder> configurator) {
        WasmtimeConfig.Builder cb = WasmtimeConfig.builder();
        configurator.accept(cb);
        return WebAssemblyConfig.builder()
                .engine(ENGINE_ID)
                .engineConfig(cb.build());
    }
}
