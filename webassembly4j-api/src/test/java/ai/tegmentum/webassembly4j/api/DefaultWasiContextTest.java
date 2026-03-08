package ai.tegmentum.webassembly4j.api;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class DefaultWasiContextTest {

    @Test
    void defaultsAreEmpty() {
        DefaultWasiContext ctx = DefaultWasiContext.builder().build();
        assertTrue(ctx.args().isEmpty());
        assertTrue(ctx.env().isEmpty());
        assertFalse(ctx.inheritStdin());
        assertFalse(ctx.inheritStdout());
        assertFalse(ctx.inheritStderr());
        assertTrue(ctx.preopenDirs().isEmpty());
    }

    @Test
    void argsArePreserved() {
        DefaultWasiContext ctx = DefaultWasiContext.builder()
                .addArg("program")
                .addArg("--flag")
                .build();

        assertEquals(Arrays.asList("program", "--flag"), ctx.args());
    }

    @Test
    void argsListReplacesAll() {
        DefaultWasiContext ctx = DefaultWasiContext.builder()
                .addArg("old")
                .args(Arrays.asList("new1", "new2"))
                .build();

        assertEquals(Arrays.asList("new1", "new2"), ctx.args());
    }

    @Test
    void envIsPreserved() {
        DefaultWasiContext ctx = DefaultWasiContext.builder()
                .env("HOME", "/tmp")
                .env("PATH", "/usr/bin")
                .build();

        assertEquals("/tmp", ctx.env().get("HOME"));
        assertEquals("/usr/bin", ctx.env().get("PATH"));
        assertEquals(2, ctx.env().size());
    }

    @Test
    void stdioInheritance() {
        DefaultWasiContext ctx = DefaultWasiContext.builder()
                .inheritStdin(true)
                .inheritStdout(true)
                .inheritStderr(true)
                .build();

        assertTrue(ctx.inheritStdin());
        assertTrue(ctx.inheritStdout());
        assertTrue(ctx.inheritStderr());
    }

    @Test
    void inheritStdioSetsAll() {
        DefaultWasiContext ctx = DefaultWasiContext.builder()
                .inheritStdio(true)
                .build();

        assertTrue(ctx.inheritStdin());
        assertTrue(ctx.inheritStdout());
        assertTrue(ctx.inheritStderr());
    }

    @Test
    void preopenDirsArePreserved() {
        DefaultWasiContext ctx = DefaultWasiContext.builder()
                .preopenDir("/tmp")
                .preopenDir("/data")
                .build();

        assertEquals(Arrays.asList("/tmp", "/data"), ctx.preopenDirs());
    }

    @Test
    void argsAreUnmodifiable() {
        DefaultWasiContext ctx = DefaultWasiContext.builder()
                .addArg("test")
                .build();

        assertThrows(UnsupportedOperationException.class,
                () -> ctx.args().add("new"));
    }

    @Test
    void envIsUnmodifiable() {
        DefaultWasiContext ctx = DefaultWasiContext.builder()
                .env("KEY", "VAL")
                .build();

        assertThrows(UnsupportedOperationException.class,
                () -> ctx.env().put("NEW", "VAL"));
    }

    @Test
    void preopenDirsAreUnmodifiable() {
        DefaultWasiContext ctx = DefaultWasiContext.builder()
                .preopenDir("/tmp")
                .build();

        assertThrows(UnsupportedOperationException.class,
                () -> ctx.preopenDirs().add("/new"));
    }

    @Test
    void implementsWasiContext() {
        DefaultWasiContext ctx = DefaultWasiContext.builder().build();
        assertInstanceOf(WasiContext.class, ctx);
    }
}
