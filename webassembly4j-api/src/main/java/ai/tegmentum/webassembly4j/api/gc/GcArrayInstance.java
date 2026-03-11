package ai.tegmentum.webassembly4j.api.gc;

/**
 * A live instance of a GC array, backed by the runtime's managed heap.
 *
 * <p>Elements can be read and (if the array type is mutable) written by index.
 */
public interface GcArrayInstance extends GcObject {

    /**
     * Returns the type of this array instance.
     */
    GcArrayType type();

    /**
     * Returns the number of elements.
     */
    int length();

    /**
     * Reads the value of an element by index.
     *
     * @param index the zero-based element index
     * @return the element value
     * @throws GcException if the index is out of bounds
     */
    GcValue getElement(int index);

    /**
     * Writes a value to an element by index.
     *
     * @param index the zero-based element index
     * @param value the value to write
     * @throws GcException if the index is out of bounds, the array is immutable,
     *                     or the value type is incompatible
     */
    void setElement(int index, GcValue value);

    @Override
    default GcReferenceType referenceType() {
        return GcReferenceType.ARRAY_REF;
    }
}
