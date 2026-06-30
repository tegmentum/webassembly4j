package ai.tegmentum.webassembly4j.api.config;

/**
 * Marker interface for engine-specific configuration objects.
 *
 * <p>The {@code webassembly4j-api} module is intentionally free of provider-specific
 * types, so this interface carries no methods. Each provider artifact ships its own
 * concrete implementation that exposes the tunables for that engine — for example
 * {@code WasmtimeConfig}, {@code WamrConfig}, or {@code EndiveConfig}. Obtain one
 * from the provider's builder and hand it to
 * {@link WebAssemblyConfigBuilder#engineConfig(EngineConfig)}:</p>
 *
 * <pre>{@code
 * WasmtimeConfig wc = WasmtimeConfig.builder()
 *         .wasmGc(true)
 *         .consumeFuel(true)
 *         .build();
 *
 * WebAssemblyConfig config = WebAssemblyConfig.builder()
 *         .engine("wasmtime")
 *         .engineConfig(wc)
 *         .build();
 * }</pre>
 *
 * <p>For settings that apply across engines (WASI, fuel, debug, optimization,
 * timeout, resource limits) prefer {@link CommonConfig}. For ad-hoc string keys
 * use {@link WebAssemblyConfigBuilder#option(String, Object)}.</p>
 *
 * <p>The provider an {@code EngineConfig} belongs to is discoverable at runtime
 * via {@code EngineProvider.configType()}; the selector uses that to reject a
 * config passed to the wrong engine.</p>
 */
public interface EngineConfig {
}
