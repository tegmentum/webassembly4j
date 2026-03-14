package ai.tegmentum.webassembly4j.runtime.marshal;

import ai.tegmentum.webassembly4j.api.Memory;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Writes typed values to WebAssembly linear memory.
 *
 * <p>Handles endianness conversion (WASM uses little-endian) and complex type
 * marshalling through the provided {@link MemoryAllocator}.
 *
 * <p>Uses a reusable scratch buffer to avoid per-call byte array and ByteBuffer
 * allocations in the hot path.
 */
public final class MemoryWriter {

    private static final byte[] TRUE_BYTE = {1};
    private static final byte[] FALSE_BYTE = {0};

    private final Memory memory;
    private final MemoryAllocator allocator;
    private final ByteBuffer scratch;
    private final byte[] scratchArray;

    /**
     * Creates a new MemoryWriter.
     *
     * @param memory the linear memory to write to
     * @param allocator the allocator for complex types that need additional memory
     */
    public MemoryWriter(Memory memory, MemoryAllocator allocator) {
        this.memory = memory;
        this.allocator = allocator;
        this.scratchArray = new byte[8];
        this.scratch = ByteBuffer.wrap(scratchArray).order(ByteOrder.LITTLE_ENDIAN);
    }

    /**
     * Writes a boolean value at the given offset.
     *
     * @param offset the byte offset in linear memory
     * @param value the boolean value
     */
    public void writeBool(int offset, boolean value) {
        memory.write(offset, value ? TRUE_BYTE : FALSE_BYTE);
    }

    /**
     * Writes a 32-bit integer at the given offset (little-endian).
     *
     * @param offset the byte offset in linear memory
     * @param value the integer value
     */
    public void writeI32(int offset, int value) {
        scratch.putInt(0, value);
        memory.write(offset, scratchArray, 0, 4);
    }

    /**
     * Writes a 64-bit integer at the given offset (little-endian).
     *
     * @param offset the byte offset in linear memory
     * @param value the long value
     */
    public void writeI64(int offset, long value) {
        scratch.putLong(0, value);
        memory.write(offset, scratchArray, 0, 8);
    }

    /**
     * Writes a 32-bit float at the given offset (little-endian).
     *
     * @param offset the byte offset in linear memory
     * @param value the float value
     */
    public void writeF32(int offset, float value) {
        scratch.putFloat(0, value);
        memory.write(offset, scratchArray, 0, 4);
    }

    /**
     * Writes a 64-bit float at the given offset (little-endian).
     *
     * @param offset the byte offset in linear memory
     * @param value the double value
     */
    public void writeF64(int offset, double value) {
        scratch.putDouble(0, value);
        memory.write(offset, scratchArray, 0, 8);
    }

    /**
     * Writes a string at the given offset as a (pointer, byte_length) pair.
     *
     * <p>The string data is UTF-8 encoded and allocated separately in linear memory.
     * The (pointer, byte_length) descriptor is written at the given offset.
     *
     * @param offset the byte offset where the (ptr, len) pair is written
     * @param value the string to write
     */
    public void writeString(int offset, String value) {
        int[] encoded = StringCodec.encode(value, memory, allocator);
        writeI32(offset, encoded[0]);
        writeI32(offset + 4, encoded[1]);
    }

    /**
     * Writes a byte array at the given offset as a (pointer, byte_length) pair.
     *
     * <p>The byte data is allocated separately in linear memory. The (pointer, byte_length)
     * descriptor is written at the given offset.
     *
     * @param offset the byte offset where the (ptr, len) pair is written
     * @param value the byte array to write
     */
    public void writeBytes(int offset, byte[] value) {
        int ptr = allocator.allocate(value.length, 1);
        memory.write(ptr, value);
        writeI32(offset, ptr);
        writeI32(offset + 4, value.length);
    }
}
