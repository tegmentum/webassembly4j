package ai.tegmentum.webassembly4j.pool;

/**
 * Configuration for a {@link WasmInstancePool}.
 */
public final class PoolConfig {

    private final int minSize;
    private final int maxSize;
    private final long maxIdleMillis;
    private final long borrowTimeoutMillis;

    private PoolConfig(Builder builder) {
        if (builder.minSize < 0) {
            throw new IllegalArgumentException("minSize must be >= 0");
        }
        if (builder.maxSize < 1) {
            throw new IllegalArgumentException("maxSize must be >= 1");
        }
        if (builder.minSize > builder.maxSize) {
            throw new IllegalArgumentException("minSize must be <= maxSize");
        }
        this.minSize = builder.minSize;
        this.maxSize = builder.maxSize;
        this.maxIdleMillis = builder.maxIdleMillis;
        this.borrowTimeoutMillis = builder.borrowTimeoutMillis;
    }

    public int minSize() {
        return minSize;
    }

    public int maxSize() {
        return maxSize;
    }

    public long maxIdleMillis() {
        return maxIdleMillis;
    }

    public long borrowTimeoutMillis() {
        return borrowTimeoutMillis;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static PoolConfig defaults() {
        return builder().build();
    }

    public static final class Builder {
        private int minSize = 0;
        private int maxSize = 8;
        private long maxIdleMillis = 300_000; // 5 minutes
        private long borrowTimeoutMillis = 30_000; // 30 seconds

        private Builder() {}

        public Builder minSize(int minSize) {
            this.minSize = minSize;
            return this;
        }

        public Builder maxSize(int maxSize) {
            this.maxSize = maxSize;
            return this;
        }

        public Builder maxIdleMillis(long maxIdleMillis) {
            this.maxIdleMillis = maxIdleMillis;
            return this;
        }

        public Builder borrowTimeoutMillis(long borrowTimeoutMillis) {
            this.borrowTimeoutMillis = borrowTimeoutMillis;
            return this;
        }

        public PoolConfig build() {
            return new PoolConfig(this);
        }
    }

    @Override
    public String toString() {
        return "PoolConfig{minSize=" + minSize
                + ", maxSize=" + maxSize
                + ", maxIdleMillis=" + maxIdleMillis
                + ", borrowTimeoutMillis=" + borrowTimeoutMillis
                + '}';
    }
}
