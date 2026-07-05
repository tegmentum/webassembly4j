package ai.tegmentum.webassembly4j.api;

import java.util.Objects;

/**
 * A WIT-typed host function to be linked into a component's imports.
 *
 * <p>Immutable value. Register via {@link
 * DefaultLinkingContext.Builder#addWitHostFunction(String, WitHostFunction)} or the
 * two-arg overload that takes a pre-built definition.
 *
 * <p>The {@code witPath} uses the standard WIT import path form —
 * {@code "package:name/interface#function"} for an interface-scoped import (e.g.
 * {@code "stardog:webfunction/mapping-dictionary#get"}) or {@code "#function"} for
 * a root-level import — matching the format the native component linker expects.
 */
public final class WitHostFunctionDefinition {

    private final String witPath;
    private final WitHostFunction function;

    public WitHostFunctionDefinition(final String witPath, final WitHostFunction function) {
        this.witPath = Objects.requireNonNull(witPath, "witPath");
        this.function = Objects.requireNonNull(function, "function");
    }

    public String witPath() {
        return witPath;
    }

    public WitHostFunction function() {
        return function;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (!(o instanceof WitHostFunctionDefinition)) return false;
        final WitHostFunctionDefinition other = (WitHostFunctionDefinition) o;
        return witPath.equals(other.witPath) && function.equals(other.function);
    }

    @Override
    public int hashCode() {
        return Objects.hash(witPath, function);
    }

    @Override
    public String toString() {
        return "WitHostFunctionDefinition{witPath='" + witPath + "'}";
    }
}
