package ai.tegmentum.webassembly4j.api.gc;

import java.util.Objects;

/**
 * Describes a WebAssembly GC array type with a single element type.
 *
 * <p>Example:
 * <pre>
 * GcArrayType intArray = GcArrayType.builder("IntArray")
 *     .elementType(GcFieldType.i32())
 *     .mutable(true)
 *     .build();
 * </pre>
 */
public final class GcArrayType {

    private final String name;
    private final GcFieldType elementType;
    private final boolean mutable;
    private final Finality finality;
    private final GcArrayType supertype;

    private GcArrayType(String name, GcFieldType elementType, boolean mutable,
                        Finality finality, GcArrayType supertype) {
        this.name = name;
        this.elementType = elementType;
        this.mutable = mutable;
        this.finality = finality;
        this.supertype = supertype;
    }

    public String name() { return name; }
    public GcFieldType elementType() { return elementType; }
    public boolean isMutable() { return mutable; }
    public Finality finality() { return finality; }
    public GcArrayType supertype() { return supertype; }

    /**
     * Returns true if this type is a subtype of the given type.
     */
    public boolean isSubtypeOf(GcArrayType other) {
        GcArrayType current = this;
        while (current != null) {
            if (current.equals(other)) return true;
            current = current.supertype;
        }
        return false;
    }

    public static Builder builder(String name) {
        return new Builder(Objects.requireNonNull(name, "name"));
    }

    public static final class Builder {
        private final String name;
        private GcFieldType elementType;
        private boolean mutable;
        private Finality finality = Finality.FINAL;
        private GcArrayType supertype;

        private Builder(String name) {
            this.name = name;
        }

        public Builder elementType(GcFieldType elementType) {
            this.elementType = Objects.requireNonNull(elementType, "elementType");
            return this;
        }

        public Builder mutable(boolean mutable) {
            this.mutable = mutable;
            return this;
        }

        public Builder finality(Finality finality) {
            this.finality = Objects.requireNonNull(finality, "finality");
            return this;
        }

        public Builder extend(GcArrayType supertype) {
            this.supertype = supertype;
            return this;
        }

        public GcArrayType build() {
            if (elementType == null) {
                throw new IllegalStateException("Array type must have an element type");
            }
            if (supertype != null && !supertype.finality().allowsSubtyping()) {
                throw new IllegalStateException("Cannot extend final array type: " + supertype.name());
            }
            return new GcArrayType(name, elementType, mutable, finality, supertype);
        }
    }

    @Override
    public String toString() {
        return "array " + name + " (" + elementType + (mutable ? ", mut" : "") + ")";
    }
}
