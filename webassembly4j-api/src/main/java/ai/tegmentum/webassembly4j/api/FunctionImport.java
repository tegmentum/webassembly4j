package ai.tegmentum.webassembly4j.api;

import java.util.Objects;

/**
 * An {@link ExternImportDefinition} variant that wires a {@link Function}
 * instance as an imported function. The importing module must declare
 * {@code (import "<moduleName>" "<name>" (func ...))} in its Wasm binary.
 *
 * <p><b>Provider support</b>: as of {@code 2.5.2} the wasmtime4j-provider does
 * not implement this variant and will throw
 * {@link UnsupportedOperationException} at
 * {@link Module#instantiate(LinkingContext) instantiate} time. Wiring
 * requires an {@code unwrap(Class)} method on {@link Function}, which is
 * scheduled for a follow-up charter. For today's function-import needs use
 * {@link DefaultLinkingContext.Builder#addHostFunction(HostFunctionDefinition)
 * addHostFunction} instead.
 *
 * @since 2.5.2
 */
public final class FunctionImport implements ExternImportDefinition {

    private final String moduleName;
    private final String name;
    private final Function function;

    public FunctionImport(String moduleName, String name, Function function) {
        this.moduleName = Objects.requireNonNull(moduleName, "moduleName");
        this.name = Objects.requireNonNull(name, "name");
        this.function = Objects.requireNonNull(function, "function");
    }

    @Override
    public String moduleName() {
        return moduleName;
    }

    @Override
    public String name() {
        return name;
    }

    public Function function() {
        return function;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof FunctionImport)) return false;
        FunctionImport other = (FunctionImport) o;
        return moduleName.equals(other.moduleName)
                && name.equals(other.name)
                && function.equals(other.function);
    }

    @Override
    public int hashCode() {
        return Objects.hash(moduleName, name, function);
    }

    @Override
    public String toString() {
        return "FunctionImport[" + moduleName + "::" + name + "]";
    }
}
