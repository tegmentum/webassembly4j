package ai.tegmentum.webassembly4j.provider.endive;

import ai.tegmentum.webassembly4j.api.config.WebAssemblyConfig;
import ai.tegmentum.webassembly4j.provider.endive.config.EndiveConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EndiveConfigsTest {

    @Test
    void ofWiresEngineAndEngineConfig() {
        WebAssemblyConfig cfg = EndiveConfigs.of(b -> b
                .executionMode(EndiveConfig.ExecutionMode.COMPILE));

        assertEquals("endive", cfg.engineId().orElse(null));
        assertTrue(cfg.engineConfig().isPresent());
        EndiveConfig cc = (EndiveConfig) cfg.engineConfig().get();
        assertEquals(EndiveConfig.ExecutionMode.COMPILE, cc.executionMode());
    }

    @Test
    void builderHandsOffToCommonSettings() {
        WebAssemblyConfig cfg = EndiveConfigs.builder(b -> {})
                .debug(true)
                .build();

        assertEquals("endive", cfg.engineId().orElse(null));
        assertTrue(cfg.commonConfig().debug().orElse(false));
        EndiveConfig cc = (EndiveConfig) cfg.engineConfig().get();
        assertEquals(EndiveConfig.ExecutionMode.INTERPRET, cc.executionMode());
    }

    @Test
    void engineIdConstantMatchesProviderDescriptor() {
        assertEquals(new EndiveProvider().descriptor().engineId(), EndiveConfigs.ENGINE_ID);
    }
}
