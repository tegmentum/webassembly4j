package ai.tegmentum.webassembly4j.runtime.gc;

import ai.tegmentum.webassembly4j.api.Engine;
import ai.tegmentum.webassembly4j.api.Function;
import ai.tegmentum.webassembly4j.api.Instance;
import ai.tegmentum.webassembly4j.api.gc.GcStructInstance;
import ai.tegmentum.webassembly4j.runtime.proxy.TypeConverter;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.Map;

/**
 * Invocation handler that marshals {@link GcMapped}-annotated parameters and
 * return values through WebAssembly GC structs.
 *
 * <p>Primitive parameters pass through directly. GC-mapped parameters are
 * marshalled to {@link GcStructInstance} before the call, and GC-mapped return
 * types are unmarshalled back to Java objects after.
 */
final class GcInvocationHandler implements InvocationHandler {

    private final Class<?> iface;
    private final Engine engine;
    private final ai.tegmentum.webassembly4j.api.Module module;
    private final Instance instance;
    private final Map<Method, Function> exports;
    private final Map<Method, GcProxyFactory.MethodBinding> bindings;
    private final GcMarshaller marshaller;
    private volatile boolean closed;

    GcInvocationHandler(Class<?> iface, Engine engine,
                         ai.tegmentum.webassembly4j.api.Module module,
                         Instance instance, Map<Method, Function> exports,
                         Map<Method, GcProxyFactory.MethodBinding> bindings,
                         GcMarshaller marshaller) {
        this.iface = iface;
        this.engine = engine;
        this.module = module;
        this.instance = instance;
        this.exports = exports;
        this.bindings = bindings;
        this.marshaller = marshaller;
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
            return "GcProxy[" + iface.getSimpleName() + "]";
        }

        if (closed) {
            throw new IllegalStateException("GC binding has been closed");
        }

        Function fn = exports.get(method);
        if (fn == null) {
            throw new UnsupportedOperationException(
                    "No WASM export bound for method: " + method.getName());
        }

        GcProxyFactory.MethodBinding binding = bindings.get(method);
        Object[] actualArgs = args != null ? args : new Object[0];

        if (binding != null && binding.anyGcMarshalling) {
            return invokeWithGcMarshalling(fn, binding, actualArgs);
        }

        // All primitive — direct pass-through
        Object result = fn.invoke(actualArgs);
        return convertReturn(result, method.getReturnType());
    }

    private Object invokeWithGcMarshalling(Function fn, GcProxyFactory.MethodBinding binding,
                                            Object[] args) {
        Class<?>[] paramTypes = binding.method.getParameterTypes();
        Object[] marshalledArgs = new Object[args.length];

        for (int i = 0; i < args.length; i++) {
            if (binding.paramNeedsGcMarshalling[i]) {
                marshalledArgs[i] = marshaller.marshal(args[i]);
            } else {
                marshalledArgs[i] = args[i];
            }
        }

        Object result = fn.invoke(marshalledArgs);

        if (binding.returnNeedsGcMarshalling) {
            return marshaller.unmarshal((GcStructInstance) result, binding.method.getReturnType());
        }
        return convertReturn(result, binding.method.getReturnType());
    }

    private static Object convertReturn(Object value, Class<?> targetType) {
        if (value == null || targetType == void.class || targetType == Void.class) {
            return null;
        }
        if (targetType == boolean.class || targetType == Boolean.class) {
            return ((Number) value).intValue() != 0;
        }
        return TypeConverter.fromWasm(value, targetType);
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
