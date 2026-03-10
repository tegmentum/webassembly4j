package ai.tegmentum.webassembly4j.benchmarks;

import ai.tegmentum.webassembly4j.api.config.EngineConfig;
import ai.tegmentum.webassembly4j.provider.wamr.config.WamrConfig;
import ai.tegmentum.webassembly4j.provider.wamr.config.WamrRunningMode;

import java.util.function.Supplier;

/**
 * Engine variants for benchmarking. Each variant is tagged with a {@link JdkAffinity}
 * indicating which JDK(s) it should run on:
 * <ul>
 *   <li>{@code STANDARD} - native runtimes (Wasmtime, WAMR) that don't benefit from GraalVM JVMCI</li>
 *   <li>{@code GRAALVM} - GraalWasm, which requires GraalVM CE/EE for JIT compilation</li>
 *   <li>{@code ANY} - pure-Java runtimes (Chicory) that benefit from comparison on both JDKs</li>
 * </ul>
 */
public enum EngineVariant {
    WASMTIME_JNI("wasmtime", "wasmtime4j.runtime", "jni", null, JdkAffinity.STANDARD),
    WASMTIME_PANAMA("wasmtime", "wasmtime4j.runtime", "panama", null, JdkAffinity.STANDARD),
    WAMR_JNI("wamr", "wamr4j.runtime", "jni",
            () -> WamrConfig.builder().runningMode(WamrRunningMode.INTERP).build(), JdkAffinity.STANDARD),
    WAMR_PANAMA("wamr", "wamr4j.runtime", "panama",
            () -> WamrConfig.builder().runningMode(WamrRunningMode.INTERP).build(), JdkAffinity.STANDARD),
    WAMR_LLVM_JIT_JNI("wamr", "wamr4j.runtime", "jni",
            () -> WamrConfig.builder().runningMode(WamrRunningMode.LLVM_JIT).build(), JdkAffinity.STANDARD),
    WAMR_LLVM_JIT_PANAMA("wamr", "wamr4j.runtime", "panama",
            () -> WamrConfig.builder().runningMode(WamrRunningMode.LLVM_JIT).build(), JdkAffinity.STANDARD),
    GRAALWASM("graalwasm", null, null, null, JdkAffinity.GRAALVM),
    CHICORY("chicory", null, null, null, JdkAffinity.ANY);

    public enum JdkAffinity {
        /** Native runtimes - run on standard JDK only. */
        STANDARD,
        /** Truffle-based runtimes - require GraalVM CE/EE for JIT. */
        GRAALVM,
        /** Pure-Java runtimes - run on both for comparison. */
        ANY
    }

    private final String engineId;
    private final String systemProperty;
    private final String propertyValue;
    private final Supplier<EngineConfig> engineConfigSupplier;
    private final JdkAffinity jdkAffinity;

    EngineVariant(String engineId, String systemProperty, String propertyValue,
                  Supplier<EngineConfig> engineConfigSupplier, JdkAffinity jdkAffinity) {
        this.engineId = engineId;
        this.systemProperty = systemProperty;
        this.propertyValue = propertyValue;
        this.engineConfigSupplier = engineConfigSupplier;
        this.jdkAffinity = jdkAffinity;
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

    public JdkAffinity jdkAffinity() {
        return jdkAffinity;
    }

    /**
     * Returns true if the current JVM is GraalVM with JVMCI support.
     * Detects via vendor name ("GraalVM") or JVMCI in the VM version string.
     */
    public static boolean isRunningOnGraalVM() {
        String vendor = System.getProperty("java.vendor", "");
        String vmVersion = System.getProperty("java.vm.version", "");
        return vendor.contains("GraalVM") || vmVersion.contains("jvmci");
    }

    /**
     * Returns true if this variant should run on the current JDK based on its affinity.
     */
    public boolean isApplicableToCurrentJdk() {
        if (jdkAffinity == JdkAffinity.ANY) {
            return true;
        }
        boolean onGraal = isRunningOnGraalVM();
        return (jdkAffinity == JdkAffinity.GRAALVM) == onGraal;
    }
}
