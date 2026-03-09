package ai.tegmentum.webassembly4j.runtime.marshal;

/**
 * Static utilities for Component Model Canonical ABI layout calculations.
 */
public final class CanonicalABI {

    private CanonicalABI() {
    }

    /**
     * Aligns an offset up to the given alignment boundary.
     *
     * @param offset the current offset
     * @param alignment the required alignment (must be a power of 2)
     * @return the aligned offset
     */
    public static int alignTo(int offset, int alignment) {
        return (offset + alignment - 1) & ~(alignment - 1);
    }

    /**
     * Computes the total size of a record with the given field layouts,
     * including padding for alignment.
     *
     * @param fields the field layouts in order
     * @return the total record size in bytes (padded to record alignment)
     */
    public static int recordSize(TypeLayout... fields) {
        if (fields.length == 0) {
            return 0;
        }
        int[] offsets = fieldOffsets(fields);
        TypeLayout lastField = fields[fields.length - 1];
        int endOfLastField = offsets[fields.length - 1] + lastField.size();
        int recordAlignment = recordAlignment(fields);
        return alignTo(endOfLastField, recordAlignment);
    }

    /**
     * Computes the byte offset of each field in a record.
     *
     * @param fields the field layouts in order
     * @return an array of byte offsets, one per field
     */
    public static int[] fieldOffsets(TypeLayout... fields) {
        int[] offsets = new int[fields.length];
        int offset = 0;
        for (int i = 0; i < fields.length; i++) {
            offset = alignTo(offset, fields[i].alignment());
            offsets[i] = offset;
            offset += fields[i].size();
        }
        return offsets;
    }

    /**
     * Computes the alignment of a record, which is the maximum alignment of its fields.
     *
     * @param fields the field layouts
     * @return the record alignment in bytes
     */
    public static int recordAlignment(TypeLayout... fields) {
        int maxAlign = 1;
        for (TypeLayout field : fields) {
            maxAlign = Math.max(maxAlign, field.alignment());
        }
        return maxAlign;
    }
}
