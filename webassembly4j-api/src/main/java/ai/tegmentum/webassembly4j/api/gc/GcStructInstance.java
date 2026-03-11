package ai.tegmentum.webassembly4j.api.gc;

/**
 * A live instance of a GC struct, backed by the runtime's managed heap.
 *
 * <p>Fields can be read and (if mutable) written by index.
 */
public interface GcStructInstance extends GcObject {

    /**
     * Returns the type of this struct instance.
     */
    GcStructType type();

    /**
     * Returns the number of fields.
     */
    int fieldCount();

    /**
     * Reads the value of a field by index.
     *
     * @param index the zero-based field index
     * @return the field value
     * @throws GcException if the index is out of bounds
     */
    GcValue getField(int index);

    /**
     * Writes a value to a mutable field by index.
     *
     * @param index the zero-based field index
     * @param value the value to write
     * @throws GcException if the index is out of bounds, the field is immutable,
     *                     or the value type is incompatible
     */
    void setField(int index, GcValue value);

    @Override
    default GcReferenceType referenceType() {
        return GcReferenceType.STRUCT_REF;
    }
}
