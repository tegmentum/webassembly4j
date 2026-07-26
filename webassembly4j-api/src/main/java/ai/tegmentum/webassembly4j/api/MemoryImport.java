package ai.tegmentum.webassembly4j.api;

import java.util.Objects;

/**
 * An {@link ExternImportDefinition} variant that wires a {@link Memory}
 * instance as an imported linear memory. The importing module must declare
 * {@code (import "<moduleName>" "<name>" (memory ...))} in its Wasm binary.
 *
 * <p>The provided {@code memory} must be
 * {@link Memory#unwrap(Class) unwrappable} to the target provider's native
 * memory type. Cross-provider wiring is not supported: a memory produced by
 * one provider cannot in general be handed to another provider's linker.
 *
 * @since 2.5.2
 */
public final class MemoryImport implements ExternImportDefinition {

    private final String moduleName;
    private final String name;
    private final Memory memory;

    public MemoryImport(String moduleName, String name, Memory memory) {
        this.moduleName = Objects.requireNonNull(moduleName, "moduleName");
        this.name = Objects.requireNonNull(name, "name");
        this.memory = Objects.requireNonNull(memory, "memory");
    }

    @Override
    public String moduleName() {
        return moduleName;
    }

    @Override
    public String name() {
        return name;
    }

    public Memory memory() {
        return memory;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MemoryImport)) return false;
        MemoryImport other = (MemoryImport) o;
        return moduleName.equals(other.moduleName)
                && name.equals(other.name)
                && memory.equals(other.memory);
    }

    @Override
    public int hashCode() {
        return Objects.hash(moduleName, name, memory);
    }

    @Override
    public String toString() {
        return "MemoryImport[" + moduleName + "::" + name + "]";
    }
}
