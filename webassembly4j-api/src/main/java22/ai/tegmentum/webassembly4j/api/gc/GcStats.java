package ai.tegmentum.webassembly4j.api.gc;

/**
 * Statistics about garbage collection activity in the runtime.
 */
public record GcStats(long totalAllocated, long totalCollected, long liveObjects,
                      long totalCollections, long heapSizeBytes) {

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private long totalAllocated;
        private long totalCollected;
        private long liveObjects;
        private long totalCollections;
        private long heapSizeBytes;

        public Builder totalAllocated(long v) { totalAllocated = v; return this; }
        public Builder totalCollected(long v) { totalCollected = v; return this; }
        public Builder liveObjects(long v) { liveObjects = v; return this; }
        public Builder totalCollections(long v) { totalCollections = v; return this; }
        public Builder heapSizeBytes(long v) { heapSizeBytes = v; return this; }
        public GcStats build() { return new GcStats(totalAllocated, totalCollected, liveObjects, totalCollections, heapSizeBytes); }
    }

    @Override
    public String toString() {
        return String.format("GcStats{allocated=%d, collected=%d, live=%d, collections=%d, heap=%d bytes}",
                totalAllocated, totalCollected, liveObjects, totalCollections, heapSizeBytes);
    }
}
