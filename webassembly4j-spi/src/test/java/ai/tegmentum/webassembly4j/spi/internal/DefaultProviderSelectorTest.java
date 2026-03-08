package ai.tegmentum.webassembly4j.spi.internal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ai.tegmentum.webassembly4j.api.Engine;
import ai.tegmentum.webassembly4j.api.config.EngineConfig;
import ai.tegmentum.webassembly4j.api.config.WebAssemblyConfig;
import ai.tegmentum.webassembly4j.spi.*;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class DefaultProviderSelectorTest {

    private DefaultProviderSelector selector;

    @BeforeEach
    void setUp() {
        selector = new DefaultProviderSelector();
    }

    @Test
    void emptyProvidersReturnsEmpty() {
        ProviderContext ctx = context(null, null);
        ProviderSelectionResult result = selector.select(Collections.emptyList(), ctx);

        assertFalse(result.selectedProvider().isPresent());
        assertTrue(result.explanation().contains("No providers"));
    }

    @Test
    void selectsSingleAvailableProvider() {
        EngineProvider provider = stubProvider("wasmtime", "wasmtime-ffm", 200, 11, true);
        ProviderContext ctx = context(null, null);

        ProviderSelectionResult result = selector.select(Collections.singletonList(provider), ctx);

        assertTrue(result.selectedProvider().isPresent());
        assertEquals("wasmtime-ffm", result.selectedProvider().get().descriptor().providerId());
    }

    @Test
    void filtersUnavailableProviders() {
        EngineProvider unavailable = stubProvider("wasmtime", "wasmtime-ffm", 200, 11, false);
        ProviderContext ctx = context(null, null);

        ProviderSelectionResult result = selector.select(
                Collections.singletonList(unavailable), ctx);

        assertFalse(result.selectedProvider().isPresent());
    }

    @Test
    void filtersByEngineId() {
        EngineProvider wasmtime = stubProvider("wasmtime", "wasmtime-ffm", 200, 11, true);
        EngineProvider chicory = stubProvider("chicory", "chicory", 200, 11, true);
        ProviderContext ctx = context("chicory", null);

        ProviderSelectionResult result = selector.select(Arrays.asList(wasmtime, chicory), ctx);

        assertTrue(result.selectedProvider().isPresent());
        assertEquals("chicory", result.selectedProvider().get().descriptor().providerId());
    }

    @Test
    void filtersByProviderId() {
        EngineProvider ffm = stubProvider("wasmtime", "wasmtime-ffm", 200, 22, true);
        EngineProvider jni = stubProvider("wasmtime", "wasmtime-j11", 100, 11, true);
        ProviderContext ctx = context(null, "wasmtime-j11");

        ProviderSelectionResult result = selector.select(Arrays.asList(ffm, jni), ctx);

        assertTrue(result.selectedProvider().isPresent());
        assertEquals("wasmtime-j11", result.selectedProvider().get().descriptor().providerId());
    }

    @Test
    void filtersByJavaVersion() {
        EngineProvider ffm = stubProvider("wasmtime", "wasmtime-ffm", 200, 22, true);
        EngineProvider jni = stubProvider("wasmtime", "wasmtime-j11", 100, 11, true);
        ProviderContext ctx = contextWithJava(11, null, null);

        ProviderSelectionResult result = selector.select(Arrays.asList(ffm, jni), ctx);

        assertTrue(result.selectedProvider().isPresent());
        assertEquals("wasmtime-j11", result.selectedProvider().get().descriptor().providerId());
    }

    @Test
    void selectsHighestPriority() {
        EngineProvider ffm = stubProvider("wasmtime", "wasmtime-ffm", 200, 11, true);
        EngineProvider jni = stubProvider("wasmtime", "wasmtime-j11", 100, 11, true);
        ProviderContext ctx = context(null, null);

        ProviderSelectionResult result = selector.select(Arrays.asList(jni, ffm), ctx);

        assertTrue(result.selectedProvider().isPresent());
        assertEquals("wasmtime-ffm", result.selectedProvider().get().descriptor().providerId());
    }

    @Test
    void ambiguousTiesFail() {
        EngineProvider a = stubProvider("wasmtime", "wasmtime-a", 200, 11, true);
        EngineProvider b = stubProvider("wasmtime", "wasmtime-b", 200, 11, true);
        ProviderContext ctx = context(null, null);

        ProviderSelectionResult result = selector.select(Arrays.asList(a, b), ctx);

        assertFalse(result.selectedProvider().isPresent());
        assertTrue(result.explanation().contains("Ambiguous"));
    }

    private ProviderContext context(String engineId, String providerId) {
        return contextWithJava(22, engineId, providerId);
    }

    private ProviderContext contextWithJava(int javaVersion, String engineId, String providerId) {
        return new DefaultProviderContext(javaVersion, null, engineId, providerId);
    }

    private EngineProvider stubProvider(String engineId, String providerId,
                                       int priority, int minJava, boolean available) {
        return new EngineProvider() {
            @Override
            public ProviderDescriptor descriptor() {
                return new ProviderDescriptor() {
                    @Override public String engineId() { return engineId; }
                    @Override public String providerId() { return providerId; }
                    @Override public String version() { return "1.0.0"; }
                    @Override public int minimumJavaVersion() { return minJava; }
                    @Override public Set<String> tags() { return Collections.emptySet(); }
                    @Override public int priority() { return priority; }
                };
            }

            @Override
            public ProviderAvailability availability() {
                return new ProviderAvailability() {
                    @Override public boolean available() { return available; }
                    @Override public String message() { return available ? "OK" : "Unavailable"; }
                };
            }

            @Override
            public ValidationResult validate(WebAssemblyConfig config) {
                return DefaultValidationResult.ok();
            }

            @Override
            public boolean supports(EngineConfig engineConfig) {
                return true;
            }

            @Override
            public Engine create(WebAssemblyConfig config) {
                return null;
            }
        };
    }
}
