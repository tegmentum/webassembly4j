package ai.tegmentum.webassembly4j.runtime;

import ai.tegmentum.webassembly4j.api.DefaultLinkingContext;
import ai.tegmentum.webassembly4j.api.Engine;
import ai.tegmentum.webassembly4j.api.Function;
import ai.tegmentum.webassembly4j.api.Instance;
import ai.tegmentum.webassembly4j.api.LinkingContext;
import ai.tegmentum.webassembly4j.runtime.proxy.ProxyFactory;

public final class WasmModule implements AutoCloseable {

    private final Engine engine;
    private final ai.tegmentum.webassembly4j.api.Module module;
    private final LinkingContext linkingContext;
    private volatile boolean closed;

    WasmModule(Engine engine, ai.tegmentum.webassembly4j.api.Module module,
               LinkingContext linkingContext) {
        this.engine = engine;
        this.module = module;
        this.linkingContext = linkingContext;
    }

    @SuppressWarnings("unchecked")
    public <R> R call(String functionName, Class<R> returnType, Object... args) {
        Instance instance = instantiate();
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
        Instance instance = instantiate();
        Function fn = instance.function(functionName)
                .orElseThrow(() -> new IllegalArgumentException(
                        "No exported function: " + functionName));
        fn.invoke(args);
    }

    public WasmInstance newInstance() {
        checkNotClosed();
        Instance instance = instantiate();
        return new WasmInstance(instance, module, engine);
    }

    public <T extends AutoCloseable> T bind(Class<T> iface) {
        checkNotClosed();
        Instance instance = instantiate();
        return ProxyFactory.create(iface, engine, module, instance);
    }

    @Override
    public void close() {
        if (!closed) {
            closed = true;
            try {
                module.close();
            } finally {
                engine.close();
            }
        }
    }

    private Instance instantiate() {
        checkNotClosed();
        if (linkingContext != null) {
            return module.instantiate(linkingContext);
        }
        return module.instantiate();
    }

    private void checkNotClosed() {
        if (closed) {
            throw new IllegalStateException("WasmModule has been closed");
        }
    }
}
