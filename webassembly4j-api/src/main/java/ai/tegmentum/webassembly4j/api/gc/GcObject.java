package ai.tegmentum.webassembly4j.api.gc;

/**
 * Base interface for all GC-managed objects (structs, arrays, i31 values).
 */
public interface GcObject {

    /**
     * Returns the reference type of this object.
     */
    GcReferenceType referenceType();

    /**
     * Returns true if this is a null reference.
     */
    boolean isNull();

    /**
     * Tests reference equality with another GC object ({@code ref.eq}).
     */
    boolean refEquals(GcObject other);
}
