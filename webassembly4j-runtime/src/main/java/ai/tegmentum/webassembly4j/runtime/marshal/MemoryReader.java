package ai.tegmentum.webassembly4j.runtime.marshal;

import ai.tegmentum.webassembly4j.api.Memory;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Reads typed values from WebAssembly linear memory.
 *
 * <p>Handles endianness conversion (WASM uses little-endian) and complex type
 * unmarshalling.
 */
public final class MemoryReader {

    private final Memory memory;

    /**
     * Creates a new MemoryReader.
     *
     * @param memory the linear memory to read from
     */
    public MemoryReader(Memory memory) {
        this.memory = memory;
    }

    /**
     * Reads a boolean value from the given offset.
     *
     * @param offset the byte offset in linear memory
     * @return the boolean value
     */
    public boolean readBool(int offset) {
        byte[] bytes = memory.read(offset, 1);
        return bytes[0] != 0;
    }

    /**
     * Reads a 32-bit integer from the given offset (little-endian).
     *
     * @param offset the byte offset in linear memory
     * @return the integer value
     */
    public int readI32(int offset) {
        byte[] bytes = memory.read(offset, 4);
        return ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).getInt();
    }

    /**
     * Reads a 64-bit integer from the given offset (little-endian).
     *
     * @param offset the byte offset in linear memory
     * @return the long value
     */
    public long readI64(int offset) {
        byte[] bytes = memory.read(offset, 8);
        return ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).getLong();
    }

    /**
     * Reads a 32-bit float from the given offset (little-endian).
     *
     * @param offset the byte offset in linear memory
     * @return the float value
     */
    public float readF32(int offset) {
        byte[] bytes = memory.read(offset, 4);
        return ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).getFloat();
    }

    /**
     * Reads a 64-bit float from the given offset (little-endian).
     *
     * @param offset the byte offset in linear memory
     * @return the double value
     */
    public double readF64(int offset) {
        byte[] bytes = memory.read(offset, 8);
        return ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).getDouble();
    }

    /**
     * Reads a string from the given offset.
     *
     * <p>Expects a (pointer, byte_length) pair at the offset, then reads UTF-8
     * data from the pointed-to location.
     *
     * @param offset the byte offset of the (ptr, len) pair
     * @return the decoded string
     */
    public String readString(int offset) {
        int ptr = readI32(offset);
        int len = readI32(offset + 4);
        return StringCodec.decode(memory, ptr, len);
    }

    /**
     * Reads a byte array from the given offset.
     *
     * <p>Expects a (pointer, byte_length) pair at the offset, then reads the
     * byte data from the pointed-to location.
     *
     * @param offset the byte offset of the (ptr, len) pair
     * @return the byte array
     */
    public byte[] readBytes(int offset) {
        int ptr = readI32(offset);
        int len = readI32(offset + 4);
        return memory.read(ptr, len);
    }
}
