package ai.tegmentum.webassembly4j.provider.wasmtime;

import ai.tegmentum.wasmtime4j.WasmGlobal;
import ai.tegmentum.wasmtime4j.WasmValue;
import ai.tegmentum.wasmtime4j.WasmValueType;
import ai.tegmentum.webassembly4j.api.Global;
import ai.tegmentum.webassembly4j.api.ValueType;
import ai.tegmentum.webassembly4j.api.exception.ExecutionException;

import java.util.Optional;

final class WasmtimeGlobalAdapter implements Global {

    private final WasmGlobal nativeGlobal;

    WasmtimeGlobalAdapter(WasmGlobal nativeGlobal) {
        this.nativeGlobal = nativeGlobal;
    }

    @Override
    public ValueType type() {
        return convertType(nativeGlobal.getType());
    }

    @Override
    public Object get() {
        WasmValue value = nativeGlobal.get();
        switch (value.getType()) {
            case I32: return value.asInt();
            case I64: return value.asLong();
            case F32: return value.asFloat();
            case F64: return value.asDouble();
            default: return value;
        }
    }

    @Override
    public void set(Object value) {
        WasmValue wasmValue;
        Number num = (Number) value;
        switch (nativeGlobal.getType()) {
            case I32:
                wasmValue = WasmValue.i32(num.intValue());
                break;
            case I64:
                wasmValue = WasmValue.i64(num.longValue());
                break;
            case F32:
                wasmValue = WasmValue.f32(num.floatValue());
                break;
            case F64:
                wasmValue = WasmValue.f64(num.doubleValue());
                break;
            default:
                throw new ExecutionException("Unsupported global type: " + nativeGlobal.getType());
        }
        nativeGlobal.set(wasmValue);
    }

    @Override
    public boolean mutable() {
        return nativeGlobal.isMutable();
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> Optional<T> unwrap(Class<T> nativeType) {
        if (nativeType.isInstance(nativeGlobal)) {
            return Optional.of((T) nativeGlobal);
        }
        return Optional.empty();
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
