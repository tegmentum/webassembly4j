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
    private Instance sharedInstance;

    WasmModule(Engine engine, ai.tegmentum.webassembly4j.api.Module module,
               LinkingContext linkingContext) {
        this.engine = engine;
        this.module = module;
        this.linkingContext = linkingContext;
    }

    /**
     * Invokes a function using a shared instance. The shared instance is
     * created lazily on first call and reused for subsequent calls.
     * State mutations (globals, memory) persist across calls.
     * Use {@link #newInstance()} for isolated execution.
     */
    @SuppressWarnings("unchecked")
    public <R> R call(String functionName, Class<R> returnType, Object... args) {
        Function fn = getSharedInstance().function(functionName)
                .orElseThrow(() -> new IllegalArgumentException(
                        "No exported function: " + functionName));
        Object result = fn.invoke(args);
        if (returnType == void.class || returnType == Void.class) {
            return null;
        }
        return (R) result;
    }

    /**
     * Invokes a void function using a shared instance. The shared instance is
     * created lazily on first call and reused for subsequent calls.
     * State mutations (globals, memory) persist across calls.
     * Use {@link #newInstance()} for isolated execution.
     */
    public void callVoid(String functionName, Object... args) {
        getSharedInstance().function(functionName)
                .orElseThrow(() -> new IllegalArgumentException(
                        "No exported function: " + functionName))
                .invoke(args);
    }

    /**
     * Creates a new isolated instance of this module.
     */
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

    private Instance getSharedInstance() {
        checkNotClosed();
        if (sharedInstance == null) {
            sharedInstance = instantiate();
        }
        return sharedInstance;
    }

    private Instance instantiate() {
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
