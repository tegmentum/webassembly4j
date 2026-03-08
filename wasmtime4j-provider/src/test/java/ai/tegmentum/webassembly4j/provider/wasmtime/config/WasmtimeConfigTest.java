package ai.tegmentum.webassembly4j.provider.wasmtime.config;

import ai.tegmentum.webassembly4j.api.config.EngineConfig;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class WasmtimeConfigTest {

    @Test
    void emptyBuilderHasNoValues() {
        WasmtimeConfig config = WasmtimeConfig.builder().build();
        assertFalse(config.consumeFuel().isPresent());
        assertFalse(config.epochInterruption().isPresent());
        assertFalse(config.craneliftOptLevel().isPresent());
        assertFalse(config.debugInfo().isPresent());
    }

    @Test
    void builderSetsValues() {
        WasmtimeConfig config = WasmtimeConfig.builder()
                .consumeFuel(true)
                .epochInterruption(true)
                .craneliftOptLevel(CraneliftOptLevel.SPEED)
                .debugInfo(false)
                .parallelCompilation(true)
                .wasmThreads(true)
                .wasmMultiMemory(false)
                .wasmComponentModel(true)
                .wasmGc(false)
                .build();

        assertEquals(Optional.of(true), config.consumeFuel());
        assertEquals(Optional.of(true), config.epochInterruption());
        assertEquals(Optional.of(CraneliftOptLevel.SPEED), config.craneliftOptLevel());
        assertEquals(Optional.of(false), config.debugInfo());
        assertEquals(Optional.of(true), config.parallelCompilation());
        assertEquals(Optional.of(true), config.wasmThreads());
        assertEquals(Optional.of(false), config.wasmMultiMemory());
        assertEquals(Optional.of(true), config.wasmComponentModel());
        assertEquals(Optional.of(false), config.wasmGc());
    }

    @Test
    void implementsEngineConfig() {
        WasmtimeConfig config = WasmtimeConfig.builder().build();
        assertInstanceOf(EngineConfig.class, config);
    }
}
