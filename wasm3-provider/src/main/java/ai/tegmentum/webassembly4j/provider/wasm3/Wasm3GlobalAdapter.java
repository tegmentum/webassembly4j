package ai.tegmentum.webassembly4j.provider.wasm3;

import ai.tegmentum.wasm34j.WasmGlobal;
import ai.tegmentum.webassembly4j.api.Global;
import ai.tegmentum.webassembly4j.api.ValueType;
import ai.tegmentum.webassembly4j.api.exception.ExecutionException;

import java.util.Optional;

/** {@link Global} backed by a wasm34j {@link WasmGlobal}. */
final class Wasm3GlobalAdapter implements Global {

    private final WasmGlobal nativeGlobal;

    Wasm3GlobalAdapter(final WasmGlobal nativeGlobal) {
        this.nativeGlobal = nativeGlobal;
    }

    @Override
    public ValueType type() {
        return Wasm3Types.toApi(nativeGlobal.type());
    }

    @Override
    public Object get() {
        return nativeGlobal.get().boxed();
    }

    @Override
    public void set(final Object value) {
        try {
            nativeGlobal.set(Wasm3Types.toWasmValue(type(), value));
        } catch (final ai.tegmentum.wasm34j.exception.WasmException e) {
            throw new ExecutionException("Failed to set global", e);
        }
    }

    @Override
    public boolean mutable() {
        return nativeGlobal.mutable();
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> Optional<T> unwrap(final Class<T> nativeType) {
        if (nativeType.isInstance(nativeGlobal)) {
            return Optional.of((T) nativeGlobal);
        }
        return Optional.empty();
    }
}
