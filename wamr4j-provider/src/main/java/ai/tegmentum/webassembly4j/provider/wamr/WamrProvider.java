package ai.tegmentum.webassembly4j.provider.wamr;

import ai.tegmentum.webassembly4j.api.Engine;
import ai.tegmentum.webassembly4j.api.config.EngineConfig;
import ai.tegmentum.webassembly4j.api.config.WebAssemblyConfig;
import ai.tegmentum.webassembly4j.provider.wamr.config.WamrConfig;
import ai.tegmentum.webassembly4j.spi.EngineProvider;
import ai.tegmentum.webassembly4j.spi.ProviderAvailability;
import ai.tegmentum.webassembly4j.spi.ProviderDescriptor;
import ai.tegmentum.webassembly4j.spi.ValidationResult;
import ai.tegmentum.webassembly4j.spi.internal.DefaultValidationResult;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class WamrProvider implements EngineProvider {

    private static final String ENGINE_ID = "wamr";
    private static final String PROVIDER_ID = "wamr";
    private static final String VERSION = "1.0.0-SNAPSHOT";
    private static final int MIN_JAVA = 17;
    private static final int PRIORITY = 100;

    @Override
    public ProviderDescriptor descriptor() {
        return new ProviderDescriptor() {
            @Override public String engineId() { return ENGINE_ID; }
            @Override public String providerId() { return PROVIDER_ID; }
            @Override public String version() { return VERSION; }
            @Override public int minimumJavaVersion() { return MIN_JAVA; }
            @Override public Set<String> tags() {
                Set<String> tags = new HashSet<>();
                tags.add("native");
                tags.add("interpreter");
                return Collections.unmodifiableSet(tags);
            }
            @Override public int priority() { return PRIORITY; }
        };
    }

    @Override
    public ProviderAvailability availability() {
        try {
            ai.tegmentum.wamr4j.WebAssemblyRuntime runtime =
                    ai.tegmentum.wamr4j.RuntimeFactory.createRuntime();
            runtime.close();
            return new ProviderAvailability() {
                @Override public boolean available() { return true; }
                @Override public String message() { return "WAMR runtime available"; }
            };
        } catch (Throwable e) {
            final String msg = e.getClass().getName() + ": " + e.getMessage();
            return new ProviderAvailability() {
                @Override public boolean available() { return false; }
                @Override public String message() { return "WAMR not available: " + msg; }
            };
        }
    }

    @Override
    public ValidationResult validate(WebAssemblyConfig config) {
        if (config == null) {
            return DefaultValidationResult.ok();
        }
        List<String> errors = new ArrayList<>();

        config.engineConfig().ifPresent(ec -> {
            if (!(ec instanceof WamrConfig)) {
                errors.add("Engine config must be an instance of WamrConfig, got: "
                        + ec.getClass().getName());
            }
        });

        if (errors.isEmpty()) {
            return DefaultValidationResult.ok();
        }
        return DefaultValidationResult.invalid(errors);
    }

    @Override
    public boolean supports(EngineConfig engineConfig) {
        return engineConfig instanceof WamrConfig;
    }

    @Override
    public Engine create(WebAssemblyConfig config) {
        return WamrEngineAdapter.create(config);
    }
}
