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

    private final WasmFunction nativeFunction;

    WasmtimeFunctionAdapter(WasmFunction nativeFunction) {
        this.nativeFunction = nativeFunction;
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
        WasmValue[] wasmArgs = convertToWasmValues(args);
        try {
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
        } catch (ai.tegmentum.wasmtime4j.exception.TrapException e) {
            throw new TrapException(e.getMessage(), e);
        } catch (ai.tegmentum.wasmtime4j.exception.WasmException e) {
            throw new ExecutionException(e.getMessage(), e);
        }
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
