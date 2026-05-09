package ai.tegmentum.webassembly4j.provider.graalwasm;

import ai.tegmentum.webassembly4j.api.config.WebAssemblyConfig;
import ai.tegmentum.webassembly4j.api.config.WebAssemblyConfigBuilder;

/**
 * Convenience entry point for building a {@link WebAssemblyConfig} pre-wired for the
 * GraalWasm engine.
 *
 * <p>GraalWasm exposes no engine-specific tunables through this library, so the
 * facade is intentionally minimal — it only avoids hand-typing {@code "graalwasm"}
 * as the engine id. Cross-engine settings (WASI, debug, timeout, etc.) are layered
 * on through {@link #builder()}.</p>
 *
 * <h2>Examples</h2>
 * <pre>{@code
 * WebAssemblyConfig cfg = GraalWasmConfigs.of();
 *
 * WebAssemblyConfig cfg = GraalWasmConfigs.builder()
 *         .debug(true)
 *         .build();
 * }</pre>
 */
public final class GraalWasmConfigs {

    public static final String ENGINE_ID = "graalwasm";

    private GraalWasmConfigs() {}

    public static WebAssemblyConfig of() {
        return builder().build();
    }

    public static WebAssemblyConfigBuilder builder() {
        return WebAssemblyConfig.builder().engine(ENGINE_ID);
    }
}
