package ai.tegmentum.webassembly4j.provider.wamr.config;

import ai.tegmentum.webassembly4j.api.config.EngineConfig;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class WamrConfigTest {

    @Test
    void emptyBuilderHasNoValues() {
        WamrConfig config = WamrConfig.builder().build();
        assertFalse(config.runningMode().isPresent());
        assertFalse(config.defaultStackSize().isPresent());
        assertFalse(config.hostManagedHeapSize().isPresent());
        assertFalse(config.maxMemoryPages().isPresent());
    }

    @Test
    void builderSetsValues() {
        WamrConfig config = WamrConfig.builder()
                .runningMode(WamrRunningMode.INTERP)
                .defaultStackSize(131072)
                .hostManagedHeapSize(65536)
                .maxMemoryPages(256)
                .build();

        assertEquals(Optional.of(WamrRunningMode.INTERP), config.runningMode());
        assertEquals(Optional.of(131072), config.defaultStackSize());
        assertEquals(Optional.of(65536), config.hostManagedHeapSize());
        assertEquals(Optional.of(256), config.maxMemoryPages());
    }

    @Test
    void implementsEngineConfig() {
        WamrConfig config = WamrConfig.builder().build();
        assertInstanceOf(EngineConfig.class, config);
    }
}
