package ai.tegmentum.webassembly4j.api;

public final class HostFunctionDefinition {

    private final String moduleName;
    private final String functionName;
    private final ValueType[] parameterTypes;
    private final ValueType[] resultTypes;
    private final HostFunction function;

    public HostFunctionDefinition(String moduleName, String functionName,
                                   ValueType[] parameterTypes, ValueType[] resultTypes,
                                   HostFunction function) {
        this.moduleName = moduleName;
        this.functionName = functionName;
        this.parameterTypes = parameterTypes.clone();
        this.resultTypes = resultTypes.clone();
        this.function = function;
    }

    public String moduleName() {
        return moduleName;
    }

    public String functionName() {
        return functionName;
    }

    public ValueType[] parameterTypes() {
        return parameterTypes.clone();
    }

    public ValueType[] resultTypes() {
        return resultTypes.clone();
    }

    public HostFunction function() {
        return function;
    }
}
