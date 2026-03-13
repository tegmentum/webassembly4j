package ai.tegmentum.webassembly4j.api.gc;

import java.util.Objects;

/**
 * Describes the type of a field in a GC struct or the element type of an array.
 *
 * <p>Supports value types (i32, i64, f32, f64), packed types (i8, i16),
 * and reference types (anyref, eqref, structref, etc.).
 */
public record GcFieldType(Kind kind, GcReferenceType referenceType, boolean nullable) {

    /**
     * The storage kind for a field.
     */
    public enum Kind {
        I32, I64, F32, F64, V128,
        PACKED_I8, PACKED_I16,
        REFERENCE
    }

    public static GcFieldType i32() { return new GcFieldType(Kind.I32, null, false); }
    public static GcFieldType i64() { return new GcFieldType(Kind.I64, null, false); }
    public static GcFieldType f32() { return new GcFieldType(Kind.F32, null, false); }
    public static GcFieldType f64() { return new GcFieldType(Kind.F64, null, false); }
    public static GcFieldType v128() { return new GcFieldType(Kind.V128, null, false); }
    public static GcFieldType packedI8() { return new GcFieldType(Kind.PACKED_I8, null, false); }
    public static GcFieldType packedI16() { return new GcFieldType(Kind.PACKED_I16, null, false); }

    public static GcFieldType reference(GcReferenceType refType, boolean nullable) {
        Objects.requireNonNull(refType, "refType");
        return new GcFieldType(Kind.REFERENCE, refType, nullable);
    }

    public static GcFieldType anyRef() { return reference(GcReferenceType.ANY_REF, true); }
    public static GcFieldType eqRef() { return reference(GcReferenceType.EQ_REF, true); }
    public static GcFieldType i31Ref() { return reference(GcReferenceType.I31_REF, true); }
    public static GcFieldType structRef() { return reference(GcReferenceType.STRUCT_REF, true); }
    public static GcFieldType arrayRef() { return reference(GcReferenceType.ARRAY_REF, true); }

    public boolean isReference() { return kind == Kind.REFERENCE; }
    public boolean isPacked() { return kind == Kind.PACKED_I8 || kind == Kind.PACKED_I16; }
    public boolean isNullable() { return nullable; }

    /**
     * Returns the size in bytes for this field type's storage.
     */
    public int sizeBytes() {
        return switch (kind) {
            case PACKED_I8 -> 1;
            case PACKED_I16 -> 2;
            case I32, F32, REFERENCE -> 4;
            case I64, F64 -> 8;
            case V128 -> 16;
        };
    }

    @Override
    public String toString() {
        if (isReference()) {
            return (nullable ? "(ref null " : "(ref ") + referenceType + ")";
        }
        return kind.name().toLowerCase();
    }
}
