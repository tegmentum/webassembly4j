package ai.tegmentum.webassembly4j.api;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ValueTypeTest {

    @Test
    void allValueTypesExist() {
        assertEquals(7, ValueType.values().length);
        assertNotNull(ValueType.I32);
        assertNotNull(ValueType.I64);
        assertNotNull(ValueType.F32);
        assertNotNull(ValueType.F64);
        assertNotNull(ValueType.V128);
        assertNotNull(ValueType.FUNCREF);
        assertNotNull(ValueType.EXTERNREF);
    }
}
