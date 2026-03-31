package ai.tegmentum.webassembly4j.provider.chicory.config;

import ai.tegmentum.webassembly4j.api.config.EngineConfig;

import java.util.Optional;

/**
 * Configuration for the Chicory WebAssembly engine.
 *
 * <p>Controls the execution mode used when instantiating modules:
 * <ul>
 *   <li>{@link ExecutionMode#INTERPRET} (default) — pure interpretation, no extra dependencies</li>
 *   <li>{@link ExecutionMode#COMPILE} — runtime AOT compilation to JVM bytecode via ASM,
 *       requires {@code com.dylibso.chicory:compiler} on the classpath</li>
 * </ul>
 */
public final class ChicoryConfig implements EngineConfig {

    /**
     * Execution mode for WebAssembly modules.
     */
    public enum ExecutionMode {
        /** Pure interpreter — no extra dependencies required. */
        INTERPRET,
        /** Runtime compilation to JVM bytecode — requires chicory-compiler dependency. */
        COMPILE
    }

    private final ExecutionMode executionMode;

    private ChicoryConfig(Builder builder) {
        this.executionMode = builder.executionMode;
    }

    public ExecutionMode executionMode() {
        return executionMode;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private ExecutionMode executionMode = ExecutionMode.INTERPRET;

        private Builder() {}

        public Builder executionMode(ExecutionMode executionMode) {
            this.executionMode = executionMode;
            return this;
        }

        public ChicoryConfig build() {
            return new ChicoryConfig(this);
        }
    }
}
