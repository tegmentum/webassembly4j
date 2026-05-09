package ai.tegmentum.webassembly4j.spi;

import ai.tegmentum.webassembly4j.api.Engine;
import ai.tegmentum.webassembly4j.api.config.EngineConfig;
import ai.tegmentum.webassembly4j.api.config.WebAssemblyConfig;
import ai.tegmentum.webassembly4j.spi.internal.DefaultValidationResult;

import java.util.Collections;
import java.util.Optional;

public interface EngineProvider {

    ProviderDescriptor descriptor();

    ProviderAvailability availability();

    /**
     * The concrete {@link EngineConfig} subtype this provider accepts, if any.
     *
     * <p>Returning a non-empty value lets the SPI's default {@link #supports(EngineConfig)}
     * and {@link #validate(WebAssemblyConfig)} implementations reject mismatched config
     * automatically, and lets tooling discover which builder a user should reach for.
     * Providers with no engine-specific config (e.g. GraalWasm) return
     * {@link Optional#empty()}.</p>
     */
    default Optional<Class<? extends EngineConfig>> configType() {
        return Optional.empty();
    }

    default ValidationResult validate(WebAssemblyConfig config) {
        if (config == null) {
            return DefaultValidationResult.ok();
        }
        Optional<Class<? extends EngineConfig>> expected = configType();
        return config.engineConfig()
                .map(ec -> {
                    if (expected.isPresent() && !expected.get().isInstance(ec)) {
                        return DefaultValidationResult.invalid(Collections.singletonList(
                                "Engine config must be an instance of "
                                        + expected.get().getSimpleName()
                                        + ", got: " + ec.getClass().getName()));
                    }
                    if (!expected.isPresent()) {
                        return DefaultValidationResult.invalid(Collections.singletonList(
                                "Provider " + descriptor().providerId()
                                        + " does not accept engine-specific config; got: "
                                        + ec.getClass().getName()));
                    }
                    return DefaultValidationResult.ok();
                })
                .orElseGet(DefaultValidationResult::ok);
    }

    default boolean supports(EngineConfig engineConfig) {
        if (engineConfig == null) {
            return true;
        }
        return configType().map(t -> t.isInstance(engineConfig)).orElse(false);
    }

    Engine create(WebAssemblyConfig config);
}
