package ai.tegmentum.webassembly4j.provider.wasmtime;

import ai.tegmentum.webassembly4j.api.Engine;
import ai.tegmentum.webassembly4j.api.EngineCapabilities;
import ai.tegmentum.webassembly4j.api.EngineInfo;
import ai.tegmentum.webassembly4j.api.config.OptimizationLevel;
import ai.tegmentum.webassembly4j.api.config.WebAssemblyConfig;
import ai.tegmentum.webassembly4j.api.exception.ConfigurationException;
import ai.tegmentum.webassembly4j.provider.wasmtime.config.CraneliftOptLevel;
import ai.tegmentum.webassembly4j.provider.wasmtime.config.WasmtimeConfig;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import static org.junit.jupiter.api.Assertions.*;

class WasmtimeEngineAdapterTest {

    static boolean runtimeAvailable() {
        return new WasmtimeProvider().availability().available();
    }

    @Test
    @EnabledIf("runtimeAvailable")
    void createWithNullConfig() {
        try (Engine engine = WasmtimeEngineAdapter.create(null)) {
            assertNotNull(engine);
            assertNotNull(engine.info());
            assertNotNull(engine.capabilities());
        }
    }

    @Test
    @EnabledIf("runtimeAvailable")
    void createWithEmptyConfig() {
        WebAssemblyConfig config = WebAssemblyConfig.builder().build();
        try (Engine engine = WasmtimeEngineAdapter.create(config)) {
            assertNotNull(engine);
        }
    }

    @Test
    @EnabledIf("runtimeAvailable")
    void engineInfoIsCorrect() {
        try (Engine engine = WasmtimeEngineAdapter.create(null)) {
            EngineInfo info = engine.info();
            assertEquals("wasmtime", info.engineId());
            assertEquals("wasmtime", info.providerId());
            assertEquals(11, info.minimumJavaVersion());
        }
    }

    @Test
    @EnabledIf("runtimeAvailable")
    void capabilitiesReflectConfig() {
        try (Engine engine = WasmtimeEngineAdapter.create(null)) {
            EngineCapabilities caps = engine.capabilities();
            assertTrue(caps.supportsCoreModules());
            assertTrue(caps.supportsWasi());
            assertTrue(caps.supportsNativeInterop());
        }
    }

    @Test
    @EnabledIf("runtimeAvailable")
    void fuelCapabilityReflectsConfig() {
        WasmtimeConfig wasmtimeConfig = WasmtimeConfig.builder()
                .consumeFuel(true)
                .build();
        WebAssemblyConfig config = WebAssemblyConfig.builder()
                .engineConfig(wasmtimeConfig)
                .build();

        try (Engine engine = WasmtimeEngineAdapter.create(config)) {
            assertTrue(engine.capabilities().supportsFuel());
        }
    }

    @Test
    void conflictingOptimizationThrows() {
        WasmtimeConfig wasmtimeConfig = WasmtimeConfig.builder()
                .craneliftOptLevel(CraneliftOptLevel.NONE)
                .build();
        WebAssemblyConfig config = WebAssemblyConfig.builder()
                .optimizationLevel(OptimizationLevel.SPEED)
                .engineConfig(wasmtimeConfig)
                .build();

        assertThrows(ConfigurationException.class,
                () -> WasmtimeEngineAdapter.create(config));
    }

    @Test
    @EnabledIf("runtimeAvailable")
    void compatibleOptimizationSucceeds() {
        WasmtimeConfig wasmtimeConfig = WasmtimeConfig.builder()
                .craneliftOptLevel(CraneliftOptLevel.SPEED)
                .build();
        WebAssemblyConfig config = WebAssemblyConfig.builder()
                .optimizationLevel(OptimizationLevel.SPEED)
                .engineConfig(wasmtimeConfig)
                .build();

        try (Engine engine = WasmtimeEngineAdapter.create(config)) {
            assertNotNull(engine);
        }
    }

    @Test
    @EnabledIf("runtimeAvailable")
    void unwrapReturnsNativeEngine() {
        try (Engine engine = WasmtimeEngineAdapter.create(null)) {
            assertTrue(engine.unwrap(ai.tegmentum.wasmtime4j.Engine.class).isPresent());
            assertFalse(engine.unwrap(String.class).isPresent());
        }
    }

    @Test
    @EnabledIf("runtimeAvailable")
    void epochExtensionAvailableWhenEnabled() {
        WasmtimeConfig wasmtimeConfig = WasmtimeConfig.builder()
                .epochInterruption(true)
                .build();
        WebAssemblyConfig config = WebAssemblyConfig.builder()
                .engineConfig(wasmtimeConfig)
                .build();

        try (Engine engine = WasmtimeEngineAdapter.create(config)) {
            assertTrue(engine.extension(
                    ai.tegmentum.webassembly4j.api.capability.EpochController.class).isPresent());
        }
    }
}
