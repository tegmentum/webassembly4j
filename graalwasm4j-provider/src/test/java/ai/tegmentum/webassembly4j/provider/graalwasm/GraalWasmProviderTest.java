package ai.tegmentum.webassembly4j.provider.graalwasm;

import ai.tegmentum.webassembly4j.api.Engine;
import ai.tegmentum.webassembly4j.api.EngineCapabilities;
import ai.tegmentum.webassembly4j.api.EngineInfo;
import ai.tegmentum.webassembly4j.api.config.WebAssemblyConfig;
import ai.tegmentum.webassembly4j.api.exception.UnsupportedFeatureException;
import ai.tegmentum.webassembly4j.spi.ProviderAvailability;
import ai.tegmentum.webassembly4j.spi.ProviderDescriptor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import static org.junit.jupiter.api.Assertions.*;

class GraalWasmProviderTest {

    private final GraalWasmProvider provider = new GraalWasmProvider();

    @Test
    void descriptorIsCorrect() {
        ProviderDescriptor desc = provider.descriptor();
        assertEquals("graalwasm", desc.engineId());
        assertEquals("graalwasm", desc.providerId());
        assertEquals(17, desc.minimumJavaVersion());
        assertEquals(150, desc.priority());
        assertTrue(desc.tags().contains("jit"));
        assertTrue(desc.tags().contains("polyglot"));
    }

    @Test
    void availabilityReportsCorrectly() {
        ProviderAvailability avail = provider.availability();
        assertNotNull(avail.message());
    }

    @Test
    void validationAlwaysOk() {
        assertTrue(provider.validate(null).valid());
        assertTrue(provider.validate(WebAssemblyConfig.builder().build()).valid());
    }

    @Test
    @EnabledIf("runtimeAvailable")
    void createEngine() {
        try (Engine engine = provider.create(null)) {
            assertNotNull(engine);
            EngineInfo info = engine.info();
            assertEquals("graalwasm", info.engineId());
            assertEquals("graalwasm", info.providerId());
            assertEquals(17, info.minimumJavaVersion());
        }
    }

    @Test
    @EnabledIf("runtimeAvailable")
    void capabilities() {
        try (Engine engine = provider.create(null)) {
            EngineCapabilities caps = engine.capabilities();
            assertTrue(caps.supportsCoreModules());
            assertFalse(caps.supportsComponents());
            assertFalse(caps.supportsWasi());
            assertFalse(caps.supportsFuel());
            assertFalse(caps.supportsNativeInterop());
        }
    }

    @Test
    @EnabledIf("runtimeAvailable")
    void loadComponentThrowsUnsupported() {
        try (Engine engine = provider.create(null)) {
            assertThrows(UnsupportedFeatureException.class,
                    () -> engine.loadComponent(new byte[0]));
        }
    }

    static boolean runtimeAvailable() {
        return new GraalWasmProvider().availability().available();
    }
}
