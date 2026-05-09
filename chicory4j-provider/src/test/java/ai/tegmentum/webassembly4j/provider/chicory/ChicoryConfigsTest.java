package ai.tegmentum.webassembly4j.provider.chicory;

import ai.tegmentum.webassembly4j.api.config.WebAssemblyConfig;
import ai.tegmentum.webassembly4j.provider.chicory.config.ChicoryConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ChicoryConfigsTest {

    @Test
    void ofWiresEngineAndEngineConfig() {
        WebAssemblyConfig cfg = ChicoryConfigs.of(b -> b
                .executionMode(ChicoryConfig.ExecutionMode.COMPILE));

        assertEquals("chicory", cfg.engineId().orElse(null));
        assertTrue(cfg.engineConfig().isPresent());
        ChicoryConfig cc = (ChicoryConfig) cfg.engineConfig().get();
        assertEquals(ChicoryConfig.ExecutionMode.COMPILE, cc.executionMode());
    }

    @Test
    void builderHandsOffToCommonSettings() {
        WebAssemblyConfig cfg = ChicoryConfigs.builder(b -> {})
                .debug(true)
                .build();

        assertEquals("chicory", cfg.engineId().orElse(null));
        assertTrue(cfg.commonConfig().debug().orElse(false));
        ChicoryConfig cc = (ChicoryConfig) cfg.engineConfig().get();
        assertEquals(ChicoryConfig.ExecutionMode.INTERPRET, cc.executionMode());
    }

    @Test
    void engineIdConstantMatchesProviderDescriptor() {
        assertEquals(new ChicoryProvider().descriptor().engineId(), ChicoryConfigs.ENGINE_ID);
    }
}
