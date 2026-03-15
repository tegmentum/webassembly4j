package ai.tegmentum.webassembly4j.runtime.proxy;

import ai.tegmentum.webassembly4j.api.Engine;
import ai.tegmentum.webassembly4j.api.Function;
import ai.tegmentum.webassembly4j.api.Instance;
import ai.tegmentum.webassembly4j.runtime.marshal.MarshalContext;
import ai.tegmentum.webassembly4j.runtime.marshal.StringCodec;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

final class WasmInvocationHandler implements InvocationHandler {

    private static final Object[] EMPTY_ARGS = new Object[0];

    /**
     * Pre-computed dispatch entry for a single method. Avoids per-call string
     * comparisons and HashMap lookups by caching everything at construction time.
     */
    private static final class DispatchEntry {
        final Function function;
        final InterfaceAnalyzer.MethodBinding binding;
        final Class<?> returnType;

        DispatchEntry(Function function, InterfaceAnalyzer.MethodBinding binding,
                      Class<?> returnType) {
            this.function = function;
            this.binding = binding;
            this.returnType = returnType;
        }
    }

    private final Class<?> iface;
    private final Engine engine;
    private final ai.tegmentum.webassembly4j.api.Module module;
    private final Instance instance;

    // Single dispatch table: merges exports + bindings + returnType into one lookup
    private final HashMap<Method, DispatchEntry> dispatch;

    private volatile boolean closed;
    private MarshalContext marshalContext;

    WasmInvocationHandler(Class<?> iface, Engine engine, ai.tegmentum.webassembly4j.api.Module module,
                          Instance instance, Map<Method, Function> exports,
                          Map<Method, InterfaceAnalyzer.MethodBinding> bindings) {
        this.iface = iface;
        this.engine = engine;
        this.module = module;
        this.instance = instance;

        // Pre-compute dispatch table merging exports + bindings + returnType
        this.dispatch = new HashMap<>(exports.size());
        for (Map.Entry<Method, Function> entry : exports.entrySet()) {
            Method m = entry.getKey();
            InterfaceAnalyzer.MethodBinding binding = bindings.get(m);
            dispatch.put(m, new DispatchEntry(entry.getValue(), binding, m.getReturnType()));
        }
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        // Check for special Object/AutoCloseable methods using lightweight string checks.
        // These are not on the hot path — WASM method names never collide.
        String name = method.getName();
        switch (name.length()) {
            case 5: // "close"
                if ("close".equals(name) && method.getParameterCount() == 0) {
                    close();
                    return null;
                }
                break;
            case 6: // "equals"
                if ("equals".equals(name) && method.getParameterCount() == 1) {
                    return proxy == args[0];
                }
                break;
            case 8: // "hashCode" or "toString"
                if ("hashCode".equals(name) && method.getParameterCount() == 0) {
                    return System.identityHashCode(proxy);
                }
                if ("toString".equals(name) && method.getParameterCount() == 0) {
                    return "WasmProxy[" + iface.getSimpleName() + "]";
                }
                break;
        }

        if (closed) {
            throw new IllegalStateException("WASM binding has been closed");
        }

        // Single lookup replaces exports.get() + bindings.get() + method.getReturnType()
        DispatchEntry entry = dispatch.get(method);
        if (entry == null) {
            throw new UnsupportedOperationException(
                    "No WASM export bound for method: " + method.getName());
        }

        if (entry.binding != null && entry.binding.requiresMarshalling()) {
            return invokeMarshal(entry.function, entry.binding, args != null ? args : EMPTY_ARGS);
        }

        Object result = entry.function.invoke(args != null ? args : EMPTY_ARGS);
        return TypeConverter.fromWasm(result, entry.returnType);
    }

    private Object invokeMarshal(Function fn, InterfaceAnalyzer.MethodBinding binding, Object[] args) {
        MarshalContext ctx = getMarshalContext();
        Class<?>[] paramTypes = binding.method().getParameterTypes();
        Class<?> returnType = binding.method().getReturnType();
        boolean[] paramMarshalling = binding.paramNeedsMarshalling();

        Object[] loweredArgs = new Object[binding.loweredArgCount()];
        int argIdx = 0;

        // For complex return types, allocate a return pointer as the first argument
        int retptr = -1;
        if (binding.returnNeedsMarshalling()) {
            retptr = ctx.allocator().allocate(8, 4); // (ptr, len) pair
            loweredArgs[argIdx++] = retptr;
        }

        // Lower each parameter using pre-computed flags
        for (int i = 0; i < paramTypes.length; i++) {
            if (paramMarshalling[i]) {
                if (paramTypes[i] == String.class) {
                    int[] encoded = StringCodec.encode(
                            (String) args[i], ctx.memory(), ctx.allocator());
                    loweredArgs[argIdx++] = encoded[0]; // ptr
                    loweredArgs[argIdx++] = encoded[1]; // len
                } else if (paramTypes[i] == byte[].class) {
                    byte[] data = (byte[]) args[i];
                    int ptr = ctx.allocator().allocate(data.length, 1);
                    ctx.memory().write(ptr, data);
                    loweredArgs[argIdx++] = ptr;
                    loweredArgs[argIdx++] = data.length;
                }
            } else {
                loweredArgs[argIdx++] = args[i];
            }
        }

        Object result = fn.invoke(loweredArgs);

        // Lift the return value
        if (binding.returnNeedsMarshalling()) {
            if (returnType == String.class) {
                return ctx.reader().readString(retptr);
            } else if (returnType == byte[].class) {
                return ctx.reader().readBytes(retptr);
            }
        }

        return TypeConverter.fromWasm(result, returnType);
    }

    private MarshalContext getMarshalContext() {
        if (marshalContext == null) {
            marshalContext = MarshalContext.fromInstance(instance);
        }
        return marshalContext;
    }

    private void close() {
        if (!closed) {
            closed = true;
            try {
                module.close();
            } finally {
                engine.close();
            }
        }
    }
}
