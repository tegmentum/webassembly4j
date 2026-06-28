package ai.tegmentum.webassembly4j.provider.wasm3;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ai.tegmentum.webassembly4j.spi.ProviderDescriptor;

import org.junit.jupiter.api.Test;

class Wasm3ProviderTest {

    private final Wasm3Provider provider = new Wasm3Provider();

    @Test
    void descriptorHasExpectedValues() {
        final ProviderDescriptor descriptor = provider.descriptor();
        assertEquals("wasm3", descriptor.engineId());
        assertEquals("wasm3", descriptor.providerId());
        assertEquals(17, descriptor.minimumJavaVersion());
        assertTrue(descriptor.priority() > 0);
        assertTrue(descriptor.tags().contains("native"));
        assertTrue(descriptor.tags().contains("interpreter"));
    }

    @Test
    void availabilityReportsMessage() {
        assertNotNull(provider.availability().message());
    }
}
