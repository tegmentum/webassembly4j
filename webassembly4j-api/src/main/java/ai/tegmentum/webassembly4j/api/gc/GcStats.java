package ai.tegmentum.webassembly4j.api.gc;

/**
 * Statistics about garbage collection activity in the runtime.
 */
public final class GcStats {

    private final long totalAllocated;
    private final long totalCollected;
    private final long liveObjects;
    private final long totalCollections;
    private final long heapSizeBytes;

    private GcStats(Builder builder) {
        this.totalAllocated = builder.totalAllocated;
        this.totalCollected = builder.totalCollected;
        this.liveObjects = builder.liveObjects;
        this.totalCollections = builder.totalCollections;
        this.heapSizeBytes = builder.heapSizeBytes;
    }

    public long totalAllocated() { return totalAllocated; }
    public long totalCollected() { return totalCollected; }
    public long liveObjects() { return liveObjects; }
    public long totalCollections() { return totalCollections; }
    public long heapSizeBytes() { return heapSizeBytes; }

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
        public GcStats build() { return new GcStats(this); }
    }

    @Override
    public String toString() {
        return String.format("GcStats{allocated=%d, collected=%d, live=%d, collections=%d, heap=%d bytes}",
                totalAllocated, totalCollected, liveObjects, totalCollections, heapSizeBytes);
    }
}
