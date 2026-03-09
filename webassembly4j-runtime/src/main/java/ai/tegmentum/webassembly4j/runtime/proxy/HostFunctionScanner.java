package ai.tegmentum.webassembly4j.runtime.proxy;

import ai.tegmentum.webassembly4j.api.HostFunction;
import ai.tegmentum.webassembly4j.api.HostFunctionDefinition;
import ai.tegmentum.webassembly4j.api.ValueType;
import ai.tegmentum.webassembly4j.runtime.annotation.WasmImport;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

public final class HostFunctionScanner {

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
                HostFunction hostFunction = args -> {
                    try {
                        Object[] convertedArgs = new Object[paramTypes.length];
                        for (int i = 0; i < paramTypes.length; i++) {
                            convertedArgs[i] = TypeConverter.fromWasm(args[i], paramTypes[i]);
                        }
                        Object result = targetMethod.invoke(target, convertedArgs);
                        if (TypeConverter.isVoid(targetMethod.getReturnType())) {
                            return new Object[0];
                        }
                        return new Object[]{result};
                    } catch (Exception e) {
                        if (e.getCause() instanceof RuntimeException) {
                            throw (RuntimeException) e.getCause();
                        }
                        throw new RuntimeException("Host function invocation failed", e);
                    }
                };

                definitions.add(new HostFunctionDefinition(
                        moduleName, functionName, wasmParamTypes, wasmResultTypes, hostFunction));
            }
        }

        return definitions;
    }
}
