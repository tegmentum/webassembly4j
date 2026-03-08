package ai.tegmentum.webassembly4j.api.config;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class WebAssemblyConfigTest {

    @Test
    void emptyConfigHasDefaults() {
        WebAssemblyConfig config = WebAssemblyConfig.builder().build();

        assertFalse(config.engineId().isPresent());
        assertFalse(config.engineConfig().isPresent());
        assertTrue(config.extraOptions().isEmpty());

        CommonConfig common = config.commonConfig();
        assertNotNull(common);
        assertFalse(common.wasi().isPresent());
        assertFalse(common.resourceLimits().isPresent());
        assertFalse(common.optimizationLevel().isPresent());
        assertFalse(common.timeout().isPresent());
        assertFalse(common.debug().isPresent());
        assertFalse(common.fuelLimit().isPresent());
    }

    @Test
    void engineIdIsSet() {
        WebAssemblyConfig config = WebAssemblyConfig.builder()
                .engine("wasmtime")
                .build();

        assertEquals(Optional.of("wasmtime"), config.engineId());
    }

    @Test
    void commonConfigValues() {
        WebAssemblyConfig config = WebAssemblyConfig.builder()
                .optimizationLevel(OptimizationLevel.SPEED)
                .debug(true)
                .fuelLimit(50000)
                .timeoutMillis(5000)
                .build();

        CommonConfig common = config.commonConfig();
        assertEquals(Optional.of(OptimizationLevel.SPEED), common.optimizationLevel());
        assertEquals(Optional.of(true), common.debug());
        assertEquals(Optional.of(50000L), common.fuelLimit());
        assertEquals(Optional.of(Duration.ofMillis(5000)), common.timeout());
    }

    @Test
    void extraOptionsArePreserved() {
        WebAssemblyConfig config = WebAssemblyConfig.builder()
                .option("wasmtime.consumeFuel", true)
                .option("wasmtime.someFlag", "value")
                .build();

        assertEquals(2, config.extraOptions().size());
        assertEquals(true, config.extraOptions().get("wasmtime.consumeFuel"));
        assertEquals("value", config.extraOptions().get("wasmtime.someFlag"));
    }

    @Test
    void extraOptionsAreUnmodifiable() {
        WebAssemblyConfig config = WebAssemblyConfig.builder()
                .option("key", "value")
                .build();

        assertThrows(UnsupportedOperationException.class,
                () -> config.extraOptions().put("new", "entry"));
    }

    @Test
    void engineConfigMarkerIsSet() {
        EngineConfig mockConfig = new EngineConfig() {};
        WebAssemblyConfig config = WebAssemblyConfig.builder()
                .engineConfig(mockConfig)
                .build();

        assertTrue(config.engineConfig().isPresent());
        assertSame(mockConfig, config.engineConfig().get());
    }
}
