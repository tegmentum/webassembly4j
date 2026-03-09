package ai.tegmentum.webassembly4j.runtime.proxy;

import ai.tegmentum.webassembly4j.api.ValueType;

final class TypeConverter {

    private TypeConverter() {
    }

    static ValueType javaTypeToValueType(Class<?> type) {
        if (type == int.class || type == Integer.class) {
            return ValueType.I32;
        } else if (type == long.class || type == Long.class) {
            return ValueType.I64;
        } else if (type == float.class || type == Float.class) {
            return ValueType.F32;
        } else if (type == double.class || type == Double.class) {
            return ValueType.F64;
        }
        throw new IllegalArgumentException(
                "Unsupported Java type for WASM binding: " + type.getName()
                        + ". Supported types: int, long, float, double, String, byte[]");
    }

    static Object fromWasm(Object wasmValue, Class<?> targetType) {
        if (wasmValue == null) {
            return null;
        }
        if (targetType == void.class || targetType == Void.class) {
            return null;
        }
        Number number = (Number) wasmValue;
        if (targetType == int.class || targetType == Integer.class) {
            return number.intValue();
        } else if (targetType == long.class || targetType == Long.class) {
            return number.longValue();
        } else if (targetType == float.class || targetType == Float.class) {
            return number.floatValue();
        } else if (targetType == double.class || targetType == Double.class) {
            return number.doubleValue();
        }
        return wasmValue;
    }

    static boolean isVoid(Class<?> type) {
        return type == void.class || type == Void.class;
    }

    static boolean isSupportedType(Class<?> type) {
        return type == int.class || type == Integer.class
                || type == long.class || type == Long.class
                || type == float.class || type == Float.class
                || type == double.class || type == Double.class
                || type == void.class || type == Void.class;
    }

    /**
     * Returns true if the type requires marshalling through linear memory.
     */
    static boolean isComplexType(Class<?> type) {
        return type == String.class || type == byte[].class;
    }

    /**
     * Returns true if any of the parameter types or the return type requires marshalling.
     */
    static boolean requiresMarshalling(Class<?>[] paramTypes, Class<?> returnType) {
        if (isComplexType(returnType)) {
            return true;
        }
        for (Class<?> paramType : paramTypes) {
            if (isComplexType(paramType)) {
                return true;
            }
        }
        return false;
    }
}
