package ai.tegmentum.webassembly4j.pool;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PoolConfigTest {

    @Test
    void defaultConfig() {
        PoolConfig config = PoolConfig.defaults();
        assertEquals(0, config.minSize());
        assertEquals(8, config.maxSize());
        assertEquals(300_000, config.maxIdleMillis());
        assertEquals(30_000, config.borrowTimeoutMillis());
    }

    @Test
    void customConfig() {
        PoolConfig config = PoolConfig.builder()
                .minSize(2)
                .maxSize(16)
                .maxIdleMillis(60_000)
                .borrowTimeoutMillis(5_000)
                .build();

        assertEquals(2, config.minSize());
        assertEquals(16, config.maxSize());
        assertEquals(60_000, config.maxIdleMillis());
        assertEquals(5_000, config.borrowTimeoutMillis());
    }

    @Test
    void negativeMinSizeThrows() {
        assertThrows(IllegalArgumentException.class, () ->
                PoolConfig.builder().minSize(-1).build());
    }

    @Test
    void zeroMaxSizeThrows() {
        assertThrows(IllegalArgumentException.class, () ->
                PoolConfig.builder().maxSize(0).build());
    }

    @Test
    void minExceedsMaxThrows() {
        assertThrows(IllegalArgumentException.class, () ->
                PoolConfig.builder().minSize(10).maxSize(5).build());
    }

    @Test
    void toStringContainsValues() {
        PoolConfig config = PoolConfig.builder().minSize(2).maxSize(4).build();
        String str = config.toString();
        assertTrue(str.contains("minSize=2"));
        assertTrue(str.contains("maxSize=4"));
    }
}
