package ai.tegmentum.webassembly4j.provider.wasmtime;

import ai.tegmentum.webassembly4j.api.Function;
import ai.tegmentum.webassembly4j.api.Global;
import ai.tegmentum.webassembly4j.api.Instance;
import ai.tegmentum.webassembly4j.api.Memory;
import ai.tegmentum.webassembly4j.api.Table;

import java.util.Optional;

final class WasmtimeInstanceAdapter implements Instance {

    private final ai.tegmentum.wasmtime4j.Instance nativeInstance;

    WasmtimeInstanceAdapter(ai.tegmentum.wasmtime4j.Instance nativeInstance) {
        this.nativeInstance = nativeInstance;
    }

    @Override
    public Optional<Function> function(String name) {
        return nativeInstance.getFunction(name)
                .map(WasmtimeFunctionAdapter::new);
    }

    @Override
    public Optional<Memory> memory(String name) {
        return nativeInstance.getMemory(name)
                .map(WasmtimeMemoryAdapter::new);
    }

    @Override
    public Optional<Table> table(String name) {
        return nativeInstance.getTable(name)
                .map(WasmtimeTableAdapter::new);
    }

    @Override
    public Optional<Global> global(String name) {
        return nativeInstance.getGlobal(name)
                .map(WasmtimeGlobalAdapter::new);
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
