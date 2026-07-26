package ai.tegmentum.webassembly4j.provider.wasmtime;

import ai.tegmentum.wasmtime4j.WasmTable;
import ai.tegmentum.webassembly4j.api.Table;

import java.util.Optional;
import java.util.OptionalInt;

final class WasmtimeTableAdapter implements Table {

    private final WasmTable nativeTable;

    WasmtimeTableAdapter(WasmTable nativeTable) {
        this.nativeTable = nativeTable;
    }

    @Override
    public int size() {
        return nativeTable.getSize();
    }

    @Override
    public OptionalInt maxSize() {
        // ai.tegmentum.wasmtime4j.WasmTable#getMaxSize Javadoc: "returns -1
        // if unlimited". Treat any negative as unbounded to be defensive
        // against future extensions of the sentinel space.
        int max = nativeTable.getMaxSize();
        return max < 0 ? OptionalInt.empty() : OptionalInt.of(max);
    }

    @Override
    public Object get(int index) {
        return nativeTable.get(index);
    }

    @Override
    public void set(int index, Object value) {
        nativeTable.set(index, value);
    }

    @Override
    public int grow(int delta, Object init) {
        return nativeTable.grow(delta, init);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> Optional<T> unwrap(Class<T> nativeType) {
        if (nativeType.isInstance(nativeTable)) {
            return Optional.of((T) nativeTable);
        }
        return Optional.empty();
    }
}
