package ai.tegmentum.webassembly4j.api.config;

public interface WebAssemblyConfigBuilder {

    WebAssemblyConfigBuilder engine(String engineId);

    WebAssemblyConfigBuilder wasi(WasiConfig wasiConfig);

    WebAssemblyConfigBuilder resourceLimits(ResourceLimits limits);

    WebAssemblyConfigBuilder optimizationLevel(OptimizationLevel level);

    WebAssemblyConfigBuilder timeoutMillis(long timeoutMillis);

    WebAssemblyConfigBuilder debug(boolean enabled);

    WebAssemblyConfigBuilder fuelLimit(long fuel);

    WebAssemblyConfigBuilder engineConfig(EngineConfig config);

    WebAssemblyConfigBuilder option(String key, Object value);

    WebAssemblyConfig build();
}
