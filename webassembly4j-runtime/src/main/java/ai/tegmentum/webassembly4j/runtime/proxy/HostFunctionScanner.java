package ai.tegmentum.webassembly4j.runtime.proxy;

import ai.tegmentum.webassembly4j.api.HostFunction;
import ai.tegmentum.webassembly4j.api.HostFunctionDefinition;
import ai.tegmentum.webassembly4j.api.ValueType;
import ai.tegmentum.webassembly4j.runtime.annotation.WasmImport;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

public final class HostFunctionScanner {

    private static final Object[] EMPTY_RESULT = new Object[0];

    private HostFunctionScanner() {
    }

    public static List<HostFunctionDefinition> scan(Object... hostObjects) {
        List<HostFunctionDefinition> definitions = new ArrayList<>();

        for (Object hostObject : hostObjects) {
            if (hostObject == null) {
                continue;
            }
            Class<?> clazz = hostObject.getClass();
            for (Method method : clazz.getMethods()) {
                WasmImport annotation = method.getAnnotation(WasmImport.class);
                if (annotation == null) {
                    continue;
                }

                String moduleName = annotation.module();
                String functionName = annotation.name().isEmpty()
                        ? method.getName() : annotation.name();

                Class<?>[] paramTypes = method.getParameterTypes();
                ValueType[] wasmParamTypes = new ValueType[paramTypes.length];
                for (int i = 0; i < paramTypes.length; i++) {
                    wasmParamTypes[i] = TypeConverter.javaTypeToValueType(paramTypes[i]);
                }

                ValueType[] wasmResultTypes;
                if (TypeConverter.isVoid(method.getReturnType())) {
                    wasmResultTypes = new ValueType[0];
                } else {
                    wasmResultTypes = new ValueType[]{
                            TypeConverter.javaTypeToValueType(method.getReturnType())};
                }

                final Object target = hostObject;
                final Method targetMethod = method;
                targetMethod.setAccessible(true);

                HostFunction hostFunction = createHostFunction(
                        target, targetMethod, paramTypes);

                definitions.add(new HostFunctionDefinition(
                        moduleName, functionName, wasmParamTypes, wasmResultTypes, hostFunction));
            }
        }

        return definitions;
    }

    private static HostFunction createHostFunction(Object target, Method method,
                                                   Class<?>[] paramTypes) {
        final int paramCount = paramTypes.length;
        final boolean voidReturn = TypeConverter.isVoid(method.getReturnType());

        // Fast paths for common signatures avoid Object[] allocation
        if (paramCount == 0) {
            return args -> {
                try {
                    Object result = method.invoke(target);
                    return voidReturn ? EMPTY_RESULT : new Object[]{result};
                } catch (Exception e) {
                    throw unwrap(e);
                }
            };
        }

        if (paramCount == 1) {
            final Class<?> p0 = paramTypes[0];
            return args -> {
                try {
                    Object result = method.invoke(target,
                            TypeConverter.fromWasm(args[0], p0));
                    return voidReturn ? EMPTY_RESULT : new Object[]{result};
                } catch (Exception e) {
                    throw unwrap(e);
                }
            };
        }

        if (paramCount == 2) {
            final Class<?> p0 = paramTypes[0];
            final Class<?> p1 = paramTypes[1];
            return args -> {
                try {
                    Object result = method.invoke(target,
                            TypeConverter.fromWasm(args[0], p0),
                            TypeConverter.fromWasm(args[1], p1));
                    return voidReturn ? EMPTY_RESULT : new Object[]{result};
                } catch (Exception e) {
                    throw unwrap(e);
                }
            };
        }

        // Generic path for 3+ params
        return args -> {
            try {
                Object[] convertedArgs = new Object[paramCount];
                for (int i = 0; i < paramCount; i++) {
                    convertedArgs[i] = TypeConverter.fromWasm(args[i], paramTypes[i]);
                }
                Object result = method.invoke(target, convertedArgs);
                return voidReturn ? EMPTY_RESULT : new Object[]{result};
            } catch (Exception e) {
                throw unwrap(e);
            }
        };
    }

    private static RuntimeException unwrap(Exception e) {
        if (e.getCause() instanceof RuntimeException) {
            return (RuntimeException) e.getCause();
        }
        return new RuntimeException("Host function invocation failed", e);
    }
}
