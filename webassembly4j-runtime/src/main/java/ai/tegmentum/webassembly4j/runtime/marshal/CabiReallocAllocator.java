package ai.tegmentum.webassembly4j.runtime.marshal;

import ai.tegmentum.webassembly4j.api.Function;

/**
 * Allocator backed by the WASM-exported {@code cabi_realloc} function.
 *
 * <p>This follows the Component Model Canonical ABI convention where the guest exports
 * {@code cabi_realloc(old_ptr: i32, old_size: i32, align: i32, new_size: i32) -> i32}.
 *
 * <p>The Canonical ABI does not define a free operation, so {@link #free} is a no-op.
 */
final class CabiReallocAllocator implements MemoryAllocator {

    private final Function cabiRealloc;

    CabiReallocAllocator(Function cabiRealloc) {
        this.cabiRealloc = cabiRealloc;
    }

    @Override
    public int allocate(int size, int alignment) {
        Object result = cabiRealloc.invoke(0, 0, alignment, size);
        return ((Number) result).intValue();
    }

    @Override
    public void free(int pointer, int size, int alignment) {
        // Canonical ABI does not define a free operation
    }
}
