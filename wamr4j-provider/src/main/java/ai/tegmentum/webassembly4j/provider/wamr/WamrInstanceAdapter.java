package ai.tegmentum.webassembly4j.provider.wamr;

import ai.tegmentum.wamr4j.WebAssemblyInstance;
import ai.tegmentum.webassembly4j.api.Function;
import ai.tegmentum.webassembly4j.api.Global;
import ai.tegmentum.webassembly4j.api.Instance;
import ai.tegmentum.webassembly4j.api.Memory;
import ai.tegmentum.webassembly4j.api.Table;

import java.util.Optional;

final class WamrInstanceAdapter implements Instance {

    private final WebAssemblyInstance nativeInstance;

    WamrInstanceAdapter(WebAssemblyInstance nativeInstance) {
        this.nativeInstance = nativeInstance;
    }

    @Override
    public Optional<Function> function(String name) {
        try {
            return Optional.of(new WamrFunctionAdapter(nativeInstance.getFunction(name)));
        } catch (ai.tegmentum.wamr4j.exception.WasmRuntimeException e) {
            return Optional.empty();
        }
    }

    @Override
    public Optional<Memory> memory(String name) {
        try {
            if (nativeInstance.hasMemory()) {
                return Optional.of(new WamrMemoryAdapter(nativeInstance.getMemory()));
            }
            return Optional.empty();
        } catch (ai.tegmentum.wamr4j.exception.WasmRuntimeException e) {
            return Optional.empty();
        }
    }

    @Override
    public Optional<Table> table(String name) {
        try {
            return Optional.of(new WamrTableAdapter(nativeInstance.getTable(name)));
        } catch (ai.tegmentum.wamr4j.exception.WasmRuntimeException e) {
            return Optional.empty();
        }
    }

    @Override
    public Optional<Global> global(String name) {
        try {
            nativeInstance.getGlobal(name);
            return Optional.of(new WamrGlobalAdapter(nativeInstance, name));
        } catch (ai.tegmentum.wamr4j.exception.WasmRuntimeException e) {
            return Optional.empty();
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
