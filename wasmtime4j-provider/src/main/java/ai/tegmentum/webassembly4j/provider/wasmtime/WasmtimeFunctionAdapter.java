package ai.tegmentum.webassembly4j.provider.wasmtime;

import ai.tegmentum.wasmtime4j.WasmFunction;
import ai.tegmentum.wasmtime4j.WasmValue;
import ai.tegmentum.wasmtime4j.WasmValueType;
import ai.tegmentum.wasmtime4j.type.FunctionType;
import ai.tegmentum.webassembly4j.api.Function;
import ai.tegmentum.webassembly4j.api.ValueType;
import ai.tegmentum.webassembly4j.api.exception.ExecutionException;
import ai.tegmentum.webassembly4j.api.exception.TrapException;

import java.util.List;

final class WasmtimeFunctionAdapter implements Function {

    private enum FastPath { V_V, V_I, I_I, II_I, I_V, J_J, GENERIC }

    private final WasmFunction nativeFunction;
    private volatile FastPath cachedFastPath;

    WasmtimeFunctionAdapter(WasmFunction nativeFunction) {
        this.nativeFunction = nativeFunction;
    }

    private FastPath resolveFastPath() {
        FastPath fp = cachedFastPath;
        if (fp != null) {
            return fp;
        }
        FunctionType type = nativeFunction.getFunctionType();
        fp = classifySignature(type.getParams(), type.getResults());
        cachedFastPath = fp;
        return fp;
    }

    private static FastPath classifySignature(List<WasmValueType> params,
                                              List<WasmValueType> results) {
        boolean voidReturn = results.isEmpty();
        boolean i32Return = results.size() == 1 && results.get(0) == WasmValueType.I32;
        boolean i64Return = results.size() == 1 && results.get(0) == WasmValueType.I64;

        if (voidReturn && params.isEmpty()) return FastPath.V_V;
        if (voidReturn && params.size() == 1 && params.get(0) == WasmValueType.I32)
            return FastPath.I_V;
        if (i32Return && params.isEmpty()) return FastPath.V_I;
        if (i32Return && params.size() == 1 && params.get(0) == WasmValueType.I32)
            return FastPath.I_I;
        if (i32Return && params.size() == 2
                && params.get(0) == WasmValueType.I32 && params.get(1) == WasmValueType.I32)
            return FastPath.II_I;
        if (i64Return && params.size() == 1 && params.get(0) == WasmValueType.I64)
            return FastPath.J_J;
        return FastPath.GENERIC;
    }

    @Override
    public ValueType[] parameterTypes() {
        FunctionType type = nativeFunction.getFunctionType();
        return convertTypes(type.getParams());
    }

    @Override
    public ValueType[] resultTypes() {
        FunctionType type = nativeFunction.getFunctionType();
        return convertTypes(type.getResults());
    }

    @Override
    public Object invoke(Object... args) {
        try {
            return invokeFastPath(args);
        } catch (ai.tegmentum.wasmtime4j.exception.TrapException e) {
            throw new TrapException(e.getMessage(), e);
        } catch (ai.tegmentum.wasmtime4j.exception.WasmException e) {
            throw new ExecutionException(e.getMessage(), e);
        }
    }

    private Object invokeFastPath(Object[] args) throws ai.tegmentum.wasmtime4j.exception.WasmException {
        switch (resolveFastPath()) {
            case V_V:
                nativeFunction.callVoid();
                return null;
            case V_I:
                return nativeFunction.callToI32();
            case I_V:
                nativeFunction.callVoid();
                return null;
            case I_I:
                return nativeFunction.callI32ToI32(((Number) args[0]).intValue());
            case II_I:
                return nativeFunction.callI32I32ToI32(
                        ((Number) args[0]).intValue(), ((Number) args[1]).intValue());
            case J_J:
                return nativeFunction.callI64ToI64(((Number) args[0]).longValue());
            default:
                return invokeGeneric(args);
        }
    }

    private Object invokeGeneric(Object[] args) throws ai.tegmentum.wasmtime4j.exception.WasmException {
        WasmValue[] wasmArgs = convertToWasmValues(args);
        WasmValue[] results = nativeFunction.call(wasmArgs);
        if (results.length == 0) {
            return null;
        }
        if (results.length == 1) {
            return extractValue(results[0]);
        }
        Object[] extracted = new Object[results.length];
        for (int i = 0; i < results.length; i++) {
            extracted[i] = extractValue(results[i]);
        }
        return extracted;
    }

    private WasmValue[] convertToWasmValues(Object[] args) {
        if (args == null || args.length == 0) {
            return new WasmValue[0];
        }
        FunctionType type = nativeFunction.getFunctionType();
        List<WasmValueType> paramTypes = type.getParams();
        WasmValue[] wasmValues = new WasmValue[args.length];
        for (int i = 0; i < args.length; i++) {
            wasmValues[i] = convertToWasmValue(args[i], paramTypes.get(i));
        }
        return wasmValues;
    }

    private WasmValue convertToWasmValue(Object value, WasmValueType targetType) {
        switch (targetType) {
            case I32:
                return WasmValue.i32(((Number) value).intValue());
            case I64:
                return WasmValue.i64(((Number) value).longValue());
            case F32:
                return WasmValue.f32(((Number) value).floatValue());
            case F64:
                return WasmValue.f64(((Number) value).doubleValue());
            default:
                throw new ExecutionException("Unsupported parameter type: " + targetType);
        }
    }

    private Object extractValue(WasmValue value) {
        switch (value.getType()) {
            case I32:
                return value.asInt();
            case I64:
                return value.asLong();
            case F32:
                return value.asFloat();
            case F64:
                return value.asDouble();
            default:
                return value;
        }
    }

    private static ValueType[] convertTypes(List<WasmValueType> nativeTypes) {
        ValueType[] types = new ValueType[nativeTypes.size()];
        for (int i = 0; i < nativeTypes.size(); i++) {
            types[i] = convertType(nativeTypes.get(i));
        }
        return types;
    }

    private static ValueType convertType(WasmValueType nativeType) {
        if (nativeType == WasmValueType.I32) return ValueType.I32;
        if (nativeType == WasmValueType.I64) return ValueType.I64;
        if (nativeType == WasmValueType.F32) return ValueType.F32;
        if (nativeType == WasmValueType.F64) return ValueType.F64;
        if (nativeType == WasmValueType.V128) return ValueType.V128;
        if (nativeType == WasmValueType.FUNCREF) return ValueType.FUNCREF;
        if (nativeType == WasmValueType.EXTERNREF) return ValueType.EXTERNREF;
        throw new IllegalArgumentException("Unknown type: " + nativeType);
    }
}
