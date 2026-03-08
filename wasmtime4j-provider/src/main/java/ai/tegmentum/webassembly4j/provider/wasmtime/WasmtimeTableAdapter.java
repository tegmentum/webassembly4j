package ai.tegmentum.webassembly4j.provider.wasmtime;

import ai.tegmentum.wasmtime4j.WasmTable;
import ai.tegmentum.webassembly4j.api.Table;

import java.util.Optional;

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
    @SuppressWarnings("unchecked")
    public <T> Optional<T> unwrap(Class<T> nativeType) {
        if (nativeType.isInstance(nativeTable)) {
            return Optional.of((T) nativeTable);
        }
        return Optional.empty();
    }
}
