package ai.tegmentum.webassembly4j.api;

import java.util.Objects;

/**
 * An {@link ExternImportDefinition} variant that wires a {@link Table}
 * instance as an imported table. The importing module must declare
 * {@code (import "<moduleName>" "<name>" (table ...))} in its Wasm binary.
 *
 * <p>The provided {@code table} must be {@link Table#unwrap(Class) unwrappable}
 * to the target provider's native table type. Cross-provider wiring is not
 * supported.
 *
 * @since 2.5.2
 */
public final class TableImport implements ExternImportDefinition {

    private final String moduleName;
    private final String name;
    private final Table table;

    public TableImport(String moduleName, String name, Table table) {
        this.moduleName = Objects.requireNonNull(moduleName, "moduleName");
        this.name = Objects.requireNonNull(name, "name");
        this.table = Objects.requireNonNull(table, "table");
    }

    @Override
    public String moduleName() {
        return moduleName;
    }

    @Override
    public String name() {
        return name;
    }

    public Table table() {
        return table;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TableImport)) return false;
        TableImport other = (TableImport) o;
        return moduleName.equals(other.moduleName)
                && name.equals(other.name)
                && table.equals(other.table);
    }

    @Override
    public int hashCode() {
        return Objects.hash(moduleName, name, table);
    }

    @Override
    public String toString() {
        return "TableImport[" + moduleName + "::" + name + "]";
    }
}
