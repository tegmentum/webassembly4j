package ai.tegmentum.webassembly4j.spring;

import ai.tegmentum.webassembly4j.api.Engine;
import ai.tegmentum.webassembly4j.api.EngineInfo;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;

/**
 * Health indicator that reports the status of the WebAssembly engine.
 */
public class WebAssemblyHealthIndicator implements HealthIndicator {

    private final Engine engine;

    public WebAssemblyHealthIndicator(Engine engine) {
        this.engine = engine;
    }

    @Override
    public Health health() {
        try {
            EngineInfo info = engine.info();
            return Health.up()
                    .withDetail("engine", info.engineId())
                    .withDetail("provider", info.providerId())
                    .withDetail("engineVersion", info.engineVersion())
                    .withDetail("providerVersion", info.providerVersion())
                    .build();
        } catch (Exception e) {
            return Health.down()
                    .withException(e)
                    .build();
        }
    }
}
