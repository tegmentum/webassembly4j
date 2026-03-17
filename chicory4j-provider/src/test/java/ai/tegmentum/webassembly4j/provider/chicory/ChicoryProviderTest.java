package ai.tegmentum.webassembly4j.provider.chicory;

import ai.tegmentum.webassembly4j.api.Engine;
import ai.tegmentum.webassembly4j.api.EngineCapabilities;
import ai.tegmentum.webassembly4j.api.EngineInfo;
import ai.tegmentum.webassembly4j.api.config.WebAssemblyConfig;
import ai.tegmentum.webassembly4j.api.exception.UnsupportedFeatureException;
import ai.tegmentum.webassembly4j.spi.ProviderAvailability;
import ai.tegmentum.webassembly4j.spi.ProviderDescriptor;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ChicoryProviderTest {

    private final ChicoryProvider provider = new ChicoryProvider();

    @Test
    void descriptorIsCorrect() {
        ProviderDescriptor desc = provider.descriptor();
        assertEquals("chicory", desc.engineId());
        assertEquals("chicory", desc.providerId());
        assertEquals(11, desc.minimumJavaVersion());
        assertEquals(50, desc.priority());
        assertTrue(desc.tags().contains("pure-java"));
        assertTrue(desc.tags().contains("interpreter"));
    }

    @Test
    void alwaysAvailable() {
        ProviderAvailability avail = provider.availability();
        assertTrue(avail.available());
    }

    @Test
    void validationAlwaysOk() {
        assertTrue(provider.validate(null).valid());
        assertTrue(provider.validate(WebAssemblyConfig.builder().build()).valid());
    }

    @Test
    void createEngine() {
        try (Engine engine = provider.create(null)) {
            assertNotNull(engine);
            EngineInfo info = engine.info();
            assertEquals("chicory", info.engineId());
            assertEquals("chicory", info.providerId());
            assertEquals(11, info.minimumJavaVersion());
        }
    }

    @Test
    void capabilities() {
        try (Engine engine = provider.create(null)) {
            EngineCapabilities caps = engine.capabilities();
            assertTrue(caps.supportsCoreModules());
            assertFalse(caps.supportsComponents());
            assertTrue(caps.supportsWasi());
            assertFalse(caps.supportsFuel());
            assertFalse(caps.supportsNativeInterop());
            assertTrue(caps.supportsGc());
            assertTrue(caps.supportsThreads());
            assertTrue(caps.supportsReferenceTypes());
        }
    }

    @Test
    void loadComponentThrowsUnsupported() {
        try (Engine engine = provider.create(null)) {
            assertThrows(UnsupportedFeatureException.class,
                    () -> engine.loadComponent(new byte[0]));
        }
    }
}
