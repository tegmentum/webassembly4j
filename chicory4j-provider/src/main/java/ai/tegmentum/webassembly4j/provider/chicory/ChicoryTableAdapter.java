package ai.tegmentum.webassembly4j.provider.chicory;

import ai.tegmentum.webassembly4j.api.Table;
import com.dylibso.chicory.runtime.TableInstance;

import java.util.Optional;

final class ChicoryTableAdapter implements Table {

    private final TableInstance nativeTable;

    ChicoryTableAdapter(TableInstance nativeTable) {
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
