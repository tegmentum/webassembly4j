package ai.tegmentum.webassembly4j.provider.graalwasm;

import ai.tegmentum.webassembly4j.api.config.WebAssemblyConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GraalWasmConfigsTest {

    @Test
    void ofWiresEngineId() {
        WebAssemblyConfig cfg = GraalWasmConfigs.of();
        assertEquals("graalwasm", cfg.engineId().orElse(null));
        assertFalse(cfg.engineConfig().isPresent());
    }

    @Test
    void builderHandsOffToCommonSettings() {
        WebAssemblyConfig cfg = GraalWasmConfigs.builder()
                .debug(true)
                .build();

        assertEquals("graalwasm", cfg.engineId().orElse(null));
        assertTrue(cfg.commonConfig().debug().orElse(false));
    }

    @Test
    void engineIdConstantMatchesProviderDescriptor() {
        assertEquals(new GraalWasmProvider().descriptor().engineId(), GraalWasmConfigs.ENGINE_ID);
    }
}
