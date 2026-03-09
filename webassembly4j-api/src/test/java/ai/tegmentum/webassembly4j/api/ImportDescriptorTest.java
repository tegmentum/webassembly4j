package ai.tegmentum.webassembly4j.api;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImportDescriptorTest {

    @Test
    void functionImport() {
        ImportDescriptor desc = ImportDescriptor.function("env", "log",
                new ValueType[]{ValueType.I32, ValueType.I32},
                new ValueType[0]);

        assertEquals("env", desc.moduleName());
        assertEquals("log", desc.name());
        assertEquals(ExternType.FUNCTION, desc.type());
        assertArrayEquals(new ValueType[]{ValueType.I32, ValueType.I32}, desc.parameterTypes());
        assertArrayEquals(new ValueType[0], desc.resultTypes());
    }

    @Test
    void memoryImport() {
        ImportDescriptor desc = ImportDescriptor.memory("env", "memory");

        assertEquals("env", desc.moduleName());
        assertEquals("memory", desc.name());
        assertEquals(ExternType.MEMORY, desc.type());
    }

    @Test
    void tableImport() {
        ImportDescriptor desc = ImportDescriptor.table("env", "table");

        assertEquals("env", desc.moduleName());
        assertEquals("table", desc.name());
        assertEquals(ExternType.TABLE, desc.type());
    }

    @Test
    void globalImport() {
        ImportDescriptor desc = ImportDescriptor.global("env", "counter", ValueType.I64);

        assertEquals("env", desc.moduleName());
        assertEquals("counter", desc.name());
        assertEquals(ExternType.GLOBAL, desc.type());
        assertArrayEquals(new ValueType[]{ValueType.I64}, desc.parameterTypes());
    }

    @Test
    void defensiveCopy() {
        ValueType[] params = {ValueType.I32};
        ImportDescriptor desc = ImportDescriptor.function("env", "fn", params, new ValueType[0]);

        params[0] = ValueType.F64;
        assertArrayEquals(new ValueType[]{ValueType.I32}, desc.parameterTypes());
    }

    @Test
    void equalsByModuleNameAndType() {
        ImportDescriptor a = ImportDescriptor.function("env", "log",
                new ValueType[]{ValueType.I32}, new ValueType[0]);
        ImportDescriptor b = ImportDescriptor.function("env", "log",
                new ValueType[]{ValueType.I64}, new ValueType[0]);

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    void notEqualDifferentModule() {
        ImportDescriptor a = ImportDescriptor.function("env", "log",
                new ValueType[0], new ValueType[0]);
        ImportDescriptor b = ImportDescriptor.function("wasi", "log",
                new ValueType[0], new ValueType[0]);

        assertNotEquals(a, b);
    }

    @Test
    void toStringContainsFields() {
        ImportDescriptor desc = ImportDescriptor.function("env", "log",
                new ValueType[0], new ValueType[0]);
        String str = desc.toString();
        assertTrue(str.contains("env"));
        assertTrue(str.contains("log"));
        assertTrue(str.contains("FUNCTION"));
    }
}
