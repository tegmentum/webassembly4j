package ai.tegmentum.webassembly4j.provider.graalwasm;

import ai.tegmentum.webassembly4j.api.Table;
import org.graalvm.polyglot.Value;

import java.util.Optional;

final class GraalWasmTableAdapter implements Table {

    private final Value nativeTable;

    GraalWasmTableAdapter(Value nativeTable) {
        this.nativeTable = nativeTable;
    }

    @Override
    public int size() {
        return (int) nativeTable.getArraySize();
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
