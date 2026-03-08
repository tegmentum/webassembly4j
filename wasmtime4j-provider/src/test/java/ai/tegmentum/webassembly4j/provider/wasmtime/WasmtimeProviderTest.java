package ai.tegmentum.webassembly4j.provider.wasmtime;

import ai.tegmentum.webassembly4j.api.config.EngineConfig;
import ai.tegmentum.webassembly4j.provider.wasmtime.config.WasmtimeConfig;
import ai.tegmentum.webassembly4j.spi.ProviderAvailability;
import ai.tegmentum.webassembly4j.spi.ProviderDescriptor;
import ai.tegmentum.webassembly4j.spi.ValidationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class WasmtimeProviderTest {

    private WasmtimeProvider provider;

    @BeforeEach
    void setUp() {
        provider = new WasmtimeProvider();
    }

    @Test
    void descriptorHasCorrectValues() {
        ProviderDescriptor desc = provider.descriptor();
        assertEquals("wasmtime", desc.engineId());
        assertEquals("wasmtime", desc.providerId());
        assertEquals(11, desc.minimumJavaVersion());
        assertTrue(desc.priority() > 0);
        assertFalse(desc.tags().isEmpty());
    }

    @Test
    void availabilityReturnsResult() {
        ProviderAvailability availability = provider.availability();
        assertNotNull(availability.message());
        System.out.println("Availability: " + availability.available()
                + " - " + availability.message());
    }

    @Test
    void supportsWasmtimeConfig() {
        WasmtimeConfig config = WasmtimeConfig.builder()
                .consumeFuel(true)
                .build();
        assertTrue(provider.supports(config));
    }

    @Test
    void doesNotSupportForeignConfig() {
        EngineConfig foreign = new EngineConfig() {};
        assertFalse(provider.supports(foreign));
    }

    @Test
    void validateAcceptsNull() {
        ValidationResult result = provider.validate(null);
        assertTrue(result.valid());
    }
}
