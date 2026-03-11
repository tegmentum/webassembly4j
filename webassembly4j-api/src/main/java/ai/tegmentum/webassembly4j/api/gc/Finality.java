package ai.tegmentum.webassembly4j.api.gc;

/**
 * Controls whether a GC type can be subtyped.
 */
public enum Finality {
    /** The type cannot be subtyped. */
    FINAL,
    /** The type can be subtyped. */
    NON_FINAL;

    public boolean allowsSubtyping() {
        return this == NON_FINAL;
    }
}
