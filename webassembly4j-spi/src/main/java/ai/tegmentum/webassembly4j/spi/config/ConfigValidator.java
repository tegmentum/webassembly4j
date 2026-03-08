package ai.tegmentum.webassembly4j.spi.config;

import ai.tegmentum.webassembly4j.api.config.WebAssemblyConfig;
import ai.tegmentum.webassembly4j.spi.ValidationResult;

public interface ConfigValidator {

    ValidationResult validate(WebAssemblyConfig config);
}
