package ai.tegmentum.webassembly4j.provider.wasmtime;

import ai.tegmentum.webassembly4j.api.Engine;
import ai.tegmentum.webassembly4j.api.config.EngineConfig;
import ai.tegmentum.webassembly4j.api.config.WebAssemblyConfig;
import ai.tegmentum.webassembly4j.spi.EngineProvider;
import ai.tegmentum.webassembly4j.spi.ProviderAvailability;
import ai.tegmentum.webassembly4j.spi.ProviderDescriptor;
import ai.tegmentum.webassembly4j.spi.ValidationResult;
import ai.tegmentum.webassembly4j.spi.internal.DefaultValidationResult;
import ai.tegmentum.webassembly4j.provider.wasmtime.config.WasmtimeConfig;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class WasmtimeProvider implements EngineProvider {

    private static final String ENGINE_ID = "wasmtime";
    private static final String PROVIDER_ID = "wasmtime";
    private static final String VERSION = "1.0.0-SNAPSHOT";
    private static final int MIN_JAVA = 11;
    private static final int PRIORITY = 200;

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
                return Collections.unmodifiableSet(tags);
            }
            @Override public int priority() { return PRIORITY; }
        };
    }

    @Override
    public ProviderAvailability availability() {
        try {
            ai.tegmentum.wasmtime4j.WasmRuntime runtime =
                    ai.tegmentum.wasmtime4j.factory.WasmRuntimeFactory.create();
            runtime.close();
            return new ProviderAvailability() {
                @Override public boolean available() { return true; }
                @Override public String message() { return "Wasmtime runtime available"; }
            };
        } catch (Throwable e) {
            final String msg = e.getClass().getName() + ": " + e.getMessage();
            return new ProviderAvailability() {
                @Override public boolean available() { return false; }
                @Override public String message() { return "Wasmtime not available: " + msg; }
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
            if (!(ec instanceof WasmtimeConfig)) {
                errors.add("Engine config must be an instance of WasmtimeConfig, got: "
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
        return engineConfig instanceof WasmtimeConfig;
    }

    @Override
    public Engine create(WebAssemblyConfig config) {
        return WasmtimeEngineAdapter.create(config);
    }
}
