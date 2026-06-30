package ai.tegmentum.webassembly4j.provider.wasmtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ai.tegmentum.wasmtime4j.wasi.WasiPreview2Config;
import ai.tegmentum.webassembly4j.api.DefaultWasiContext;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Fix 1 unit test: a WasiContext attached to component instantiation must be forwarded into the
 * component's WasiPreview2Config (preopens / env / stdio), not dropped. Pure mapping test — no
 * native library required (WasiPreview2Config is a config holder).
 */
class WasmtimeComponentAdapterWasiTest {

    @Test
    @DisplayName("WasiContext (env/stdio/preopens) is forwarded into the component WASI config")
    void forwardsWasiContext() {
        DefaultWasiContext wasi = DefaultWasiContext.builder()
                .env("SVLN_FIX1", "ok")
                .env("LOG_LEVEL", "INFO")
                .inheritStdout(true)
                .preopenDir("/fiji-jdk")
                .preopenDir("/cp")
                .build();

        WasiPreview2Config cfg = WasmtimeComponentAdapter.toWasiPreview2Config(wasi);

        // env forwarded (was silently dropped before Fix 1).
        assertEquals("ok", cfg.getEnv().get("SVLN_FIX1"));
        assertEquals("INFO", cfg.getEnv().get("LOG_LEVEL"));

        // stdio inheritance forwarded.
        assertTrue(cfg.isInheritStdout(), "inheritStdout should be forwarded");
        assertFalse(cfg.isInheritStderr(), "inheritStderr was not set");

        // preopens forwarded (host == guest for now).
        Set<String> guests = new HashSet<>();
        for (WasiPreview2Config.PreopenDir d : cfg.getPreopenDirs()) {
            guests.add(d.getGuestPath());
        }
        assertEquals(2, cfg.getPreopenDirs().size());
        assertTrue(guests.contains("/fiji-jdk"));
        assertTrue(guests.contains("/cp"));

        // Capability-minimal default: network denied unless explicitly granted.
        assertFalse(cfg.isAllowNetwork(), "network must default to denied");
    }

    @Test
    @DisplayName("Empty WasiContext yields an empty, network-denied WASI config")
    void emptyContextIsDenyAll() {
        WasiPreview2Config cfg =
                WasmtimeComponentAdapter.toWasiPreview2Config(DefaultWasiContext.builder().build());
        assertTrue(cfg.getEnv().isEmpty());
        assertTrue(cfg.getPreopenDirs().isEmpty());
        assertFalse(cfg.isAllowNetwork());
        assertFalse(cfg.isInheritStdout());
    }
}
