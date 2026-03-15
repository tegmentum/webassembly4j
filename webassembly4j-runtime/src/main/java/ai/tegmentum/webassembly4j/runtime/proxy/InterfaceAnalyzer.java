package ai.tegmentum.webassembly4j.runtime.proxy;

import ai.tegmentum.webassembly4j.runtime.annotation.WasmExport;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

final class InterfaceAnalyzer {

    private static final ConcurrentHashMap<Class<?>, Map<Method, MethodBinding>> analysisCache =
            new ConcurrentHashMap<>();

    private InterfaceAnalyzer() {
    }

    /**
     * Pre-computed binding metadata for a single interface method.
     */
    static final class MethodBinding {
        private final Method method;
        private final String exportName;
        private final boolean requiresMarshalling;
        private final boolean[] paramNeedsMarshalling;
        private final boolean returnNeedsMarshalling;

        private final int loweredArgCount;

        MethodBinding(Method method, String exportName) {
            this.method = method;
            this.exportName = exportName;

            Class<?>[] paramTypes = method.getParameterTypes();
            this.paramNeedsMarshalling = new boolean[paramTypes.length];
            boolean anyParamNeedsMarshalling = false;
            int argCount = 0;
            for (int i = 0; i < paramTypes.length; i++) {
                paramNeedsMarshalling[i] = TypeConverter.isComplexType(paramTypes[i]);
                if (paramNeedsMarshalling[i]) {
                    anyParamNeedsMarshalling = true;
                    argCount += 2; // complex types expand to (ptr, len)
                } else {
                    argCount += 1;
                }
            }

            Class<?> returnType = method.getReturnType();
            this.returnNeedsMarshalling = TypeConverter.isComplexType(returnType);
            this.requiresMarshalling = anyParamNeedsMarshalling || returnNeedsMarshalling;

            // retptr takes one slot when return needs marshalling
            this.loweredArgCount = argCount + (this.returnNeedsMarshalling ? 1 : 0);
        }

        Method method() {
            return method;
        }

        String exportName() {
            return exportName;
        }

        boolean requiresMarshalling() {
            return requiresMarshalling;
        }

        boolean[] paramNeedsMarshalling() {
            return paramNeedsMarshalling;
        }

        boolean returnNeedsMarshalling() {
            return returnNeedsMarshalling;
        }

        int loweredArgCount() {
            return loweredArgCount;
        }
    }

    /**
     * Resolves export names for all bindable methods on the interface.
     *
     * @param iface the interface to analyze
     * @return a map of Method to export name
     */
    static Map<Method, String> resolveExportNames(Class<?> iface) {
        if (!iface.isInterface()) {
            throw new IllegalArgumentException(iface.getName() + " is not an interface");
        }

        Map<Method, String> exports = new LinkedHashMap<>();

        for (Method method : iface.getMethods()) {
            if (isObjectMethod(method)) {
                continue;
            }
            if (isCloseMethod(method)) {
                continue;
            }
            if (method.isDefault()) {
                continue;
            }

            validateMethodSignature(method);

            WasmExport annotation = method.getAnnotation(WasmExport.class);
            String exportName;
            if (annotation != null && !annotation.value().isEmpty()) {
                exportName = annotation.value();
            } else {
                exportName = method.getName();
            }
            exports.put(method, exportName);
        }

        return exports;
    }

    /**
     * Analyzes the interface and returns pre-computed method bindings including
     * marshalling requirements for each method.
     *
     * @param iface the interface to analyze
     * @return a map of Method to MethodBinding
     */
    static Map<Method, MethodBinding> analyzeExports(Class<?> iface) {
        Map<Method, MethodBinding> cached = analysisCache.get(iface);
        if (cached != null) {
            return cached;
        }
        Map<Method, String> exportNames = resolveExportNames(iface);
        Map<Method, MethodBinding> bindings = new LinkedHashMap<>();
        for (Map.Entry<Method, String> entry : exportNames.entrySet()) {
            bindings.put(entry.getKey(), new MethodBinding(entry.getKey(), entry.getValue()));
        }
        Map<Method, MethodBinding> unmodifiable = Collections.unmodifiableMap(bindings);
        analysisCache.putIfAbsent(iface, unmodifiable);
        return unmodifiable;
    }

    private static void validateMethodSignature(Method method) {
        Class<?> returnType = method.getReturnType();
        if (!TypeConverter.isVoid(returnType)
                && !TypeConverter.isSupportedType(returnType)
                && !TypeConverter.isComplexType(returnType)) {
            throw new IllegalArgumentException(
                    "Unsupported return type " + returnType.getName()
                            + " on method " + method.getName()
                            + ". Supported types: int, long, float, double, void, String, byte[]");
        }

        for (Class<?> paramType : method.getParameterTypes()) {
            if ((!TypeConverter.isSupportedType(paramType) || TypeConverter.isVoid(paramType))
                    && !TypeConverter.isComplexType(paramType)) {
                throw new IllegalArgumentException(
                        "Unsupported parameter type " + paramType.getName()
                                + " on method " + method.getName()
                                + ". Supported types: int, long, float, double, String, byte[]");
            }
        }
    }

    private static final Set<String> OBJECT_METHOD_NAMES = new HashSet<>();
    static {
        for (Method m : Object.class.getMethods()) {
            OBJECT_METHOD_NAMES.add(m.getName());
        }
    }

    private static boolean isObjectMethod(Method method) {
        if (!OBJECT_METHOD_NAMES.contains(method.getName())) {
            return false;
        }
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
}
