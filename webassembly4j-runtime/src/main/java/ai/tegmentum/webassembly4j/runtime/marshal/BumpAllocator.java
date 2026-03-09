package ai.tegmentum.webassembly4j.runtime.marshal;

/**
 * Simple bump allocator for WebAssembly linear memory.
 *
 * <p>Allocates memory by advancing a pointer through linear memory. Does not support
 * freeing individual allocations. Useful for testing and one-shot function calls where
 * memory reclamation is handled by discarding the entire instance.
 */
public final class BumpAllocator implements MemoryAllocator {

    private int offset;
    private final int limit;

    /**
     * Creates a bump allocator starting at the given offset.
     *
     * @param startOffset the starting offset in linear memory
     * @param limit the maximum offset (exclusive) that can be allocated
     */
    public BumpAllocator(int startOffset, int limit) {
        this.offset = startOffset;
        this.limit = limit;
    }

    @Override
    public int allocate(int size, int alignment) {
        int aligned = CanonicalABI.alignTo(offset, alignment);
        int end = aligned + size;
        if (end > limit) {
            throw new IllegalStateException(
                    "BumpAllocator out of memory: requested " + size
                            + " bytes at offset " + aligned + " but limit is " + limit);
        }
        offset = end;
        return aligned;
    }

    @Override
    public void free(int pointer, int size, int alignment) {
        // Bump allocator does not support freeing
    }

    /**
     * Returns the current allocation offset (high-water mark).
     *
     * @return the current offset
     */
    public int currentOffset() {
        return offset;
    }

    /**
     * Resets the allocator to the given offset, freeing all allocations past that point.
     *
     * @param newOffset the offset to reset to
     */
    public void reset(int newOffset) {
        this.offset = newOffset;
    }
}
