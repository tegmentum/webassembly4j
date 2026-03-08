package ai.tegmentum.webassembly4j.provider.graalwasm;

import ai.tegmentum.webassembly4j.api.Global;
import ai.tegmentum.webassembly4j.api.ValueType;
import org.graalvm.polyglot.Value;

import java.util.Optional;

final class GraalWasmGlobalAdapter implements Global {

    private final Value nativeGlobal;

    GraalWasmGlobalAdapter(Value nativeGlobal) {
        this.nativeGlobal = nativeGlobal;
    }

    @Override
    public ValueType type() {
        if (nativeGlobal.fitsInInt()) {
            return ValueType.I32;
        }
        if (nativeGlobal.fitsInLong()) {
            return ValueType.I64;
        }
        if (nativeGlobal.fitsInFloat()) {
            return ValueType.F32;
        }
        if (nativeGlobal.fitsInDouble()) {
            return ValueType.F64;
        }
        return ValueType.I32;
    }

    @Override
    public Object get() {
        if (nativeGlobal.fitsInInt()) {
            return nativeGlobal.asInt();
        }
        if (nativeGlobal.fitsInLong()) {
            return nativeGlobal.asLong();
        }
        if (nativeGlobal.fitsInFloat()) {
            return nativeGlobal.asFloat();
        }
        if (nativeGlobal.fitsInDouble()) {
            return nativeGlobal.asDouble();
        }
        return nativeGlobal.asInt();
    }

    @Override
    public void set(Object value) {
        throw new UnsupportedOperationException(
                "GraalWasm Polyglot API does not support setting globals directly");
    }

    @Override
    public boolean mutable() {
        return false;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> Optional<T> unwrap(Class<T> nativeType) {
        if (nativeType.isInstance(nativeGlobal)) {
            return Optional.of((T) nativeGlobal);
        }
        return Optional.empty();
    }
}
