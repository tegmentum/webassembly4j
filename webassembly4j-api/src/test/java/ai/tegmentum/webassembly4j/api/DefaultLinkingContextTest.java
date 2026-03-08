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
}
