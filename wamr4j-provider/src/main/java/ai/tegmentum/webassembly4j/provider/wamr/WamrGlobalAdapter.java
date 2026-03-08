package ai.tegmentum.webassembly4j.provider.wamr;

import ai.tegmentum.wamr4j.WebAssemblyInstance;
import ai.tegmentum.webassembly4j.api.Global;
import ai.tegmentum.webassembly4j.api.ValueType;
import ai.tegmentum.webassembly4j.api.exception.ExecutionException;

import java.util.Optional;

final class WamrGlobalAdapter implements Global {

    private final WebAssemblyInstance nativeInstance;
    private final String name;

    WamrGlobalAdapter(WebAssemblyInstance nativeInstance, String name) {
        this.nativeInstance = nativeInstance;
        this.name = name;
    }

    @Override
    public ValueType type() {
        Object value = get();
        if (value instanceof Integer) return ValueType.I32;
        if (value instanceof Long) return ValueType.I64;
        if (value instanceof Float) return ValueType.F32;
        if (value instanceof Double) return ValueType.F64;
        return ValueType.I32;
    }

    @Override
    public Object get() {
        try {
            return nativeInstance.getGlobal(name);
        } catch (ai.tegmentum.wamr4j.exception.WasmRuntimeException e) {
            throw new ExecutionException("Failed to get global '" + name + "'", e);
        }
    }

    @Override
    public void set(Object value) {
        try {
            nativeInstance.setGlobal(name, value);
        } catch (ai.tegmentum.wamr4j.exception.WasmRuntimeException e) {
            throw new ExecutionException("Failed to set global '" + name + "'", e);
        }
    }

    @Override
    public boolean mutable() {
        try {
            Object current = nativeInstance.getGlobal(name);
            nativeInstance.setGlobal(name, current);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> Optional<T> unwrap(Class<T> nativeType) {
        if (nativeType.isInstance(nativeInstance)) {
            return Optional.of((T) nativeInstance);
        }
        return Optional.empty();
    }
}
