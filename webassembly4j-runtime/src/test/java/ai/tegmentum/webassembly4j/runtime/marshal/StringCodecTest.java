package ai.tegmentum.webassembly4j.runtime.marshal;

import ai.tegmentum.webassembly4j.api.Memory;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class StringCodecTest {

    @Test
    void roundTripAscii() {
        TestMemory mem = new TestMemory(1024);
        BumpAllocator alloc = new BumpAllocator(0, 1024);

        String input = "Hello, World!";
        int[] encoded = StringCodec.encode(input, mem, alloc);

        assertEquals(0, encoded[0]); // ptr
        assertEquals(13, encoded[1]); // len

        String decoded = StringCodec.decode(mem, encoded[0], encoded[1]);
        assertEquals(input, decoded);
    }

    @Test
    void roundTripUtf8() {
        TestMemory mem = new TestMemory(1024);
        BumpAllocator alloc = new BumpAllocator(0, 1024);

        String input = "\u00e9\u00e8\u00ea"; // French accented chars
        int[] encoded = StringCodec.encode(input, mem, alloc);

        String decoded = StringCodec.decode(mem, encoded[0], encoded[1]);
        assertEquals(input, decoded);
    }

    @Test
    void roundTripEmoji() {
        TestMemory mem = new TestMemory(1024);
        BumpAllocator alloc = new BumpAllocator(0, 1024);

        String input = "\ud83d\ude00\ud83d\ude01\ud83d\ude02"; // Grinning faces
        int[] encoded = StringCodec.encode(input, mem, alloc);

        String decoded = StringCodec.decode(mem, encoded[0], encoded[1]);
        assertEquals(input, decoded);
    }

    @Test
    void emptyString() {
        TestMemory mem = new TestMemory(1024);
        BumpAllocator alloc = new BumpAllocator(0, 1024);

        int[] encoded = StringCodec.encode("", mem, alloc);
        assertEquals(0, encoded[1]); // zero length

        String decoded = StringCodec.decode(mem, encoded[0], encoded[1]);
        assertEquals("", decoded);
    }

    @Test
    void encodeLengthMatchesUtf8() {
        TestMemory mem = new TestMemory(1024);
        BumpAllocator alloc = new BumpAllocator(0, 1024);

        String input = "\u00fc\u00f6\u00e4"; // German umlauts
        int[] encoded = StringCodec.encode(input, mem, alloc);

        byte[] expected = input.getBytes(StandardCharsets.UTF_8);
        assertEquals(expected.length, encoded[1]);

        byte[] actual = mem.read(encoded[0], encoded[1]);
        assertArrayEquals(expected, actual);
    }

    /**
     * Simple in-memory implementation of Memory for testing.
     */
    static class TestMemory implements Memory {

        private final byte[] data;

        TestMemory(int size) {
            this.data = new byte[size];
        }

        @Override
        public long byteSize() {
            return data.length;
        }

        @Override
        public ByteBuffer asByteBuffer() {
            return ByteBuffer.wrap(data);
        }

        @Override
        public void write(long offset, byte[] bytes) {
            System.arraycopy(bytes, 0, data, (int) offset, bytes.length);
        }

        @Override
        public void write(long offset, byte[] bytes, int srcOffset, int length) {
            System.arraycopy(bytes, srcOffset, data, (int) offset, length);
        }

        @Override
        public byte[] read(long offset, int length) {
            byte[] result = new byte[length];
            System.arraycopy(data, (int) offset, result, 0, length);
            return result;
        }

        @Override
        public void read(long offset, int length, byte[] dest, int destOffset) {
            System.arraycopy(data, (int) offset, dest, destOffset, length);
        }

        @Override
        public <T> Optional<T> unwrap(Class<T> nativeType) {
            return Optional.empty();
        }
    }
}
