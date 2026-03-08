package ai.tegmentum.webassembly4j.benchmarks;

import ai.tegmentum.webassembly4j.api.config.EngineConfig;
import ai.tegmentum.webassembly4j.provider.wamr.config.WamrConfig;
import ai.tegmentum.webassembly4j.provider.wamr.config.WamrRunningMode;

import java.util.function.Supplier;

public enum EngineVariant {
    WASMTIME_JNI("wasmtime", "wasmtime4j.runtime", "jni", null),
    WASMTIME_PANAMA("wasmtime", "wasmtime4j.runtime", "panama", null),
    WAMR_JNI("wamr", "wamr4j.runtime", "jni",
            () -> WamrConfig.builder().runningMode(WamrRunningMode.INTERP).build()),
    WAMR_PANAMA("wamr", "wamr4j.runtime", "panama",
            () -> WamrConfig.builder().runningMode(WamrRunningMode.INTERP).build()),
    WAMR_LLVM_JIT_JNI("wamr", "wamr4j.runtime", "jni",
            () -> WamrConfig.builder().runningMode(WamrRunningMode.LLVM_JIT).build()),
    WAMR_LLVM_JIT_PANAMA("wamr", "wamr4j.runtime", "panama",
            () -> WamrConfig.builder().runningMode(WamrRunningMode.LLVM_JIT).build()),
    GRAALWASM("graalwasm", null, null, null),
    CHICORY("chicory", null, null, null);

    private final String engineId;
    private final String systemProperty;
    private final String propertyValue;
    private final Supplier<EngineConfig> engineConfigSupplier;

    EngineVariant(String engineId, String systemProperty, String propertyValue,
                  Supplier<EngineConfig> engineConfigSupplier) {
        this.engineId = engineId;
        this.systemProperty = systemProperty;
        this.propertyValue = propertyValue;
        this.engineConfigSupplier = engineConfigSupplier;
    }

    public String engineId() {
        return engineId;
    }

    public String systemProperty() {
        return systemProperty;
    }

    public String propertyValue() {
        return propertyValue;
    }

    public EngineConfig engineConfig() {
        return engineConfigSupplier != null ? engineConfigSupplier.get() : null;
    }
}
