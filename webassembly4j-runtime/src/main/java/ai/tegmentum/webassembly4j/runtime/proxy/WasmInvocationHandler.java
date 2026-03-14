package ai.tegmentum.webassembly4j.runtime.proxy;

import ai.tegmentum.webassembly4j.api.Engine;
import ai.tegmentum.webassembly4j.api.Function;
import ai.tegmentum.webassembly4j.api.Instance;
import ai.tegmentum.webassembly4j.runtime.marshal.MarshalContext;
import ai.tegmentum.webassembly4j.runtime.marshal.StringCodec;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.Map;

final class WasmInvocationHandler implements InvocationHandler {

    private final Class<?> iface;
    private final Engine engine;
    private final ai.tegmentum.webassembly4j.api.Module module;
    private final Instance instance;
    private final Map<Method, Function> exports;
    private final Map<Method, InterfaceAnalyzer.MethodBinding> bindings;
    private volatile boolean closed;
    private MarshalContext marshalContext;

    WasmInvocationHandler(Class<?> iface, Engine engine, ai.tegmentum.webassembly4j.api.Module module,
                          Instance instance, Map<Method, Function> exports,
                          Map<Method, InterfaceAnalyzer.MethodBinding> bindings) {
        this.iface = iface;
        this.engine = engine;
        this.module = module;
        this.instance = instance;
        this.exports = exports;
        this.bindings = bindings;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        if (isCloseMethod(method)) {
            close();
            return null;
        }
        if (isEqualsMethod(method)) {
            return proxy == args[0];
        }
        if (isHashCodeMethod(method)) {
            return System.identityHashCode(proxy);
        }
        if (isToStringMethod(method)) {
            return "WasmProxy[" + iface.getSimpleName() + "]";
        }

        if (closed) {
            throw new IllegalStateException("WASM binding has been closed");
        }

        Function fn = exports.get(method);
        if (fn == null) {
            throw new UnsupportedOperationException(
                    "No WASM export bound for method: " + method.getName());
        }

        InterfaceAnalyzer.MethodBinding binding = bindings.get(method);
        if (binding != null && binding.requiresMarshalling()) {
            return invokeMarshal(fn, binding, args != null ? args : new Object[0]);
        }

        Object result = fn.invoke(args != null ? args : new Object[0]);
        return TypeConverter.fromWasm(result, method.getReturnType());
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

    private static boolean isCloseMethod(Method method) {
        return "close".equals(method.getName()) && method.getParameterCount() == 0;
    }

    private static boolean isEqualsMethod(Method method) {
        return "equals".equals(method.getName())
                && method.getParameterCount() == 1
                && method.getParameterTypes()[0] == Object.class;
    }

    private static boolean isHashCodeMethod(Method method) {
        return "hashCode".equals(method.getName()) && method.getParameterCount() == 0;
    }

    private static boolean isToStringMethod(Method method) {
        return "toString".equals(method.getName()) && method.getParameterCount() == 0;
    }
}
