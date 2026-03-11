package ai.tegmentum.webassembly4j.api.gc;

/**
 * An i31ref value — a 31-bit integer stored as a GC reference without heap allocation.
 *
 * <p>i31ref values are immediate: they pack a signed 31-bit integer into the reference
 * itself, avoiding a heap allocation. Valid range is [-2^30, 2^30 - 1].
 */
public interface GcI31Instance extends GcObject {

    /**
     * Returns the signed 31-bit value.
     */
    int value();

    /**
     * Returns the value interpreted as unsigned (0 to 2^31 - 1).
     */
    int unsignedValue();

    @Override
    default GcReferenceType referenceType() {
        return GcReferenceType.I31_REF;
    }

    @Override
    default boolean isNull() {
        return false;
    }
}
