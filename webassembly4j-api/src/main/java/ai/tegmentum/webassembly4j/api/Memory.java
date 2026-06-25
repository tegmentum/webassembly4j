package ai.tegmentum.webassembly4j.api;

import java.nio.ByteBuffer;
import java.util.Optional;

public interface Memory {

    /** WebAssembly linear-memory page size in bytes (64 KiB). */
    long PAGE_SIZE = 65536;

    long byteSize();

    /**
     * Returns the current size of this memory in WebAssembly pages (64 KiB each).
     *
     * @return the current page count
     */
    default long pageCount() {
        return byteSize() / PAGE_SIZE;
    }

    /**
     * Returns the maximum number of pages this memory may grow to, or {@code -1}
     * if the memory has no declared maximum or the provider cannot report one.
     *
     * @return the maximum page count, or {@code -1} if unlimited/unknown
     */
    default long maxPageCount() {
        return -1;
    }

    /**
     * Grows this memory by the given number of 64 KiB pages.
     *
     * @param pages the number of pages to add
     * @return the previous size in pages, or {@code -1} if the growth failed
     *     (e.g. it would exceed the declared maximum)
     * @throws UnsupportedOperationException if the provider cannot grow memory
     */
    default long grow(long pages) {
        throw new UnsupportedOperationException(
                "This provider does not support growing linear memory");
    }

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
