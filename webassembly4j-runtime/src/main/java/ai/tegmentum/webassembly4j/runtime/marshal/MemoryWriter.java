package ai.tegmentum.webassembly4j.runtime.marshal;

import ai.tegmentum.webassembly4j.api.Memory;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Writes typed values to WebAssembly linear memory.
 *
 * <p>Handles endianness conversion (WASM uses little-endian) and complex type
 * marshalling through the provided {@link MemoryAllocator}.
 */
public final class MemoryWriter {

    private final Memory memory;
    private final MemoryAllocator allocator;

    /**
     * Creates a new MemoryWriter.
     *
     * @param memory the linear memory to write to
     * @param allocator the allocator for complex types that need additional memory
     */
    public MemoryWriter(Memory memory, MemoryAllocator allocator) {
        this.memory = memory;
        this.allocator = allocator;
    }

    /**
     * Writes a boolean value at the given offset.
     *
     * @param offset the byte offset in linear memory
     * @param value the boolean value
     */
    public void writeBool(int offset, boolean value) {
        memory.write(offset, new byte[]{(byte) (value ? 1 : 0)});
    }

    /**
     * Writes a 32-bit integer at the given offset (little-endian).
     *
     * @param offset the byte offset in linear memory
     * @param value the integer value
     */
    public void writeI32(int offset, int value) {
        byte[] bytes = new byte[4];
        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).putInt(value);
        memory.write(offset, bytes);
    }

    /**
     * Writes a 64-bit integer at the given offset (little-endian).
     *
     * @param offset the byte offset in linear memory
     * @param value the long value
     */
    public void writeI64(int offset, long value) {
        byte[] bytes = new byte[8];
        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).putLong(value);
        memory.write(offset, bytes);
    }

    /**
     * Writes a 32-bit float at the given offset (little-endian).
     *
     * @param offset the byte offset in linear memory
     * @param value the float value
     */
    public void writeF32(int offset, float value) {
        byte[] bytes = new byte[4];
        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).putFloat(value);
        memory.write(offset, bytes);
    }

    /**
     * Writes a 64-bit float at the given offset (little-endian).
     *
     * @param offset the byte offset in linear memory
     * @param value the double value
     */
    public void writeF64(int offset, double value) {
        byte[] bytes = new byte[8];
        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).putDouble(value);
        memory.write(offset, bytes);
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
