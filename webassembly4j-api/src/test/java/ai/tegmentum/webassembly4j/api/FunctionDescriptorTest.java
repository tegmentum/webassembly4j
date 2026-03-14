package ai.tegmentum.webassembly4j.api;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FunctionDescriptorTest {

    @Test
    void exportedFactory() {
        FunctionDescriptor desc = FunctionDescriptor.exported("add", 0,
                new ValueType[]{ValueType.I32, ValueType.I32},
                new ValueType[]{ValueType.I32});

        assertEquals("add", desc.name());
        assertEquals(0, desc.index());
        assertTrue(desc.isExported());
        assertFalse(desc.isImported());
        assertFalse(desc.isInternal());
        assertNull(desc.moduleName());
        assertArrayEquals(new ValueType[]{ValueType.I32, ValueType.I32}, desc.parameterTypes());
        assertArrayEquals(new ValueType[]{ValueType.I32}, desc.resultTypes());
    }

    @Test
    void importedFactory() {
        FunctionDescriptor desc = FunctionDescriptor.imported("env", "log", 1,
                new ValueType[]{ValueType.I32},
                new ValueType[0]);

        assertEquals("log", desc.name());
        assertEquals(1, desc.index());
        assertFalse(desc.isExported());
        assertTrue(desc.isImported());
        assertFalse(desc.isInternal());
        assertEquals("env", desc.moduleName());
    }

    @Test
    void internalFactory() {
        FunctionDescriptor desc = FunctionDescriptor.internal("helper", 5,
                new ValueType[0], new ValueType[0]);

        assertEquals("helper", desc.name());
        assertEquals(5, desc.index());
        assertFalse(desc.isExported());
        assertFalse(desc.isImported());
        assertTrue(desc.isInternal());
        assertNull(desc.moduleName());
    }

    @Test
    void internalWithNullName() {
        FunctionDescriptor desc = FunctionDescriptor.internal(null, 3,
                new ValueType[0], new ValueType[0]);

        assertNull(desc.name());
        assertTrue(desc.isInternal());
    }

    @Test
    void defensiveCopyOfParams() {
        ValueType[] params = {ValueType.I32};
        ValueType[] results = {ValueType.I64};
        FunctionDescriptor desc = FunctionDescriptor.exported("fn", 0, params, results);

        params[0] = ValueType.F64;
        results[0] = ValueType.F32;

        assertArrayEquals(new ValueType[]{ValueType.I32}, desc.parameterTypes());
        assertArrayEquals(new ValueType[]{ValueType.I64}, desc.resultTypes());
    }

    @Test
    void defensiveCopyOnAccess() {
        FunctionDescriptor desc = FunctionDescriptor.exported("fn", 0,
                new ValueType[]{ValueType.I32}, new ValueType[]{ValueType.I64});

        desc.parameterTypes()[0] = ValueType.F64;
        desc.resultTypes()[0] = ValueType.F32;

        assertArrayEquals(new ValueType[]{ValueType.I32}, desc.parameterTypes());
        assertArrayEquals(new ValueType[]{ValueType.I64}, desc.resultTypes());
    }

    @Test
    void nullArraysTreatedAsEmpty() {
        FunctionDescriptor desc = FunctionDescriptor.internal("fn", 0, null, null);

        assertArrayEquals(new ValueType[0], desc.parameterTypes());
        assertArrayEquals(new ValueType[0], desc.resultTypes());
    }

    @Test
    void negativeIndexThrows() {
        assertThrows(IllegalArgumentException.class, () ->
                FunctionDescriptor.exported("fn", -1, new ValueType[0], new ValueType[0]));
    }

    @Test
    void exportedNullNameThrows() {
        assertThrows(NullPointerException.class, () ->
                FunctionDescriptor.exported(null, 0, new ValueType[0], new ValueType[0]));
    }

    @Test
    void importedNullModuleNameThrows() {
        assertThrows(NullPointerException.class, () ->
                FunctionDescriptor.imported(null, "fn", 0, new ValueType[0], new ValueType[0]));
    }

    @Test
    void equality() {
        FunctionDescriptor a = FunctionDescriptor.exported("add", 0,
                new ValueType[]{ValueType.I32}, new ValueType[]{ValueType.I32});
        FunctionDescriptor b = FunctionDescriptor.exported("add", 0,
                new ValueType[]{ValueType.I32}, new ValueType[]{ValueType.I32});

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    void inequalityDifferentIndex() {
        FunctionDescriptor a = FunctionDescriptor.exported("add", 0,
                new ValueType[0], new ValueType[0]);
        FunctionDescriptor b = FunctionDescriptor.exported("add", 1,
                new ValueType[0], new ValueType[0]);

        assertNotEquals(a, b);
    }

    @Test
    void toStringContainsName() {
        FunctionDescriptor desc = FunctionDescriptor.exported("add", 0,
                new ValueType[0], new ValueType[0]);
        String str = desc.toString();
        assertTrue(str.contains("add"));
        assertTrue(str.contains("exported"));
    }

    @Test
    void defaultModuleFunctionsReturnsEmptyList() {
        Module module = new Module() {
            @Override
            public Instance instantiate() {
                throw new UnsupportedOperationException();
            }

            @Override
            public Instance instantiate(LinkingContext linkingContext) {
                throw new UnsupportedOperationException();
            }

            @Override
            public void close() {
            }
        };
        assertTrue(module.functions().isEmpty());
    }
}
