package ai.tegmentum.webassembly4j.provider.wamr;

import ai.tegmentum.wamr4j.WebAssemblyTable;
import ai.tegmentum.webassembly4j.api.Table;

import java.util.Optional;

final class WamrTableAdapter implements Table {

    private final WebAssemblyTable nativeTable;

    WamrTableAdapter(WebAssemblyTable nativeTable) {
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
