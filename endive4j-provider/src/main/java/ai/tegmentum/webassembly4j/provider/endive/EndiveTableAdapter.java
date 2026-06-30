package ai.tegmentum.webassembly4j.provider.endive;

import ai.tegmentum.webassembly4j.api.Table;
import run.endive.runtime.TableInstance;

import java.util.Optional;

final class EndiveTableAdapter implements Table {

    private final TableInstance nativeTable;

    EndiveTableAdapter(TableInstance nativeTable) {
        this.nativeTable = nativeTable;
    }

    @Override
    public int size() {
        return nativeTable.size();
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
