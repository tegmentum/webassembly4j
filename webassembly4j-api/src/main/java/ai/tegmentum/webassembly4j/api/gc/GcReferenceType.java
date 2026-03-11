package ai.tegmentum.webassembly4j.api.gc;

/**
 * WebAssembly GC reference type hierarchy.
 *
 * <p>The subtyping hierarchy is:
 * <pre>
 *   ANY_REF (top)
 *     └─ EQ_REF
 *         ├─ I31_REF
 *         ├─ STRUCT_REF
 *         └─ ARRAY_REF
 * </pre>
 */
public enum GcReferenceType {
    ANY_REF,
    EQ_REF,
    I31_REF,
    STRUCT_REF,
    ARRAY_REF;

    /**
     * Returns true if this type is a subtype of the given type.
     */
    public boolean isSubtypeOf(GcReferenceType other) {
        if (this == other) {
            return true;
        }
        switch (this) {
            case EQ_REF:
                return other == ANY_REF;
            case I31_REF:
            case STRUCT_REF:
            case ARRAY_REF:
                return other == EQ_REF || other == ANY_REF;
            default:
                return false;
        }
    }

    /**
     * Returns true if references of this type support {@code ref.eq}.
     */
    public boolean supportsEquality() {
        return this != ANY_REF;
    }
}
