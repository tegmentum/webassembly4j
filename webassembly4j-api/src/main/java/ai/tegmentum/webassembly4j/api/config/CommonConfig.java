package ai.tegmentum.webassembly4j.api.config;

import java.time.Duration;
import java.util.Optional;

public interface CommonConfig {

    Optional<WasiConfig> wasi();

    Optional<ResourceLimits> resourceLimits();

    Optional<OptimizationLevel> optimizationLevel();

    Optional<Duration> timeout();

    Optional<Boolean> debug();

    Optional<Long> fuelLimit();
}
