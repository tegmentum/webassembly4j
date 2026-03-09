package ai.tegmentum.webassembly4j.runtime.marshal;

/**
 * Canonical ABI type layouts with size and alignment in bytes.
 *
 * <p>These correspond to the lowered representations of Component Model types
 * in linear memory.
 */
public enum TypeLayout {

    BOOL(1, 1),
    S8(1, 1),
    U8(1, 1),
    S16(2, 2),
    U16(2, 2),
    S32(4, 4),
    U32(4, 4),
    S64(8, 8),
    U64(8, 8),
    F32(4, 4),
    F64(8, 8),
    /** A string is stored as (i32 pointer, i32 byte_length). */
    STRING(8, 4),
    /** A list is stored as (i32 pointer, i32 element_count). */
    LIST(8, 4);

    private final int size;
    private final int alignment;

    TypeLayout(int size, int alignment) {
        this.size = size;
        this.alignment = alignment;
    }

    /**
     * Returns the size of this type in bytes.
     *
     * @return the size in bytes
     */
    public int size() {
        return size;
    }

    /**
     * Returns the alignment of this type in bytes.
     *
     * @return the alignment in bytes
     */
    public int alignment() {
        return alignment;
    }
}
