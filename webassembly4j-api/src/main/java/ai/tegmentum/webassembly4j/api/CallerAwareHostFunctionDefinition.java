package ai.tegmentum.webassembly4j.api;

/**
 * Definition of a caller-aware host function to link into a module's
 * imports. Parallel to {@link HostFunctionDefinition} but wraps a
 * {@link CallerAwareHostFunction} whose implementation receives a
 * {@link Caller} handle for scoped access to the calling instance.
 *
 * <p>Providers that do not support caller-aware host functions ignore
 * entries of this type — see
 * {@link LinkingContext#callerAwareHostFunctions()}.
 *
 * @since 2.5.2
 */
public final class CallerAwareHostFunctionDefinition {

    private final String moduleName;
    private final String functionName;
    private final ValueType[] parameterTypes;
    private final ValueType[] resultTypes;
    private final CallerAwareHostFunction<?> function;

    public CallerAwareHostFunctionDefinition(String moduleName, String functionName,
                                             ValueType[] parameterTypes,
                                             ValueType[] resultTypes,
                                             CallerAwareHostFunction<?> function) {
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

    public CallerAwareHostFunction<?> function() {
        return function;
    }
}
