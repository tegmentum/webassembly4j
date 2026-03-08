package ai.tegmentum.webassembly4j.api.config;

import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

final class DefaultWebAssemblyConfigBuilder implements WebAssemblyConfigBuilder {

    private String engineId;
    private WasiConfig wasiConfig;
    private ResourceLimits resourceLimits;
    private OptimizationLevel optimizationLevel;
    private Long timeoutMillis;
    private Boolean debug;
    private Long fuelLimit;
    private EngineConfig engineConfig;
    private final Map<String, Object> extraOptions = new LinkedHashMap<>();

    @Override
    public WebAssemblyConfigBuilder engine(String engineId) {
        this.engineId = engineId;
        return this;
    }

    @Override
    public WebAssemblyConfigBuilder wasi(WasiConfig wasiConfig) {
        this.wasiConfig = wasiConfig;
        return this;
    }

    @Override
    public WebAssemblyConfigBuilder resourceLimits(ResourceLimits limits) {
        this.resourceLimits = limits;
        return this;
    }

    @Override
    public WebAssemblyConfigBuilder optimizationLevel(OptimizationLevel level) {
        this.optimizationLevel = level;
        return this;
    }

    @Override
    public WebAssemblyConfigBuilder timeoutMillis(long timeoutMillis) {
        this.timeoutMillis = timeoutMillis;
        return this;
    }

    @Override
    public WebAssemblyConfigBuilder debug(boolean enabled) {
        this.debug = enabled;
        return this;
    }

    @Override
    public WebAssemblyConfigBuilder fuelLimit(long fuel) {
        this.fuelLimit = fuel;
        return this;
    }

    @Override
    public WebAssemblyConfigBuilder engineConfig(EngineConfig config) {
        this.engineConfig = config;
        return this;
    }

    @Override
    public WebAssemblyConfigBuilder option(String key, Object value) {
        this.extraOptions.put(key, value);
        return this;
    }

    @Override
    public WebAssemblyConfig build() {
        CommonConfig common = new DefaultCommonConfig(
                wasiConfig, resourceLimits, optimizationLevel,
                timeoutMillis != null ? Duration.ofMillis(timeoutMillis) : null,
                debug, fuelLimit);

        return new DefaultWebAssemblyConfig(
                engineId, common, engineConfig,
                Collections.unmodifiableMap(new LinkedHashMap<>(extraOptions)));
    }

    private static final class DefaultWebAssemblyConfig implements WebAssemblyConfig {

        private final String engineId;
        private final CommonConfig commonConfig;
        private final EngineConfig engineConfig;
        private final Map<String, Object> extraOptions;

        DefaultWebAssemblyConfig(String engineId, CommonConfig commonConfig,
                                 EngineConfig engineConfig, Map<String, Object> extraOptions) {
            this.engineId = engineId;
            this.commonConfig = commonConfig;
            this.engineConfig = engineConfig;
            this.extraOptions = extraOptions;
        }

        @Override
        public Optional<String> engineId() {
            return Optional.ofNullable(engineId);
        }

        @Override
        public CommonConfig commonConfig() {
            return commonConfig;
        }

        @Override
        public Optional<EngineConfig> engineConfig() {
            return Optional.ofNullable(engineConfig);
        }

        @Override
        public Map<String, Object> extraOptions() {
            return extraOptions;
        }
    }

    private static final class DefaultCommonConfig implements CommonConfig {

        private final WasiConfig wasiConfig;
        private final ResourceLimits resourceLimits;
        private final OptimizationLevel optimizationLevel;
        private final Duration timeout;
        private final Boolean debug;
        private final Long fuelLimit;

        DefaultCommonConfig(WasiConfig wasiConfig, ResourceLimits resourceLimits,
                            OptimizationLevel optimizationLevel, Duration timeout,
                            Boolean debug, Long fuelLimit) {
            this.wasiConfig = wasiConfig;
            this.resourceLimits = resourceLimits;
            this.optimizationLevel = optimizationLevel;
            this.timeout = timeout;
            this.debug = debug;
            this.fuelLimit = fuelLimit;
        }

        @Override
        public Optional<WasiConfig> wasi() {
            return Optional.ofNullable(wasiConfig);
        }

        @Override
        public Optional<ResourceLimits> resourceLimits() {
            return Optional.ofNullable(resourceLimits);
        }

        @Override
        public Optional<OptimizationLevel> optimizationLevel() {
            return Optional.ofNullable(optimizationLevel);
        }

        @Override
        public Optional<Duration> timeout() {
            return Optional.ofNullable(timeout);
        }

        @Override
        public Optional<Boolean> debug() {
            return Optional.ofNullable(debug);
        }

        @Override
        public Optional<Long> fuelLimit() {
            return Optional.ofNullable(fuelLimit);
        }
    }
}
