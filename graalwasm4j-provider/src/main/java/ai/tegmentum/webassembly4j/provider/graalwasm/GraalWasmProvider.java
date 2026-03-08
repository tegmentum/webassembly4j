package ai.tegmentum.webassembly4j.provider.graalwasm;

import ai.tegmentum.webassembly4j.api.Engine;
import ai.tegmentum.webassembly4j.api.config.EngineConfig;
import ai.tegmentum.webassembly4j.api.config.WebAssemblyConfig;
import ai.tegmentum.webassembly4j.spi.EngineProvider;
import ai.tegmentum.webassembly4j.spi.ProviderAvailability;
import ai.tegmentum.webassembly4j.spi.ProviderDescriptor;
import ai.tegmentum.webassembly4j.spi.ValidationResult;
import ai.tegmentum.webassembly4j.spi.internal.DefaultValidationResult;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public final class GraalWasmProvider implements EngineProvider {

    private static final String ENGINE_ID = "graalwasm";
    private static final String PROVIDER_ID = "graalwasm";
    private static final String VERSION = "1.0.0-SNAPSHOT";
    private static final int MIN_JAVA = 17;
    private static final int PRIORITY = 150;

    @Override
    public ProviderDescriptor descriptor() {
        return new ProviderDescriptor() {
            @Override public String engineId() { return ENGINE_ID; }
            @Override public String providerId() { return PROVIDER_ID; }
            @Override public String version() { return VERSION; }
            @Override public int minimumJavaVersion() { return MIN_JAVA; }
            @Override public Set<String> tags() {
                Set<String> tags = new HashSet<>();
                tags.add("jit");
                tags.add("polyglot");
                return Collections.unmodifiableSet(tags);
            }
            @Override public int priority() { return PRIORITY; }
        };
    }

    @Override
    public ProviderAvailability availability() {
        try {
            Class.forName("org.graalvm.polyglot.Context");
            org.graalvm.polyglot.Context context =
                    org.graalvm.polyglot.Context.newBuilder("wasm").build();
            context.close();
            return new ProviderAvailability() {
                @Override public boolean available() { return true; }
                @Override public String message() { return "GraalWasm runtime available"; }
            };
        } catch (Exception e) {
            String msg = "GraalWasm runtime not available: " + e.getMessage();
            return new ProviderAvailability() {
                @Override public boolean available() { return false; }
                @Override public String message() { return msg; }
            };
        }
    }

    @Override
    public ValidationResult validate(WebAssemblyConfig config) {
        return DefaultValidationResult.ok();
    }

    @Override
    public boolean supports(EngineConfig engineConfig) {
        return false;
    }

    @Override
    public Engine create(WebAssemblyConfig config) {
        return GraalWasmEngineAdapter.create(config);
    }
}
