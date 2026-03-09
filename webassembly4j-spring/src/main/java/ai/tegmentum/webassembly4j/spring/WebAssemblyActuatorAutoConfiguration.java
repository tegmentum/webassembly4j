package ai.tegmentum.webassembly4j.spring;

import ai.tegmentum.webassembly4j.api.Engine;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * Auto-configuration for WebAssembly4J actuator endpoints.
 */
@AutoConfiguration(after = WebAssemblyAutoConfiguration.class)
@ConditionalOnClass({HealthIndicator.class, Engine.class})
@ConditionalOnBean(Engine.class)
public class WebAssemblyActuatorAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(name = "wasmHealthIndicator")
    public WebAssemblyHealthIndicator wasmHealthIndicator(Engine engine) {
        return new WebAssemblyHealthIndicator(engine);
    }

    @Bean
    @ConditionalOnMissingBean
    public WebAssemblyInfoEndpoint wasmInfoEndpoint(Engine engine) {
        return new WebAssemblyInfoEndpoint(engine);
    }
}
