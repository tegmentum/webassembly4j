package ai.tegmentum.webassembly4j.spi.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ConfigConflictTest {

    @Test
    void fieldsArePreserved() {
        ConfigConflict conflict = new ConfigConflict("optimization", "SPEED", "NONE");

        assertEquals("optimization", conflict.property());
        assertEquals("SPEED", conflict.commonValue());
        assertEquals("NONE", conflict.engineValue());
    }

    @Test
    void toStringContainsAllFields() {
        ConfigConflict conflict = new ConfigConflict("opt", "A", "B");
        String str = conflict.toString();

        assertTrue(str.contains("opt"));
        assertTrue(str.contains("A"));
        assertTrue(str.contains("B"));
    }
}
