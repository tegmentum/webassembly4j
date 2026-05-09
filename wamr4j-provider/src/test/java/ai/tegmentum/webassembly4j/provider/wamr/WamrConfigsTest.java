package ai.tegmentum.webassembly4j.provider.wamr;

import ai.tegmentum.webassembly4j.api.config.WebAssemblyConfig;
import ai.tegmentum.webassembly4j.provider.wamr.config.WamrConfig;
import ai.tegmentum.webassembly4j.provider.wamr.config.WamrRunningMode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class WamrConfigsTest {

    @Test
    void ofWiresEngineAndEngineConfig() {
        WebAssemblyConfig cfg = WamrConfigs.of(b -> b
                .runningMode(WamrRunningMode.INTERP)
                .defaultStackSize(64 * 1024));

        assertEquals("wamr", cfg.engineId().orElse(null));
        assertTrue(cfg.engineConfig().isPresent());
        WamrConfig wc = (WamrConfig) cfg.engineConfig().get();
        assertEquals(WamrRunningMode.INTERP, wc.runningMode().orElse(null));
        assertEquals(64 * 1024, wc.defaultStackSize().orElse(0));
    }

    @Test
    void builderHandsOffToCommonSettings() {
        WebAssemblyConfig cfg = WamrConfigs.builder(b -> b.runningMode(WamrRunningMode.INTERP))
                .debug(true)
                .build();

        assertEquals("wamr", cfg.engineId().orElse(null));
        assertTrue(cfg.commonConfig().debug().orElse(false));
        WamrConfig wc = (WamrConfig) cfg.engineConfig().get();
        assertEquals(WamrRunningMode.INTERP, wc.runningMode().orElse(null));
    }

    @Test
    void emptyConfiguratorStillProducesValidConfig() {
        WebAssemblyConfig cfg = WamrConfigs.of(b -> {});

        assertEquals("wamr", cfg.engineId().orElse(null));
        assertTrue(cfg.engineConfig().get() instanceof WamrConfig);
    }

    @Test
    void engineIdConstantMatchesProviderDescriptor() {
        assertEquals(new WamrProvider().descriptor().engineId(), WamrConfigs.ENGINE_ID);
    }
}
