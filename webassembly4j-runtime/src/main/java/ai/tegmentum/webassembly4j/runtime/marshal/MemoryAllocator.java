package ai.tegmentum.webassembly4j.runtime.marshal;

/**
 * Allocates and frees memory within WebAssembly linear memory.
 */
public interface MemoryAllocator {

    /**
     * Allocates a block of memory with the given size and alignment.
     *
     * @param size the number of bytes to allocate
     * @param alignment the required alignment in bytes (must be a power of 2)
     * @return the offset in linear memory where the block was allocated
     */
    int allocate(int size, int alignment);

    /**
     * Frees a previously allocated block of memory.
     *
     * @param pointer the offset of the block to free
     * @param size the size of the block in bytes
     * @param alignment the alignment of the block
     */
    void free(int pointer, int size, int alignment);
}
