package ai.tegmentum.webassembly4j.spi;

import ai.tegmentum.webassembly4j.api.Engine;
import ai.tegmentum.webassembly4j.api.config.EngineConfig;
import ai.tegmentum.webassembly4j.api.config.WebAssemblyConfig;

public interface EngineProvider {

    ProviderDescriptor descriptor();

    ProviderAvailability availability();

    ValidationResult validate(WebAssemblyConfig config);

    boolean supports(EngineConfig engineConfig);

    Engine create(WebAssemblyConfig config);
}
