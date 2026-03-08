package ai.tegmentum.webassembly4j.api;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DefaultLinkingContextTest {

    @Test
    void emptyContextHasNullWasi() {
        DefaultLinkingContext ctx = DefaultLinkingContext.builder().build();
        assertNull(ctx.wasiContext());
        assertTrue(ctx.imports().isEmpty());
    }

    @Test
    void importsArePreserved() {
        DefaultLinkingContext ctx = DefaultLinkingContext.builder()
                .addImport("memory", "mem0")
                .addImport("func", "fn0")
                .build();

        assertEquals(2, ctx.imports().size());
        assertEquals("mem0", ctx.imports().get("memory"));
    }

    @Test
    void importsAreUnmodifiable() {
        DefaultLinkingContext ctx = DefaultLinkingContext.builder()
                .addImport("key", "val")
                .build();

        assertThrows(UnsupportedOperationException.class,
                () -> ctx.imports().put("new", "entry"));
    }

    @Test
    void implementsLinkingContext() {
        DefaultLinkingContext ctx = DefaultLinkingContext.builder().build();
        assertInstanceOf(LinkingContext.class, ctx);
    }

    @Test
    void emptyContextHasNoHostFunctions() {
        DefaultLinkingContext ctx = DefaultLinkingContext.builder().build();
        assertTrue(ctx.hostFunctions().isEmpty());
    }

    @Test
    void hostFunctionsArePreserved() {
        DefaultLinkingContext ctx = DefaultLinkingContext.builder()
                .addHostFunction("env", "log",
                        new ValueType[] { ValueType.I32 },
                        new ValueType[0],
                        args -> null)
                .build();

        assertEquals(1, ctx.hostFunctions().size());
        HostFunctionDefinition def = ctx.hostFunctions().get(0);
        assertEquals("env", def.moduleName());
        assertEquals("log", def.functionName());
        assertArrayEquals(new ValueType[] { ValueType.I32 }, def.parameterTypes());
        assertEquals(0, def.resultTypes().length);
    }

    @Test
    void hostFunctionsAreUnmodifiable() {
        DefaultLinkingContext ctx = DefaultLinkingContext.builder()
                .addHostFunction("env", "log",
                        new ValueType[] { ValueType.I32 },
                        new ValueType[0],
                        args -> null)
                .build();

        assertThrows(UnsupportedOperationException.class,
                () -> ctx.hostFunctions().add(new HostFunctionDefinition(
                        "m", "f", new ValueType[0], new ValueType[0], args -> null)));
    }
}
