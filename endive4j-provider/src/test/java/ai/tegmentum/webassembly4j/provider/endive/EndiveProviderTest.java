package ai.tegmentum.webassembly4j.provider.endive;

import ai.tegmentum.webassembly4j.api.Engine;
import ai.tegmentum.webassembly4j.api.EngineCapabilities;
import ai.tegmentum.webassembly4j.api.EngineInfo;
import ai.tegmentum.webassembly4j.api.config.WebAssemblyConfig;
import ai.tegmentum.webassembly4j.api.exception.UnsupportedFeatureException;
import ai.tegmentum.webassembly4j.api.exception.ValidationException;
import ai.tegmentum.webassembly4j.api.exception.WebAssemblyException;
import ai.tegmentum.webassembly4j.provider.endive.config.EndiveConfig;
import ai.tegmentum.webassembly4j.spi.ProviderAvailability;
import ai.tegmentum.webassembly4j.spi.ProviderDescriptor;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EndiveProviderTest {

    private final EndiveProvider provider = new EndiveProvider();

    @Test
    void descriptorIsCorrect() {
        ProviderDescriptor desc = provider.descriptor();
        assertEquals("endive", desc.engineId());
        assertEquals("endive", desc.providerId());
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
            assertEquals("endive", info.engineId());
            assertEquals("endive", info.providerId());
            assertEquals(11, info.minimumJavaVersion());
        }
    }

    @Test
    void capabilities() {
        try (Engine engine = provider.create(null)) {
            EngineCapabilities caps = engine.capabilities();
            assertTrue(caps.supportsCoreModules());
            // supportsComponents() reflects runtime-guest presence; asserting a
            // hard value would make the test order-dependent on the env
            // (see WasmcmGuestBlobLocator). Locking in the rest of the surface.
            assertTrue(caps.supportsWasi());
            assertFalse(caps.supportsFuel());
            assertFalse(caps.supportsNativeInterop());
            assertTrue(caps.supportsGc());
            assertTrue(caps.supportsThreads());
            assertTrue(caps.supportsReferenceTypes());
        }
    }

    @Test
    void loadComponentSurfacesFailureCleanly() {
        try (Engine engine = provider.create(null)) {
            // Two honest outcomes depending on whether the wasmcm_runtime_guest.wasm
            // blob is resolvable at test time:
            //   - guest present  -> the guest rejects the empty payload with a
            //                        malformed/invalid diagnostic; the provider
            //                        wraps that as ValidationException.
            //   - guest missing  -> the provider surfaces UnsupportedFeatureException
            //                        naming the blob and how to point at it.
            // Both are WebAssemblyException subtypes; the test locks in the union.
            WebAssemblyException ex = assertThrows(
                    WebAssemblyException.class,
                    () -> engine.loadComponent(new byte[0]));
            assertTrue(
                    ex instanceof UnsupportedFeatureException || ex instanceof ValidationException,
                    "expected UnsupportedFeatureException or ValidationException, got "
                            + ex.getClass().getName());
        }
    }

    @Test
    void configTypeAdvertisesEndiveConfig() {
        assertEquals(EndiveConfig.class, provider.configType().orElse(null));
    }
}
