package ai.tegmentum.webassembly4j.provider.wasmtime;

import ai.tegmentum.webassembly4j.api.config.WebAssemblyConfig;
import ai.tegmentum.webassembly4j.provider.wasmtime.config.WasmtimeConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class WasmtimeConfigsTest {

    @Test
    void ofWiresEngineAndEngineConfig() {
        WebAssemblyConfig cfg = WasmtimeConfigs.of(b -> b
                .wasmGc(true)
                .consumeFuel(true));

        assertEquals("wasmtime", cfg.engineId().orElse(null));
        assertTrue(cfg.engineConfig().isPresent());
        WasmtimeConfig wc = (WasmtimeConfig) cfg.engineConfig().get();
        assertTrue(wc.wasmGc().orElse(false));
        assertTrue(wc.consumeFuel().orElse(false));
    }

    @Test
    void builderHandsOffToCommonSettings() {
        WebAssemblyConfig cfg = WasmtimeConfigs.builder(b -> b.wasmGc(true))
                .debug(true)
                .fuelLimit(1_000_000L)
                .build();

        assertEquals("wasmtime", cfg.engineId().orElse(null));
        assertTrue(cfg.commonConfig().debug().orElse(false));
        assertEquals(1_000_000L, cfg.commonConfig().fuelLimit().orElse(0L));
        WasmtimeConfig wc = (WasmtimeConfig) cfg.engineConfig().get();
        assertTrue(wc.wasmGc().orElse(false));
    }

    @Test
    void emptyConfiguratorStillProducesValidConfig() {
        WebAssemblyConfig cfg = WasmtimeConfigs.of(b -> {});

        assertEquals("wasmtime", cfg.engineId().orElse(null));
        assertTrue(cfg.engineConfig().isPresent());
        assertTrue(cfg.engineConfig().get() instanceof WasmtimeConfig);
    }

    @Test
    void engineIdConstantMatchesProviderDescriptor() {
        assertEquals(new WasmtimeProvider().descriptor().engineId(), WasmtimeConfigs.ENGINE_ID);
    }
}
