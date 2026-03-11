package ai.tegmentum.webassembly4j.provider.wasmtime;

import ai.tegmentum.webassembly4j.api.ComponentInstance;
import ai.tegmentum.webassembly4j.api.Function;
import ai.tegmentum.webassembly4j.api.Global;
import ai.tegmentum.webassembly4j.api.Memory;
import ai.tegmentum.webassembly4j.api.Table;
import ai.tegmentum.webassembly4j.api.exception.ExecutionException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;

final class WasmtimeComponentInstanceAdapter implements ComponentInstance {

    private final ai.tegmentum.wasmtime4j.component.ComponentInstance nativeInstance;

    WasmtimeComponentInstanceAdapter(
            ai.tegmentum.wasmtime4j.component.ComponentInstance nativeInstance) {
        this.nativeInstance = nativeInstance;
    }

    @Override
    public Object invoke(String functionName, Object... args) {
        try {
            return nativeInstance.invoke(functionName, args);
        } catch (ai.tegmentum.wasmtime4j.exception.WasmException e) {
            throw new ExecutionException(
                    "Failed to invoke component function: " + functionName, e);
        }
    }

    @Override
    public boolean hasFunction(String name) {
        return nativeInstance.hasFunction(name);
    }

    @Override
    public List<String> exportedFunctions() {
        Set<String> functions = nativeInstance.getExportedFunctions();
        return Collections.unmodifiableList(new ArrayList<>(functions));
    }

    @Override
    public List<String> exportedInterfaces() {
        // ComponentInstance doesn't directly expose interface names;
        // delegate to the parent component
        ai.tegmentum.wasmtime4j.component.Component component = nativeInstance.getComponent();
        try {
            Set<String> interfaces = component.getExportedInterfaces();
            return Collections.unmodifiableList(new ArrayList<>(interfaces));
        } catch (ai.tegmentum.wasmtime4j.exception.WasmException e) {
            return Collections.emptyList();
        }
    }

    @Override
    public boolean exportsInterface(String name) {
        ai.tegmentum.wasmtime4j.component.Component component = nativeInstance.getComponent();
        try {
            return component.exportsInterface(name);
        } catch (ai.tegmentum.wasmtime4j.exception.WasmException e) {
            return false;
        }
    }

    @Override
    public Optional<Function> function(String name) {
        // Component instances don't expose core module functions directly
        return Optional.empty();
    }

    @Override
    public Optional<Memory> memory(String name) {
        return Optional.empty();
    }

    @Override
    public Optional<Table> table(String name) {
        return Optional.empty();
    }

    @Override
    public Optional<Global> global(String name) {
        return Optional.empty();
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
