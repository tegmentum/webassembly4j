package ai.tegmentum.webassembly4j.runtime.marshal;

import ai.tegmentum.webassembly4j.api.Function;
import ai.tegmentum.webassembly4j.api.Instance;
import ai.tegmentum.webassembly4j.api.Memory;

import java.util.Optional;

/**
 * Factory for creating {@link MemoryAllocator} instances.
 *
 * <p>Provides automatic discovery from a WASM instance as well as explicit constructors.
 */
public final class MemoryAllocators {

    private MemoryAllocators() {
    }

    /**
     * Creates an allocator from a WASM instance by probing for known allocation exports.
     *
     * <p>Discovery order:
     * <ol>
     *   <li>{@code cabi_realloc} — Component Model Canonical ABI</li>
     *   <li>Bump allocator using the exported {@code memory}</li>
     * </ol>
     *
     * @param instance the WASM instance
     * @return an allocator for the instance
     * @throws IllegalStateException if no allocator can be created
     */
    public static MemoryAllocator fromInstance(Instance instance) {
        Optional<Function> cabiRealloc = instance.function("cabi_realloc");
        if (cabiRealloc.isPresent()) {
            return cabiRealloc(cabiRealloc.get());
        }

        Optional<Memory> memory = instance.memory("memory");
        if (memory.isPresent()) {
            long memSize = memory.get().byteSize();
            // Start bump allocator at the end of existing data, leave room for stack
            // Use half the memory as a conservative starting point
            int startOffset = (int) (memSize / 2);
            return bump(startOffset, (int) memSize);
        }

        throw new IllegalStateException(
                "Cannot create allocator: instance exports neither cabi_realloc nor memory");
    }

    /**
     * Creates an allocator backed by the {@code cabi_realloc} function.
     *
     * @param cabiRealloc the cabi_realloc function
     * @return a CabiRealloc-based allocator
     */
    public static MemoryAllocator cabiRealloc(Function cabiRealloc) {
        return new CabiReallocAllocator(cabiRealloc);
    }

    /**
     * Creates a bump allocator for linear memory.
     *
     * @param startOffset the starting offset for allocations
     * @param limit the maximum offset (exclusive)
     * @return a bump allocator
     */
    public static BumpAllocator bump(int startOffset, int limit) {
        return new BumpAllocator(startOffset, limit);
    }
}
