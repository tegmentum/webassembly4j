package ai.tegmentum.webassembly4j.provider.chicory;

import ai.tegmentum.webassembly4j.api.Function;
import ai.tegmentum.webassembly4j.api.ValueType;
import ai.tegmentum.webassembly4j.api.exception.ExecutionException;
import ai.tegmentum.webassembly4j.api.exception.TrapException;
import com.dylibso.chicory.runtime.ExportFunction;
import com.dylibso.chicory.wasm.types.FunctionType;

import java.util.List;

final class ChicoryFunctionAdapter implements Function {

    private static final long[] EMPTY_LONGS = new long[0];

    private final ExportFunction nativeFunction;
    private final FunctionType functionType;
    private final int paramCount;
    private final List<com.dylibso.chicory.wasm.types.ValueType> cachedParamTypes;
    private final List<com.dylibso.chicory.wasm.types.ValueType> cachedReturnTypes;
    private final long[] argScratch;

    ChicoryFunctionAdapter(ExportFunction nativeFunction, FunctionType functionType) {
        this.nativeFunction = nativeFunction;
        this.functionType = functionType;
        this.cachedParamTypes = functionType.params();
        this.cachedReturnTypes = functionType.returns();
        this.paramCount = cachedParamTypes.size();
        this.argScratch = paramCount > 0 ? new long[paramCount] : EMPTY_LONGS;
    }

    @Override
    public ValueType[] parameterTypes() {
        return convertTypes(functionType.params());
    }

    @Override
    public ValueType[] resultTypes() {
        return convertTypes(functionType.returns());
    }

    @Override
    public Object invoke(Object... args) {
        long[] longArgs = convertToLongs(args);
        try {
            long[] results = nativeFunction.apply(longArgs);
            if (results == null || results.length == 0) {
                return null;
            }
            if (cachedReturnTypes.isEmpty()) {
                return null;
            }
            if (results.length == 1) {
                return extractValue(results[0], cachedReturnTypes.get(0));
            }
            Object[] extracted = new Object[results.length];
            for (int i = 0; i < results.length; i++) {
                extracted[i] = extractValue(results[i], cachedReturnTypes.get(i));
            }
            return extracted;
        } catch (com.dylibso.chicory.runtime.TrapException e) {
            throw new TrapException(e.getMessage(), e);
        } catch (Exception e) {
            throw new ExecutionException(e.getMessage(), e);
        }
    }

    private long[] convertToLongs(Object[] args) {
        if (args == null || args.length == 0) {
            return EMPTY_LONGS;
        }
        for (int i = 0; i < args.length; i++) {
            argScratch[i] = convertToLong(args[i], cachedParamTypes.get(i));
        }
        return argScratch;
    }

    private long convertToLong(Object value, com.dylibso.chicory.wasm.types.ValueType type) {
        Number num = (Number) value;
        switch (type) {
            case I32:
                return num.intValue();
            case I64:
                return num.longValue();
            case F32:
                return com.dylibso.chicory.wasm.types.Value.floatToLong(num.floatValue());
            case F64:
                return com.dylibso.chicory.wasm.types.Value.doubleToLong(num.doubleValue());
            default:
                throw new ExecutionException("Unsupported parameter type: " + type);
        }
    }

    private Object extractValue(long raw, com.dylibso.chicory.wasm.types.ValueType type) {
        switch (type) {
            case I32:
                return (int) raw;
            case I64:
                return raw;
            case F32:
                return com.dylibso.chicory.wasm.types.Value.longToFloat(raw);
            case F64:
                return com.dylibso.chicory.wasm.types.Value.longToDouble(raw);
            default:
                return raw;
        }
    }

    private static ValueType[] convertTypes(
            List<com.dylibso.chicory.wasm.types.ValueType> chicoryTypes) {
        ValueType[] types = new ValueType[chicoryTypes.size()];
        for (int i = 0; i < chicoryTypes.size(); i++) {
            types[i] = convertType(chicoryTypes.get(i));
        }
        return types;
    }

    private static ValueType convertType(com.dylibso.chicory.wasm.types.ValueType chicoryType) {
        if (chicoryType == com.dylibso.chicory.wasm.types.ValueType.I32) return ValueType.I32;
        if (chicoryType == com.dylibso.chicory.wasm.types.ValueType.I64) return ValueType.I64;
        if (chicoryType == com.dylibso.chicory.wasm.types.ValueType.F32) return ValueType.F32;
        if (chicoryType == com.dylibso.chicory.wasm.types.ValueType.F64) return ValueType.F64;
        if (chicoryType == com.dylibso.chicory.wasm.types.ValueType.V128) return ValueType.V128;
        if (chicoryType == com.dylibso.chicory.wasm.types.ValueType.FuncRef) return ValueType.FUNCREF;
        if (chicoryType == com.dylibso.chicory.wasm.types.ValueType.ExternRef) return ValueType.EXTERNREF;
        throw new IllegalArgumentException("Unknown type: " + chicoryType);
    }
}
