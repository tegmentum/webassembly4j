package ai.tegmentum.webassembly4j.api.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ComponentConfigTest {

    @Test
    void defaultsAreEmpty() {
        ComponentConfig config = ComponentConfig.builder().build();

        assertFalse(config.fuelLimit().isPresent());
        assertFalse(config.epochDeadline().isPresent());
        assertFalse(config.maxMemoryBytes().isPresent());
        assertFalse(config.maxTableElements().isPresent());
        assertFalse(config.maxInstances().isPresent());
        assertFalse(config.maxTables().isPresent());
        assertFalse(config.maxMemories().isPresent());
        assertFalse(config.trapOnGrowFailure());
    }

    @Test
    void builderSetsValues() {
        ComponentConfig config = ComponentConfig.builder()
                .fuelLimit(1000)
                .epochDeadline(5)
                .maxMemoryBytes(65536)
                .maxTableElements(100)
                .maxInstances(10)
                .maxTables(4)
                .maxMemories(2)
                .trapOnGrowFailure(true)
                .build();

        assertEquals(1000, config.fuelLimit().getAsLong());
        assertEquals(5, config.epochDeadline().getAsLong());
        assertEquals(65536, config.maxMemoryBytes().getAsLong());
        assertEquals(100, config.maxTableElements().getAsLong());
        assertEquals(10, config.maxInstances().getAsLong());
        assertEquals(4, config.maxTables().getAsLong());
        assertEquals(2, config.maxMemories().getAsLong());
        assertTrue(config.trapOnGrowFailure());
    }
}
