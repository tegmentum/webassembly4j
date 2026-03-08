package ai.tegmentum.webassembly4j.provider.wamr;

import ai.tegmentum.webassembly4j.api.config.EngineConfig;
import ai.tegmentum.webassembly4j.provider.wamr.config.WamrConfig;
import ai.tegmentum.webassembly4j.provider.wamr.config.WamrRunningMode;
import ai.tegmentum.webassembly4j.spi.ProviderAvailability;
import ai.tegmentum.webassembly4j.spi.ProviderDescriptor;
import ai.tegmentum.webassembly4j.spi.ValidationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class WamrProviderTest {

    private WamrProvider provider;

    @BeforeEach
    void setUp() {
        provider = new WamrProvider();
    }

    @Test
    void descriptorHasCorrectValues() {
        ProviderDescriptor desc = provider.descriptor();
        assertEquals("wamr", desc.engineId());
        assertEquals("wamr", desc.providerId());
        assertEquals(17, desc.minimumJavaVersion());
        assertTrue(desc.priority() > 0);
        assertFalse(desc.tags().isEmpty());
        assertTrue(desc.tags().contains("native"));
        assertTrue(desc.tags().contains("interpreter"));
    }

    @Test
    void availabilityReturnsResult() {
        ProviderAvailability availability = provider.availability();
        assertNotNull(availability.message());
        System.out.println("Availability: " + availability.available()
                + " - " + availability.message());
    }

    @Test
    void supportsWamrConfig() {
        WamrConfig config = WamrConfig.builder()
                .runningMode(WamrRunningMode.INTERP)
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
