package ai.tegmentum.webassembly4j.api.config;

import java.util.OptionalLong;

/**
 * Configuration for component instantiation, controlling resource limits
 * such as fuel, memory, and table elements.
 */
public final class ComponentConfig {

    private final OptionalLong fuelLimit;
    private final OptionalLong epochDeadline;
    private final OptionalLong maxMemoryBytes;
    private final OptionalLong maxTableElements;
    private final OptionalLong maxInstances;
    private final OptionalLong maxTables;
    private final OptionalLong maxMemories;
    private final boolean trapOnGrowFailure;

    private ComponentConfig(Builder builder) {
        this.fuelLimit = builder.fuelLimit;
        this.epochDeadline = builder.epochDeadline;
        this.maxMemoryBytes = builder.maxMemoryBytes;
        this.maxTableElements = builder.maxTableElements;
        this.maxInstances = builder.maxInstances;
        this.maxTables = builder.maxTables;
        this.maxMemories = builder.maxMemories;
        this.trapOnGrowFailure = builder.trapOnGrowFailure;
    }

    public static Builder builder() {
        return new Builder();
    }

    public OptionalLong fuelLimit() {
        return fuelLimit;
    }

    public OptionalLong epochDeadline() {
        return epochDeadline;
    }

    public OptionalLong maxMemoryBytes() {
        return maxMemoryBytes;
    }

    public OptionalLong maxTableElements() {
        return maxTableElements;
    }

    public OptionalLong maxInstances() {
        return maxInstances;
    }

    public OptionalLong maxTables() {
        return maxTables;
    }

    public OptionalLong maxMemories() {
        return maxMemories;
    }

    public boolean trapOnGrowFailure() {
        return trapOnGrowFailure;
    }

    public static final class Builder {

        private OptionalLong fuelLimit = OptionalLong.empty();
        private OptionalLong epochDeadline = OptionalLong.empty();
        private OptionalLong maxMemoryBytes = OptionalLong.empty();
        private OptionalLong maxTableElements = OptionalLong.empty();
        private OptionalLong maxInstances = OptionalLong.empty();
        private OptionalLong maxTables = OptionalLong.empty();
        private OptionalLong maxMemories = OptionalLong.empty();
        private boolean trapOnGrowFailure = false;

        private Builder() {}

        public Builder fuelLimit(long fuelLimit) {
            this.fuelLimit = OptionalLong.of(fuelLimit);
            return this;
        }

        public Builder epochDeadline(long epochDeadline) {
            this.epochDeadline = OptionalLong.of(epochDeadline);
            return this;
        }

        public Builder maxMemoryBytes(long maxMemoryBytes) {
            this.maxMemoryBytes = OptionalLong.of(maxMemoryBytes);
            return this;
        }

        public Builder maxTableElements(long maxTableElements) {
            this.maxTableElements = OptionalLong.of(maxTableElements);
            return this;
        }

        public Builder maxInstances(long maxInstances) {
            this.maxInstances = OptionalLong.of(maxInstances);
            return this;
        }

        public Builder maxTables(long maxTables) {
            this.maxTables = OptionalLong.of(maxTables);
            return this;
        }

        public Builder maxMemories(long maxMemories) {
            this.maxMemories = OptionalLong.of(maxMemories);
            return this;
        }

        public Builder trapOnGrowFailure(boolean trapOnGrowFailure) {
            this.trapOnGrowFailure = trapOnGrowFailure;
            return this;
        }

        public ComponentConfig build() {
            return new ComponentConfig(this);
        }
    }
}
