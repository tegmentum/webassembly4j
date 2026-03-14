package ai.tegmentum.webassembly4j.api;

import java.nio.ByteBuffer;
import java.util.Optional;

public interface Memory {

    long byteSize();

    ByteBuffer asByteBuffer();

    void write(long offset, byte[] bytes);

    /**
     * Writes a region of the source array to linear memory at the given offset.
     *
     * @param offset the byte offset in linear memory
     * @param bytes the source byte array
     * @param srcOffset the starting offset in the source array
     * @param length the number of bytes to write
     */
    default void write(long offset, byte[] bytes, int srcOffset, int length) {
        if (srcOffset == 0 && length == bytes.length) {
            write(offset, bytes);
        } else {
            byte[] slice = new byte[length];
            System.arraycopy(bytes, srcOffset, slice, 0, length);
            write(offset, slice);
        }
    }

    byte[] read(long offset, int length);

    /**
     * Reads bytes from linear memory into an existing destination array.
     *
     * @param offset the byte offset in linear memory
     * @param length the number of bytes to read
     * @param dest the destination byte array
     * @param destOffset the starting offset in the destination array
     */
    default void read(long offset, int length, byte[] dest, int destOffset) {
        byte[] data = read(offset, length);
        System.arraycopy(data, 0, dest, destOffset, length);
    }

    <T> Optional<T> unwrap(Class<T> nativeType);
}
