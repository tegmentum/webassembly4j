package ai.tegmentum.webassembly4j.api.config;

import java.util.Map;
import java.util.Optional;

public interface WebAssemblyConfig {

    Optional<String> engineId();

    CommonConfig commonConfig();

    Optional<EngineConfig> engineConfig();

    Map<String, Object> extraOptions();

    static WebAssemblyConfigBuilder builder() {
        return new DefaultWebAssemblyConfigBuilder();
    }
}
