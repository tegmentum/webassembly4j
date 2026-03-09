package ai.tegmentum.webassembly4j.runtime;

import ai.tegmentum.webassembly4j.api.Function;
import ai.tegmentum.webassembly4j.api.Instance;
import ai.tegmentum.webassembly4j.runtime.proxy.ProxyFactory;

public final class WasmInstance implements AutoCloseable {

    private final Instance instance;
    private final ai.tegmentum.webassembly4j.api.Module module;
    private final ai.tegmentum.webassembly4j.api.Engine engine;

    WasmInstance(Instance instance, ai.tegmentum.webassembly4j.api.Module module,
                 ai.tegmentum.webassembly4j.api.Engine engine) {
        this.instance = instance;
        this.module = module;
        this.engine = engine;
    }

    @SuppressWarnings("unchecked")
    public <R> R call(String functionName, Class<R> returnType, Object... args) {
        Function fn = instance.function(functionName)
                .orElseThrow(() -> new IllegalArgumentException(
                        "No exported function: " + functionName));
        Object result = fn.invoke(args);
        if (returnType == void.class || returnType == Void.class) {
            return null;
        }
        return (R) result;
    }

    public void callVoid(String functionName, Object... args) {
        Function fn = instance.function(functionName)
                .orElseThrow(() -> new IllegalArgumentException(
                        "No exported function: " + functionName));
        fn.invoke(args);
    }

    public <T extends AutoCloseable> T bind(Class<T> iface) {
        return ProxyFactory.create(iface, engine, module, instance);
    }

    public Instance unwrap() {
        return instance;
    }

    @Override
    public void close() {
        // Instance is not AutoCloseable in the current API
    }
}
