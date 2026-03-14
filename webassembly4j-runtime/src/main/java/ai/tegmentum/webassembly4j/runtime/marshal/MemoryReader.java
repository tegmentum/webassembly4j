package ai.tegmentum.webassembly4j.runtime.marshal;

import ai.tegmentum.webassembly4j.api.Memory;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Reads typed values from WebAssembly linear memory.
 *
 * <p>Handles endianness conversion (WASM uses little-endian) and complex type
 * unmarshalling.
 *
 * <p>Uses a reusable scratch buffer to avoid per-call byte array and ByteBuffer
 * allocations in the hot path.
 */
public final class MemoryReader {

    private final Memory memory;
    private final ByteBuffer scratch;
    private final byte[] scratchArray;

    /**
     * Creates a new MemoryReader.
     *
     * @param memory the linear memory to read from
     */
    public MemoryReader(Memory memory) {
        this.memory = memory;
        this.scratchArray = new byte[8];
        this.scratch = ByteBuffer.wrap(scratchArray).order(ByteOrder.LITTLE_ENDIAN);
    }

    /**
     * Reads a boolean value from the given offset.
     *
     * @param offset the byte offset in linear memory
     * @return the boolean value
     */
    public boolean readBool(int offset) {
        memory.read(offset, 1, scratchArray, 0);
        return scratchArray[0] != 0;
    }

    /**
     * Reads a 32-bit integer from the given offset (little-endian).
     *
     * @param offset the byte offset in linear memory
     * @return the integer value
     */
    public int readI32(int offset) {
        memory.read(offset, 4, scratchArray, 0);
        return scratch.getInt(0);
    }

    /**
     * Reads a 64-bit integer from the given offset (little-endian).
     *
     * @param offset the byte offset in linear memory
     * @return the long value
     */
    public long readI64(int offset) {
        memory.read(offset, 8, scratchArray, 0);
        return scratch.getLong(0);
    }

    /**
     * Reads a 32-bit float from the given offset (little-endian).
     *
     * @param offset the byte offset in linear memory
     * @return the float value
     */
    public float readF32(int offset) {
        memory.read(offset, 4, scratchArray, 0);
        return scratch.getFloat(0);
    }

    /**
     * Reads a 64-bit float from the given offset (little-endian).
     *
     * @param offset the byte offset in linear memory
     * @return the double value
     */
    public double readF64(int offset) {
        memory.read(offset, 8, scratchArray, 0);
        return scratch.getDouble(0);
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
