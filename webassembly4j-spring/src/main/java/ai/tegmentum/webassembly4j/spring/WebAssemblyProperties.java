package ai.tegmentum.webassembly4j.spring;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for WebAssembly4J.
 *
 * <pre>
 * webassembly4j.engine=wasmtime
 * webassembly4j.provider=wasmtime4j
 * webassembly4j.pool.enabled=true
 * webassembly4j.pool.min-size=2
 * webassembly4j.pool.max-size=16
 * webassembly4j.pool.max-idle-millis=300000
 * webassembly4j.pool.borrow-timeout-millis=30000
 * </pre>
 */
@ConfigurationProperties(prefix = "webassembly4j")
public class WebAssemblyProperties {

    private String engine;
    private String provider;
    private boolean enabled = true;
    private Pool pool = new Pool();

    public String getEngine() {
        return engine;
    }

    public void setEngine(String engine) {
        this.engine = engine;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Pool getPool() {
        return pool;
    }

    public void setPool(Pool pool) {
        this.pool = pool;
    }

    public static class Pool {
        private boolean enabled = false;
        private int minSize = 0;
        private int maxSize = 8;
        private long maxIdleMillis = 300_000;
        private long borrowTimeoutMillis = 30_000;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getMinSize() {
            return minSize;
        }

        public void setMinSize(int minSize) {
            this.minSize = minSize;
        }

        public int getMaxSize() {
            return maxSize;
        }

        public void setMaxSize(int maxSize) {
            this.maxSize = maxSize;
        }

        public long getMaxIdleMillis() {
            return maxIdleMillis;
        }

        public void setMaxIdleMillis(long maxIdleMillis) {
            this.maxIdleMillis = maxIdleMillis;
        }

        public long getBorrowTimeoutMillis() {
            return borrowTimeoutMillis;
        }

        public void setBorrowTimeoutMillis(long borrowTimeoutMillis) {
            this.borrowTimeoutMillis = borrowTimeoutMillis;
        }
    }
}
