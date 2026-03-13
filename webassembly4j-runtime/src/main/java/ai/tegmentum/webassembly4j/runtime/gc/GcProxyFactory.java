package ai.tegmentum.webassembly4j.runtime.gc;

import ai.tegmentum.webassembly4j.api.Engine;
import ai.tegmentum.webassembly4j.api.Function;
import ai.tegmentum.webassembly4j.api.Instance;
import ai.tegmentum.webassembly4j.api.gc.GcExtension;
import ai.tegmentum.webassembly4j.runtime.annotation.WasmExport;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Creates Java interface proxies that marshal method arguments and return values
 * through WebAssembly GC struct types instead of linear memory.
 *
 * <p>This is the GC counterpart to
 * {@link ai.tegmentum.webassembly4j.runtime.proxy.ProxyFactory ProxyFactory}.
 * Where ProxyFactory marshals complex types through linear memory using the
 * Canonical ABI (ptr+len pairs), GcProxyFactory marshals {@link GcMapped}-annotated
 * types as GC struct references on the Wasm managed heap.
 *
 * <p>The runtime's GC manages the object lifetime — no manual allocation or
 * freeing is needed.
 *
 * <p>Supported method parameter/return types:
 * <ul>
 *   <li>Primitive numerics: {@code int}, {@code long}, {@code float}, {@code double}</li>
 *   <li>Boolean: {@code boolean}</li>
 *   <li>{@code void} returns</li>
 *   <li>{@link GcMapped}-annotated classes and records — automatically marshalled
 *       to/from GC struct instances</li>
 * </ul>
 *
 * <p>Example:
 * <pre>{@code
 * @GcMapped
 * record Point(double x, double y) {}
 *
 * interface Geometry extends AutoCloseable {
 *     @WasmExport("rotate_point")
 *     Point rotate(Point p, double angle);
 * }
 *
 * GcExtension gc = instance.extension(GcExtension.class).orElseThrow();
 * Geometry geom = GcProxyFactory.create(Geometry.class, engine, module, instance, gc);
 * Point rotated = geom.rotate(new Point(1, 0), Math.PI / 2);
 * }</pre>
 */
public final class GcProxyFactory {

    private GcProxyFactory() {
    }

    /**
     * Creates a proxy binding for the given interface using GC-based marshalling.
     *
     * @param iface    the interface to bind
     * @param engine   the engine (for lifecycle management)
     * @param module   the compiled module
     * @param instance the instantiated module
     * @param gc       the GC extension from the instance
     * @param <T>      the interface type
     * @return a proxy implementing the interface
     */
    @SuppressWarnings("unchecked")
    public static <T> T create(Class<T> iface, Engine engine,
                                ai.tegmentum.webassembly4j.api.Module module,
                                Instance instance, GcExtension gc) {
        Objects.requireNonNull(iface, "iface");
        Objects.requireNonNull(instance, "instance");
        Objects.requireNonNull(gc, "gc");
        if (!iface.isInterface()) {
            throw new IllegalArgumentException(iface.getName() + " is not an interface");
        }

        GcTypeMapper typeMapper = new GcTypeMapper();
        GcMarshaller marshaller = GcMarshaller.forExtension(gc, typeMapper);

        Map<Method, MethodBinding> bindings = analyzeInterface(iface);
        Map<Method, Function> exports = new LinkedHashMap<>();

        for (Map.Entry<Method, MethodBinding> entry : bindings.entrySet()) {
            String exportName = entry.getValue().exportName;
            Function fn = instance.function(exportName)
                    .orElseThrow(() -> new IllegalArgumentException(
                            "WASM module does not export function: " + exportName));
            exports.put(entry.getKey(), fn);
        }

        GcInvocationHandler handler = new GcInvocationHandler(
                iface, engine, module, instance, exports, bindings, marshaller);

        return (T) Proxy.newProxyInstance(
                iface.getClassLoader(),
                new Class<?>[]{iface},
                handler);
    }

    static Map<Method, MethodBinding> analyzeInterface(Class<?> iface) {
        if (!iface.isInterface()) {
            throw new IllegalArgumentException(iface.getName() + " is not an interface");
        }
        Map<Method, MethodBinding> bindings = new LinkedHashMap<>();

        for (Method method : iface.getMethods()) {
            if (isObjectMethod(method) || isCloseMethod(method) || method.isDefault()) {
                continue;
            }
            validateMethodSignature(method);

            WasmExport annotation = method.getAnnotation(WasmExport.class);
            String exportName = (annotation != null && !annotation.value().isEmpty())
                    ? annotation.value()
                    : method.getName();

            bindings.put(method, new MethodBinding(method, exportName));
        }
        return bindings;
    }

    private static void validateMethodSignature(Method method) {
        Class<?> returnType = method.getReturnType();
        if (!isSupported(returnType) && returnType != void.class && returnType != Void.class) {
            throw new IllegalArgumentException(
                    "Unsupported return type " + returnType.getName()
                            + " on method " + method.getName()
                            + ". Supported: int, long, float, double, boolean, void, or @GcMapped types");
        }
        for (Class<?> paramType : method.getParameterTypes()) {
            if (!isSupported(paramType)) {
                throw new IllegalArgumentException(
                        "Unsupported parameter type " + paramType.getName()
                                + " on method " + method.getName()
                                + ". Supported: int, long, float, double, boolean, or @GcMapped types");
            }
        }
    }

    private static boolean isSupported(Class<?> type) {
        return type == int.class || type == Integer.class
                || type == long.class || type == Long.class
                || type == float.class || type == Float.class
                || type == double.class || type == Double.class
                || type == boolean.class || type == Boolean.class
                || GcTypeMapper.isGcMapped(type);
    }

    private static boolean isObjectMethod(Method method) {
        try {
            Object.class.getMethod(method.getName(), method.getParameterTypes());
            return true;
        } catch (NoSuchMethodException e) {
            return false;
        }
    }

    private static boolean isCloseMethod(Method method) {
        return "close".equals(method.getName()) && method.getParameterCount() == 0;
    }

    /**
     * Pre-computed binding metadata for a single interface method.
     */
    static final class MethodBinding {
        final Method method;
        final String exportName;
        final boolean[] paramNeedsGcMarshalling;
        final boolean returnNeedsGcMarshalling;
        final boolean anyGcMarshalling;

        MethodBinding(Method method, String exportName) {
            this.method = method;
            this.exportName = exportName;

            Class<?>[] paramTypes = method.getParameterTypes();
            this.paramNeedsGcMarshalling = new boolean[paramTypes.length];
            boolean any = false;
            for (int i = 0; i < paramTypes.length; i++) {
                paramNeedsGcMarshalling[i] = GcTypeMapper.isGcMapped(paramTypes[i]);
                if (paramNeedsGcMarshalling[i]) {
                    any = true;
                }
            }
            this.returnNeedsGcMarshalling = GcTypeMapper.isGcMapped(method.getReturnType());
            this.anyGcMarshalling = any || returnNeedsGcMarshalling;
        }
    }
}
