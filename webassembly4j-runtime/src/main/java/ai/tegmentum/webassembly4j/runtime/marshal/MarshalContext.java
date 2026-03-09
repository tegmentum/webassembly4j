package ai.tegmentum.webassembly4j.runtime.marshal;

import ai.tegmentum.webassembly4j.api.Instance;
import ai.tegmentum.webassembly4j.api.Memory;

/**
 * Bundles memory, allocator, writer and reader for marshalling complex types
 * to and from WebAssembly linear memory.
 */
public final class MarshalContext {

    private final Memory memory;
    private final MemoryAllocator allocator;
    private final MemoryWriter writer;
    private final MemoryReader reader;

    /**
     * Creates a new MarshalContext.
     *
     * @param memory the linear memory
     * @param allocator the memory allocator
     */
    public MarshalContext(Memory memory, MemoryAllocator allocator) {
        this.memory = memory;
        this.allocator = allocator;
        this.writer = new MemoryWriter(memory, allocator);
        this.reader = new MemoryReader(memory);
    }

    /**
     * Creates a MarshalContext from a WASM instance by resolving the default
     * memory export and building an appropriate allocator.
     *
     * @param instance the WASM instance
     * @return a new MarshalContext
     * @throws IllegalStateException if the instance does not export memory
     */
    public static MarshalContext fromInstance(Instance instance) {
        Memory memory = instance.memory("memory")
                .orElseThrow(() -> new IllegalStateException(
                        "WASM instance does not export 'memory'"));
        MemoryAllocator allocator = MemoryAllocators.fromInstance(instance);
        return new MarshalContext(memory, allocator);
    }

    /**
     * Returns the linear memory.
     *
     * @return the memory
     */
    public Memory memory() {
        return memory;
    }

    /**
     * Returns the memory allocator.
     *
     * @return the allocator
     */
    public MemoryAllocator allocator() {
        return allocator;
    }

    /**
     * Returns the memory writer.
     *
     * @return the writer
     */
    public MemoryWriter writer() {
        return writer;
    }

    /**
     * Returns the memory reader.
     *
     * @return the reader
     */
    public MemoryReader reader() {
        return reader;
    }
}
