package ai.tegmentum.webassembly4j.api.config;

import java.util.OptionalLong;

public interface ResourceLimits {

    OptionalLong maxMemoryBytes();

    OptionalLong maxTableElements();

    OptionalLong maxInstances();

    OptionalLong maxExecutionTimeMillis();

    /**
     * Returns the maximum number of WebAssembly tables allowed.
     */
    default OptionalLong maxTables() {
        return OptionalLong.empty();
    }

    /**
     * Returns the maximum number of WebAssembly linear memories allowed.
     */
    default OptionalLong maxMemories() {
        return OptionalLong.empty();
    }

    /**
     * Returns whether to trap instead of returning -1 when a memory or table
     * grow operation fails due to resource limits.
     */
    default boolean trapOnGrowFailure() {
        return false;
    }
}
