package ai.tegmentum.webassembly4j.provider.chicory;

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

public final class ChicoryProvider implements EngineProvider {

    private static final String ENGINE_ID = "chicory";
    private static final String PROVIDER_ID = "chicory";
    private static final String VERSION = "1.0.0-SNAPSHOT";
    private static final int MIN_JAVA = 11;
    private static final int PRIORITY = 50;

    @Override
    public ProviderDescriptor descriptor() {
        return new ProviderDescriptor() {
            @Override public String engineId() { return ENGINE_ID; }
            @Override public String providerId() { return PROVIDER_ID; }
            @Override public String version() { return VERSION; }
            @Override public int minimumJavaVersion() { return MIN_JAVA; }
            @Override public Set<String> tags() {
                Set<String> tags = new HashSet<>();
                tags.add("pure-java");
                tags.add("interpreter");
                return Collections.unmodifiableSet(tags);
            }
            @Override public int priority() { return PRIORITY; }
        };
    }

    @Override
    public ProviderAvailability availability() {
        return new ProviderAvailability() {
            @Override public boolean available() { return true; }
            @Override public String message() { return "Chicory pure-Java runtime always available"; }
        };
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
        return ChicoryEngineAdapter.create(config);
    }
}
