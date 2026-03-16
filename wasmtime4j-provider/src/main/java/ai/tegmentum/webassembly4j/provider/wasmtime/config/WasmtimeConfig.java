package ai.tegmentum.webassembly4j.provider.wasmtime.config;

import ai.tegmentum.webassembly4j.api.config.EngineConfig;

import java.util.Optional;

public final class WasmtimeConfig implements EngineConfig {

    private final Boolean consumeFuel;
    private final Boolean epochInterruption;
    private final CraneliftOptLevel craneliftOptLevel;
    private final Boolean debugInfo;
    private final Boolean parallelCompilation;
    private final Boolean wasmThreads;
    private final Boolean wasmMultiMemory;
    private final Boolean wasmComponentModel;
    private final Boolean wasmGc;
    private final Boolean wasmExceptions;
    private final Boolean wasmFunctionReferences;

    private WasmtimeConfig(Builder builder) {
        this.consumeFuel = builder.consumeFuel;
        this.epochInterruption = builder.epochInterruption;
        this.craneliftOptLevel = builder.craneliftOptLevel;
        this.debugInfo = builder.debugInfo;
        this.parallelCompilation = builder.parallelCompilation;
        this.wasmThreads = builder.wasmThreads;
        this.wasmMultiMemory = builder.wasmMultiMemory;
        this.wasmComponentModel = builder.wasmComponentModel;
        this.wasmGc = builder.wasmGc;
        this.wasmExceptions = builder.wasmExceptions;
        this.wasmFunctionReferences = builder.wasmFunctionReferences;
    }

    public Optional<Boolean> consumeFuel() { return Optional.ofNullable(consumeFuel); }
    public Optional<Boolean> epochInterruption() { return Optional.ofNullable(epochInterruption); }
    public Optional<CraneliftOptLevel> craneliftOptLevel() { return Optional.ofNullable(craneliftOptLevel); }
    public Optional<Boolean> debugInfo() { return Optional.ofNullable(debugInfo); }
    public Optional<Boolean> parallelCompilation() { return Optional.ofNullable(parallelCompilation); }
    public Optional<Boolean> wasmThreads() { return Optional.ofNullable(wasmThreads); }
    public Optional<Boolean> wasmMultiMemory() { return Optional.ofNullable(wasmMultiMemory); }
    public Optional<Boolean> wasmComponentModel() { return Optional.ofNullable(wasmComponentModel); }
    public Optional<Boolean> wasmGc() { return Optional.ofNullable(wasmGc); }
    public Optional<Boolean> wasmExceptions() { return Optional.ofNullable(wasmExceptions); }
    public Optional<Boolean> wasmFunctionReferences() { return Optional.ofNullable(wasmFunctionReferences); }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private Boolean consumeFuel;
        private Boolean epochInterruption;
        private CraneliftOptLevel craneliftOptLevel;
        private Boolean debugInfo;
        private Boolean parallelCompilation;
        private Boolean wasmThreads;
        private Boolean wasmMultiMemory;
        private Boolean wasmComponentModel;
        private Boolean wasmGc;
        private Boolean wasmExceptions;
        private Boolean wasmFunctionReferences;

        private Builder() {}

        public Builder consumeFuel(boolean consumeFuel) { this.consumeFuel = consumeFuel; return this; }
        public Builder epochInterruption(boolean epochInterruption) { this.epochInterruption = epochInterruption; return this; }
        public Builder craneliftOptLevel(CraneliftOptLevel level) { this.craneliftOptLevel = level; return this; }
        public Builder debugInfo(boolean debugInfo) { this.debugInfo = debugInfo; return this; }
        public Builder parallelCompilation(boolean parallelCompilation) { this.parallelCompilation = parallelCompilation; return this; }
        public Builder wasmThreads(boolean wasmThreads) { this.wasmThreads = wasmThreads; return this; }
        public Builder wasmMultiMemory(boolean wasmMultiMemory) { this.wasmMultiMemory = wasmMultiMemory; return this; }
        public Builder wasmComponentModel(boolean wasmComponentModel) { this.wasmComponentModel = wasmComponentModel; return this; }
        public Builder wasmGc(boolean wasmGc) { this.wasmGc = wasmGc; return this; }
        public Builder wasmExceptions(boolean wasmExceptions) { this.wasmExceptions = wasmExceptions; return this; }
        public Builder wasmFunctionReferences(boolean wasmFunctionReferences) { this.wasmFunctionReferences = wasmFunctionReferences; return this; }

        public WasmtimeConfig build() {
            return new WasmtimeConfig(this);
        }
    }
}
