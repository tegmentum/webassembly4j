package ai.tegmentum.webassembly4j.api;

import java.util.Objects;

/**
 * An {@link ExternImportDefinition} variant that wires a {@link Global}
 * instance as an imported global. The importing module must declare
 * {@code (import "<moduleName>" "<name>" (global ...))} in its Wasm binary.
 *
 * <p>The provided {@code global} must be {@link Global#unwrap(Class) unwrappable}
 * to the target provider's native global type. Cross-provider wiring is not
 * supported.
 *
 * @since 2.5.2
 */
public final class GlobalImport implements ExternImportDefinition {

    private final String moduleName;
    private final String name;
    private final Global global;

    public GlobalImport(String moduleName, String name, Global global) {
        this.moduleName = Objects.requireNonNull(moduleName, "moduleName");
        this.name = Objects.requireNonNull(name, "name");
        this.global = Objects.requireNonNull(global, "global");
    }

    @Override
    public String moduleName() {
        return moduleName;
    }

    @Override
    public String name() {
        return name;
    }

    public Global global() {
        return global;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof GlobalImport)) return false;
        GlobalImport other = (GlobalImport) o;
        return moduleName.equals(other.moduleName)
                && name.equals(other.name)
                && global.equals(other.global);
    }

    @Override
    public int hashCode() {
        return Objects.hash(moduleName, name, global);
    }

    @Override
    public String toString() {
        return "GlobalImport[" + moduleName + "::" + name + "]";
    }
}
