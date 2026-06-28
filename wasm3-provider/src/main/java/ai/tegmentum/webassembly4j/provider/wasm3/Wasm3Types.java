package ai.tegmentum.webassembly4j.provider.wasm3;

import ai.tegmentum.webassembly4j.api.ValueType;

/** Conversions between webassembly4j and wasm34j value types and values. */
final class Wasm3Types {

    private Wasm3Types() {
    }

    static ValueType toApi(final ai.tegmentum.wasm34j.ValueType type) {
        switch (type) {
            case I32:
                return ValueType.I32;
            case I64:
                return ValueType.I64;
            case F32:
                return ValueType.F32;
            case F64:
                return ValueType.F64;
            case V128:
                return ValueType.V128;
            case FUNCREF:
                return ValueType.FUNCREF;
            case EXTERNREF:
                return ValueType.EXTERNREF;
            default:
                throw new IllegalArgumentException("Unsupported wasm34j value type: " + type);
        }
    }

    static ai.tegmentum.wasm34j.ValueType toNative(final ValueType type) {
        switch (type) {
            case I32:
                return ai.tegmentum.wasm34j.ValueType.I32;
            case I64:
                return ai.tegmentum.wasm34j.ValueType.I64;
            case F32:
                return ai.tegmentum.wasm34j.ValueType.F32;
            case F64:
                return ai.tegmentum.wasm34j.ValueType.F64;
            case V128:
                return ai.tegmentum.wasm34j.ValueType.V128;
            case FUNCREF:
                return ai.tegmentum.wasm34j.ValueType.FUNCREF;
            case EXTERNREF:
                return ai.tegmentum.wasm34j.ValueType.EXTERNREF;
            default:
                throw new IllegalArgumentException("Unsupported value type: " + type);
        }
    }

    static ai.tegmentum.wasm34j.ValueType[] toNative(final ValueType[] types) {
        final ai.tegmentum.wasm34j.ValueType[] out = new ai.tegmentum.wasm34j.ValueType[types.length];
        for (int i = 0; i < types.length; i++) {
            out[i] = toNative(types[i]);
        }
        return out;
    }

    /** Boxes a Java number into a wasm34j value of the given type. */
    static ai.tegmentum.wasm34j.WasmValue toWasmValue(final ValueType type, final Object value) {
        if (!(value instanceof Number)) {
            throw new IllegalArgumentException(
                    "Expected a numeric value for " + type + " but got "
                            + (value == null ? "null" : value.getClass().getName()));
        }
        final Number n = (Number) value;
        switch (type) {
            case I32:
                return ai.tegmentum.wasm34j.WasmValue.i32(n.intValue());
            case I64:
                return ai.tegmentum.wasm34j.WasmValue.i64(n.longValue());
            case F32:
                return ai.tegmentum.wasm34j.WasmValue.f32(n.floatValue());
            case F64:
                return ai.tegmentum.wasm34j.WasmValue.f64(n.doubleValue());
            default:
                throw new IllegalArgumentException("Unsupported host value type: " + type);
        }
    }
}
