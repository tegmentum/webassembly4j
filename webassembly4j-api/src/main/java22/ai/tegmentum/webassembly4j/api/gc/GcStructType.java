package ai.tegmentum.webassembly4j.api.gc;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Describes a WebAssembly GC struct type with named, typed fields.
 *
 * <p>Example:
 * <pre>
 * GcStructType point = GcStructType.builder("Point")
 *     .addField("x", GcFieldType.f64(), true)
 *     .addField("y", GcFieldType.f64(), true)
 *     .build();
 * </pre>
 */
public final class GcStructType {

    /**
     * A field within a struct type.
     */
    public record Field(String name, GcFieldType type, boolean mutable) {

        public Field {
            Objects.requireNonNull(type, "type");
        }

        @Override
        public String toString() {
            return (name != null ? name : "<unnamed>") + ": " + type + (mutable ? " (mut)" : "");
        }
    }

    private final String name;
    private final List<Field> fields;
    private final Finality finality;
    private final GcStructType supertype;

    private GcStructType(String name, List<Field> fields, Finality finality, GcStructType supertype) {
        this.name = name;
        this.fields = Collections.unmodifiableList(new ArrayList<>(fields));
        this.finality = finality;
        this.supertype = supertype;
    }

    public String name() { return name; }
    public List<Field> fields() { return fields; }
    public int fieldCount() { return fields.size(); }
    public Field field(int index) { return fields.get(index); }
    public Finality finality() { return finality; }
    public GcStructType supertype() { return supertype; }

    /**
     * Returns true if this type is a subtype of the given type.
     */
    public boolean isSubtypeOf(GcStructType other) {
        GcStructType current = this;
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
        private final List<Field> fields = new ArrayList<>();
        private Finality finality = Finality.FINAL;
        private GcStructType supertype;

        private Builder(String name) {
            this.name = name;
        }

        public Builder addField(String fieldName, GcFieldType type, boolean mutable) {
            fields.add(new Field(fieldName, type, mutable));
            return this;
        }

        public Builder addField(GcFieldType type, boolean mutable) {
            fields.add(new Field(null, type, mutable));
            return this;
        }

        public Builder finality(Finality finality) {
            this.finality = Objects.requireNonNull(finality, "finality");
            return this;
        }

        public Builder extend(GcStructType supertype) {
            this.supertype = supertype;
            return this;
        }

        public GcStructType build() {
            if (fields.isEmpty()) {
                throw new IllegalStateException("Struct type must have at least one field");
            }
            if (supertype != null && !supertype.finality().allowsSubtyping()) {
                throw new IllegalStateException("Cannot extend final struct type: " + supertype.name());
            }
            return new GcStructType(name, fields, finality, supertype);
        }
    }

    @Override
    public String toString() {
        return "struct " + name + " (" + fields.size() + " fields)";
    }
}
