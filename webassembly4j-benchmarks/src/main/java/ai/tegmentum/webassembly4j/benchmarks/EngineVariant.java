package ai.tegmentum.webassembly4j.benchmarks;

public enum EngineVariant {
    WASMTIME_JNI("wasmtime", "wasmtime4j.runtime", "jni"),
    WASMTIME_PANAMA("wasmtime", "wasmtime4j.runtime", "panama"),
    WAMR_JNI("wamr", "wamr4j.runtime", "jni"),
    WAMR_PANAMA("wamr", "wamr4j.runtime", "panama"),
    GRAALWASM("graalwasm", null, null),
    CHICORY("chicory", null, null);

    private final String engineId;
    private final String systemProperty;
    private final String propertyValue;

    EngineVariant(String engineId, String systemProperty, String propertyValue) {
        this.engineId = engineId;
        this.systemProperty = systemProperty;
        this.propertyValue = propertyValue;
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
}
