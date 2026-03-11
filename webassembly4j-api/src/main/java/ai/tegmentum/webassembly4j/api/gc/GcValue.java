package ai.tegmentum.webassembly4j.api.gc;

import java.util.Objects;

/**
 * A value that can be stored in or read from GC struct fields or array elements.
 *
 * <p>Wraps primitive values, references, and null.
 */
public final class GcValue {

    public enum Type {
        I32, I64, F32, F64, REFERENCE, NULL
    }

    private final Type type;
    private final long bits;
    private final GcObject reference;

    private GcValue(Type type, long bits, GcObject reference) {
        this.type = type;
        this.bits = bits;
        this.reference = reference;
    }

    public static GcValue i32(int value) {
        return new GcValue(Type.I32, value, null);
    }

    public static GcValue i64(long value) {
        return new GcValue(Type.I64, value, null);
    }

    public static GcValue f32(float value) {
        return new GcValue(Type.F32, Float.floatToRawIntBits(value), null);
    }

    public static GcValue f64(double value) {
        return new GcValue(Type.F64, Double.doubleToRawLongBits(value), null);
    }

    public static GcValue reference(GcObject obj) {
        Objects.requireNonNull(obj, "obj");
        return new GcValue(Type.REFERENCE, 0, obj);
    }

    public static GcValue nullValue() {
        return new GcValue(Type.NULL, 0, null);
    }

    public Type type() { return type; }

    public int asI32() {
        if (type != Type.I32) throw new GcException("Value is " + type + ", not I32");
        return (int) bits;
    }

    public long asI64() {
        if (type != Type.I64) throw new GcException("Value is " + type + ", not I64");
        return bits;
    }

    public float asF32() {
        if (type != Type.F32) throw new GcException("Value is " + type + ", not F32");
        return Float.intBitsToFloat((int) bits);
    }

    public double asF64() {
        if (type != Type.F64) throw new GcException("Value is " + type + ", not F64");
        return Double.longBitsToDouble(bits);
    }

    public GcObject asReference() {
        if (type != Type.REFERENCE) throw new GcException("Value is " + type + ", not REFERENCE");
        return reference;
    }

    public boolean isNull() {
        return type == Type.NULL;
    }

    public boolean isReference() {
        return type == Type.REFERENCE || type == Type.NULL;
    }

    @Override
    public String toString() {
        switch (type) {
            case I32: return "i32(" + (int) bits + ")";
            case I64: return "i64(" + bits + ")";
            case F32: return "f32(" + Float.intBitsToFloat((int) bits) + ")";
            case F64: return "f64(" + Double.longBitsToDouble(bits) + ")";
            case REFERENCE: return "ref(" + reference + ")";
            case NULL: return "null";
            default: return type.name();
        }
    }
}
