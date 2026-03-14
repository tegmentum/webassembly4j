package ai.tegmentum.webassembly4j.api.config;

import java.time.Duration;

/**
 * Configuration for WASI HTTP support, controlling connection and request limits.
 */
public final class WasiHttpConfig {

    private final Duration connectTimeout;
    private final Duration readTimeout;
    private final Integer maxConnections;
    private final Long maxRequestBodySize;
    private final Long maxResponseBodySize;

    private WasiHttpConfig(Builder builder) {
        this.connectTimeout = builder.connectTimeout;
        this.readTimeout = builder.readTimeout;
        this.maxConnections = builder.maxConnections;
        this.maxRequestBodySize = builder.maxRequestBodySize;
        this.maxResponseBodySize = builder.maxResponseBodySize;
    }

    /**
     * Returns a config with all default values (no limits set).
     */
    public static WasiHttpConfig defaults() {
        return new Builder().build();
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Returns the connect timeout, or null if not set.
     */
    public Duration connectTimeout() {
        return connectTimeout;
    }

    /**
     * Returns the read timeout, or null if not set.
     */
    public Duration readTimeout() {
        return readTimeout;
    }

    /**
     * Returns the max connections, or null if not set.
     */
    public Integer maxConnections() {
        return maxConnections;
    }

    /**
     * Returns the max request body size in bytes, or null if not set.
     */
    public Long maxRequestBodySize() {
        return maxRequestBodySize;
    }

    /**
     * Returns the max response body size in bytes, or null if not set.
     */
    public Long maxResponseBodySize() {
        return maxResponseBodySize;
    }

    public static final class Builder {

        private Duration connectTimeout;
        private Duration readTimeout;
        private Integer maxConnections;
        private Long maxRequestBodySize;
        private Long maxResponseBodySize;

        private Builder() {}

        public Builder connectTimeout(Duration connectTimeout) {
            this.connectTimeout = connectTimeout;
            return this;
        }

        public Builder readTimeout(Duration readTimeout) {
            this.readTimeout = readTimeout;
            return this;
        }

        public Builder maxConnections(int maxConnections) {
            this.maxConnections = maxConnections;
            return this;
        }

        public Builder maxRequestBodySize(long maxRequestBodySize) {
            this.maxRequestBodySize = maxRequestBodySize;
            return this;
        }

        public Builder maxResponseBodySize(long maxResponseBodySize) {
            this.maxResponseBodySize = maxResponseBodySize;
            return this;
        }

        public WasiHttpConfig build() {
            return new WasiHttpConfig(this);
        }
    }
}
