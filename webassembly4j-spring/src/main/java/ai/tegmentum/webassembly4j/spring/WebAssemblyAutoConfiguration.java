package ai.tegmentum.webassembly4j.spring;

import ai.tegmentum.webassembly4j.api.Engine;
import ai.tegmentum.webassembly4j.api.WebAssembly;
import ai.tegmentum.webassembly4j.api.WebAssemblyBuilder;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Spring Boot auto-configuration for WebAssembly4J.
 * Automatically creates an {@link Engine} bean when a WebAssembly provider
 * is available on the classpath.
 */
@AutoConfiguration
@ConditionalOnClass(Engine.class)
@ConditionalOnProperty(prefix = "webassembly4j", name = "enabled", havingValue = "true",
        matchIfMissing = true)
@EnableConfigurationProperties(WebAssemblyProperties.class)
public class WebAssemblyAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public Engine wasmEngine(WebAssemblyProperties properties) {
        WebAssemblyBuilder builder = WebAssembly.builder();

        if (properties.getEngine() != null && !properties.getEngine().isEmpty()) {
            builder.engine(properties.getEngine());
        }
        if (properties.getProvider() != null && !properties.getProvider().isEmpty()) {
            builder.provider(properties.getProvider());
        }

        return builder.build();
    }
}
