package ai.tegmentum.webassembly4j.runtime.proxy;

import ai.tegmentum.webassembly4j.api.Component;
import ai.tegmentum.webassembly4j.api.ComponentInstance;
import ai.tegmentum.webassembly4j.api.Engine;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.Map;

/**
 * Invocation handler that delegates interface method calls to
 * {@link ComponentInstance#invoke(String, Object...)} for native component model support.
 * <p>
 * Unlike {@link WasmInvocationHandler}, no marshalling is needed because the native
 * component model handles type conversion between Java and WIT types.
 */
final class ComponentInvocationHandler implements InvocationHandler {

    private final Class<?> iface;
    private final Engine engine;
    private final Component component;
    private final ComponentInstance instance;
    private final Map<Method, InterfaceAnalyzer.MethodBinding> bindings;

    ComponentInvocationHandler(Class<?> iface, Engine engine, Component component,
                               ComponentInstance instance,
                               Map<Method, InterfaceAnalyzer.MethodBinding> bindings) {
        this.iface = iface;
        this.engine = engine;
        this.component = component;
        this.instance = instance;
        this.bindings = bindings;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        if (method.getDeclaringClass() == Object.class) {
            return method.invoke(this, args);
        }

        if ("close".equals(method.getName()) && method.getParameterCount() == 0) {
            component.close();
            engine.close();
            return null;
        }

        InterfaceAnalyzer.MethodBinding binding = bindings.get(method);
        if (binding == null) {
            throw new UnsupportedOperationException("No binding for " + method);
        }

        Object result = instance.invoke(binding.exportName(),
                args != null ? args : new Object[0]);

        Class<?> returnType = method.getReturnType();
        if (returnType == void.class || returnType == Void.class) {
            return null;
        }
        if (result == null) {
            return null;
        }
        if (result instanceof Number) {
            Number num = (Number) result;
            if (returnType == int.class || returnType == Integer.class) return num.intValue();
            if (returnType == long.class || returnType == Long.class) return num.longValue();
            if (returnType == float.class || returnType == Float.class) return num.floatValue();
            if (returnType == double.class || returnType == Double.class) return num.doubleValue();
        }
        return result;
    }
}
