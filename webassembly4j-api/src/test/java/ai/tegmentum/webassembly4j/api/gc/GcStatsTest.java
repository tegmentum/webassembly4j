package ai.tegmentum.webassembly4j.api.gc;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GcStatsTest {

    @Test
    void builderRoundTrip() {
        GcStats stats = GcStats.builder()
                .totalAllocated(100)
                .totalCollected(50)
                .liveObjects(50)
                .totalCollections(3)
                .heapSizeBytes(4096)
                .build();

        assertEquals(100, stats.totalAllocated());
        assertEquals(50, stats.totalCollected());
        assertEquals(50, stats.liveObjects());
        assertEquals(3, stats.totalCollections());
        assertEquals(4096, stats.heapSizeBytes());
    }

    @Test
    void defaultsToZero() {
        GcStats stats = GcStats.builder().build();
        assertEquals(0, stats.totalAllocated());
        assertEquals(0, stats.totalCollected());
        assertEquals(0, stats.liveObjects());
        assertEquals(0, stats.totalCollections());
        assertEquals(0, stats.heapSizeBytes());
    }
}
