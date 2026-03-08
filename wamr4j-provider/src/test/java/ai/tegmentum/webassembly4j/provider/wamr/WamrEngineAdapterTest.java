package ai.tegmentum.webassembly4j.provider.wamr;

import ai.tegmentum.webassembly4j.api.Engine;
import ai.tegmentum.webassembly4j.api.EngineCapabilities;
import ai.tegmentum.webassembly4j.api.EngineInfo;
import ai.tegmentum.webassembly4j.api.config.WebAssemblyConfig;
import ai.tegmentum.webassembly4j.provider.wamr.config.WamrConfig;
import ai.tegmentum.webassembly4j.provider.wamr.config.WamrRunningMode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import static org.junit.jupiter.api.Assertions.*;

class WamrEngineAdapterTest {

    static boolean runtimeAvailable() {
        return new WamrProvider().availability().available();
    }

    @Test
    @EnabledIf("runtimeAvailable")
    void createWithNullConfig() {
        try (Engine engine = WamrEngineAdapter.create(null)) {
            assertNotNull(engine);
            assertNotNull(engine.info());
            assertNotNull(engine.capabilities());
        }
    }

    @Test
    @EnabledIf("runtimeAvailable")
    void createWithEmptyConfig() {
        WebAssemblyConfig config = WebAssemblyConfig.builder().build();
        try (Engine engine = WamrEngineAdapter.create(config)) {
            assertNotNull(engine);
        }
    }

    @Test
    @EnabledIf("runtimeAvailable")
    void engineInfoIsCorrect() {
        try (Engine engine = WamrEngineAdapter.create(null)) {
            EngineInfo info = engine.info();
            assertEquals("wamr", info.engineId());
            assertEquals("wamr", info.providerId());
            assertEquals(17, info.minimumJavaVersion());
            assertNotNull(info.engineVersion());
        }
    }

    @Test
    @EnabledIf("runtimeAvailable")
    void capabilitiesReflectWamr() {
        try (Engine engine = WamrEngineAdapter.create(null)) {
            EngineCapabilities caps = engine.capabilities();
            assertTrue(caps.supportsCoreModules());
            assertTrue(caps.supportsWasi());
            assertTrue(caps.supportsNativeInterop());
            assertFalse(caps.supportsComponents());
            assertFalse(caps.supportsFuel());
            assertFalse(caps.supportsEpochInterruption());
        }
    }

    @Test
    @EnabledIf("runtimeAvailable")
    void unwrapReturnsNativeRuntime() {
        try (Engine engine = WamrEngineAdapter.create(null)) {
            assertTrue(engine.unwrap(ai.tegmentum.wamr4j.WebAssemblyRuntime.class).isPresent());
            assertFalse(engine.unwrap(String.class).isPresent());
        }
    }

    @Test
    @EnabledIf("runtimeAvailable")
    void createWithRunningMode() {
        WamrConfig wamrConfig = WamrConfig.builder()
                .runningMode(WamrRunningMode.INTERP)
                .build();
        WebAssemblyConfig config = WebAssemblyConfig.builder()
                .engineConfig(wamrConfig)
                .build();

        try (Engine engine = WamrEngineAdapter.create(config)) {
            assertNotNull(engine);
        }
    }

    @Test
    void loadComponentThrowsUnsupported() {
        if (!runtimeAvailable()) return;
        try (Engine engine = WamrEngineAdapter.create(null)) {
            assertThrows(ai.tegmentum.webassembly4j.api.exception.UnsupportedFeatureException.class,
                    () -> engine.loadComponent(new byte[0]));
        }
    }
}
