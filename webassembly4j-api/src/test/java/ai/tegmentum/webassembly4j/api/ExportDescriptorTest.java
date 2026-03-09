package ai.tegmentum.webassembly4j.api;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExportDescriptorTest {

    @Test
    void functionExport() {
        ExportDescriptor desc = ExportDescriptor.function("add",
                new ValueType[]{ValueType.I32, ValueType.I32},
                new ValueType[]{ValueType.I32});

        assertEquals("add", desc.name());
        assertEquals(ExternType.FUNCTION, desc.type());
        assertArrayEquals(new ValueType[]{ValueType.I32, ValueType.I32}, desc.parameterTypes());
        assertArrayEquals(new ValueType[]{ValueType.I32}, desc.resultTypes());
    }

    @Test
    void memoryExport() {
        ExportDescriptor desc = ExportDescriptor.memory("memory");

        assertEquals("memory", desc.name());
        assertEquals(ExternType.MEMORY, desc.type());
        assertArrayEquals(new ValueType[0], desc.parameterTypes());
        assertArrayEquals(new ValueType[0], desc.resultTypes());
    }

    @Test
    void tableExport() {
        ExportDescriptor desc = ExportDescriptor.table("table0");

        assertEquals("table0", desc.name());
        assertEquals(ExternType.TABLE, desc.type());
    }

    @Test
    void globalExport() {
        ExportDescriptor desc = ExportDescriptor.global("counter", ValueType.I32);

        assertEquals("counter", desc.name());
        assertEquals(ExternType.GLOBAL, desc.type());
        assertArrayEquals(new ValueType[]{ValueType.I32}, desc.parameterTypes());
    }

    @Test
    void defensiveCopy() {
        ValueType[] params = {ValueType.I32};
        ValueType[] results = {ValueType.I64};
        ExportDescriptor desc = ExportDescriptor.function("fn", params, results);

        params[0] = ValueType.F64;
        results[0] = ValueType.F32;

        assertArrayEquals(new ValueType[]{ValueType.I32}, desc.parameterTypes());
        assertArrayEquals(new ValueType[]{ValueType.I64}, desc.resultTypes());
    }

    @Test
    void equalsByNameAndType() {
        ExportDescriptor a = ExportDescriptor.function("add",
                new ValueType[]{ValueType.I32}, new ValueType[]{ValueType.I32});
        ExportDescriptor b = ExportDescriptor.function("add",
                new ValueType[]{ValueType.I64}, new ValueType[]{ValueType.I64});

        assertEquals(a, b); // Same name and type
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    void notEqualDifferentName() {
        ExportDescriptor a = ExportDescriptor.function("add",
                new ValueType[0], new ValueType[0]);
        ExportDescriptor b = ExportDescriptor.function("sub",
                new ValueType[0], new ValueType[0]);

        assertNotEquals(a, b);
    }

    @Test
    void notEqualDifferentType() {
        ExportDescriptor a = ExportDescriptor.function("x",
                new ValueType[0], new ValueType[0]);
        ExportDescriptor b = ExportDescriptor.memory("x");

        assertNotEquals(a, b);
    }

    @Test
    void toStringContainsNameAndType() {
        ExportDescriptor desc = ExportDescriptor.function("add",
                new ValueType[0], new ValueType[0]);
        String str = desc.toString();
        assertTrue(str.contains("add"));
        assertTrue(str.contains("FUNCTION"));
    }
}
