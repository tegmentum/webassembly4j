package ai.tegmentum.webassembly4j.provider.wamr.config;

import ai.tegmentum.webassembly4j.api.config.EngineConfig;

import java.util.Optional;

public final class WamrConfig implements EngineConfig {

    private final WamrRunningMode runningMode;
    private final Integer defaultStackSize;
    private final Integer hostManagedHeapSize;
    private final Integer maxMemoryPages;

    private WamrConfig(Builder builder) {
        this.runningMode = builder.runningMode;
        this.defaultStackSize = builder.defaultStackSize;
        this.hostManagedHeapSize = builder.hostManagedHeapSize;
        this.maxMemoryPages = builder.maxMemoryPages;
    }

    public Optional<WamrRunningMode> runningMode() { return Optional.ofNullable(runningMode); }
    public Optional<Integer> defaultStackSize() { return Optional.ofNullable(defaultStackSize); }
    public Optional<Integer> hostManagedHeapSize() { return Optional.ofNullable(hostManagedHeapSize); }
    public Optional<Integer> maxMemoryPages() { return Optional.ofNullable(maxMemoryPages); }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private WamrRunningMode runningMode;
        private Integer defaultStackSize;
        private Integer hostManagedHeapSize;
        private Integer maxMemoryPages;

        private Builder() {}

        public Builder runningMode(WamrRunningMode runningMode) { this.runningMode = runningMode; return this; }
        public Builder defaultStackSize(int defaultStackSize) { this.defaultStackSize = defaultStackSize; return this; }
        public Builder hostManagedHeapSize(int hostManagedHeapSize) { this.hostManagedHeapSize = hostManagedHeapSize; return this; }
        public Builder maxMemoryPages(int maxMemoryPages) { this.maxMemoryPages = maxMemoryPages; return this; }

        public WamrConfig build() {
            return new WamrConfig(this);
        }
    }
}
