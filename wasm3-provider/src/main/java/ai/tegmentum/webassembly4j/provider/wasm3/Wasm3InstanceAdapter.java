package ai.tegmentum.webassembly4j.provider.wasm3;

import ai.tegmentum.wasm34j.WebAssemblyInstance;
import ai.tegmentum.webassembly4j.api.Function;
import ai.tegmentum.webassembly4j.api.Global;
import ai.tegmentum.webassembly4j.api.Instance;
import ai.tegmentum.webassembly4j.api.Memory;
import ai.tegmentum.webassembly4j.api.Table;

import java.util.Optional;

/** {@link Instance} backed by a wasm34j {@link WebAssemblyInstance}. */
final class Wasm3InstanceAdapter implements Instance {

    private final WebAssemblyInstance nativeInstance;

    Wasm3InstanceAdapter(final WebAssemblyInstance nativeInstance) {
        this.nativeInstance = nativeInstance;
    }

    @Override
    public Optional<Function> function(final String name) {
        return nativeInstance.findFunction(name).map(Wasm3FunctionAdapter::new);
    }

    @Override
    public Optional<Memory> memory(final String name) {
        return nativeInstance.findMemory(name).map(Wasm3MemoryAdapter::new);
    }

    @Override
    public Optional<Table> table(final String name) {
        // wasm3 does not expose tables through its public API.
        return Optional.empty();
    }

    @Override
    public Optional<Global> global(final String name) {
        return nativeInstance.findGlobal(name).map(Wasm3GlobalAdapter::new);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> Optional<T> unwrap(final Class<T> nativeType) {
        if (nativeType.isInstance(nativeInstance)) {
            return Optional.of((T) nativeInstance);
        }
        return Optional.empty();
    }
}
