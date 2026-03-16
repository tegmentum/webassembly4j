package ai.tegmentum.webassembly4j.provider.chicory;

import ai.tegmentum.webassembly4j.api.Global;
import ai.tegmentum.webassembly4j.api.ValueType;
import com.dylibso.chicory.runtime.GlobalInstance;
import com.dylibso.chicory.wasm.types.MutabilityType;
import com.dylibso.chicory.wasm.types.ValType;
import com.dylibso.chicory.wasm.types.Value;

import java.util.Optional;

final class ChicoryGlobalAdapter implements Global {

    private final GlobalInstance nativeGlobal;

    ChicoryGlobalAdapter(GlobalInstance nativeGlobal) {
        this.nativeGlobal = nativeGlobal;
    }

    @Override
    public ValueType type() {
        return convertType(nativeGlobal.getType());
    }

    @Override
    public Object get() {
        long raw = nativeGlobal.getValue();
        ValType type = nativeGlobal.getType();
        if (type .equals(ValType.I32)) {
            return (int) raw;
        } else if (type .equals(ValType.I64)) {
            return raw;
        } else if (type .equals(ValType.F32)) {
            return Value.longToFloat(raw);
        } else if (type .equals(ValType.F64)) {
            return Value.longToDouble(raw);
        } else {
            return raw;
        }
    }

    @Override
    public void set(Object value) {
        Number num = (Number) value;
        ValType type = nativeGlobal.getType();
        if (type .equals(ValType.I32)) {
            nativeGlobal.setValue(num.intValue());
        } else if (type .equals(ValType.I64)) {
            nativeGlobal.setValue(num.longValue());
        } else if (type .equals(ValType.F32)) {
            nativeGlobal.setValue(Value.floatToLong(num.floatValue()));
        } else if (type .equals(ValType.F64)) {
            nativeGlobal.setValue(Value.doubleToLong(num.doubleValue()));
        } else {
            nativeGlobal.setValue(num.longValue());
        }
    }

    @Override
    public boolean mutable() {
        return nativeGlobal.getMutabilityType() == MutabilityType.Var;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> Optional<T> unwrap(Class<T> nativeType) {
        if (nativeType.isInstance(nativeGlobal)) {
            return Optional.of((T) nativeGlobal);
        }
        return Optional.empty();
    }

    private static ValueType convertType(ValType chicoryType) {
        if (chicoryType .equals(ValType.I32)) return ValueType.I32;
        if (chicoryType .equals(ValType.I64)) return ValueType.I64;
        if (chicoryType .equals(ValType.F32)) return ValueType.F32;
        if (chicoryType .equals(ValType.F64)) return ValueType.F64;
        throw new IllegalArgumentException("Unknown type: " + chicoryType);
    }
}
