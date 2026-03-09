package ai.tegmentum.webassembly4j.runtime.marshal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MemoryWriterReaderTest {

    private StringCodecTest.TestMemory memory;
    private BumpAllocator allocator;
    private MemoryWriter writer;
    private MemoryReader reader;

    @BeforeEach
    void setUp() {
        memory = new StringCodecTest.TestMemory(4096);
        // Reserve first 256 bytes for structured data, allocations start at 256
        allocator = new BumpAllocator(256, 4096);
        writer = new MemoryWriter(memory, allocator);
        reader = new MemoryReader(memory);
    }

    @Test
    void roundTripBool() {
        writer.writeBool(0, true);
        assertTrue(reader.readBool(0));

        writer.writeBool(1, false);
        assertFalse(reader.readBool(1));
    }

    @Test
    void roundTripI32() {
        writer.writeI32(0, 42);
        assertEquals(42, reader.readI32(0));

        writer.writeI32(4, -1);
        assertEquals(-1, reader.readI32(4));

        writer.writeI32(8, Integer.MAX_VALUE);
        assertEquals(Integer.MAX_VALUE, reader.readI32(8));

        writer.writeI32(12, Integer.MIN_VALUE);
        assertEquals(Integer.MIN_VALUE, reader.readI32(12));
    }

    @Test
    void roundTripI64() {
        writer.writeI64(0, 123456789L);
        assertEquals(123456789L, reader.readI64(0));

        writer.writeI64(8, Long.MAX_VALUE);
        assertEquals(Long.MAX_VALUE, reader.readI64(8));

        writer.writeI64(16, Long.MIN_VALUE);
        assertEquals(Long.MIN_VALUE, reader.readI64(16));
    }

    @Test
    void roundTripF32() {
        writer.writeF32(0, 3.14f);
        assertEquals(3.14f, reader.readF32(0));

        writer.writeF32(4, Float.MAX_VALUE);
        assertEquals(Float.MAX_VALUE, reader.readF32(4));

        writer.writeF32(8, Float.MIN_VALUE);
        assertEquals(Float.MIN_VALUE, reader.readF32(8));
    }

    @Test
    void roundTripF64() {
        writer.writeF64(0, 3.141592653589793);
        assertEquals(3.141592653589793, reader.readF64(0));

        writer.writeF64(8, Double.MAX_VALUE);
        assertEquals(Double.MAX_VALUE, reader.readF64(8));
    }

    @Test
    void roundTripString() {
        writer.writeString(0, "hello");
        String result = reader.readString(0);
        assertEquals("hello", result);
    }

    @Test
    void roundTripStringUtf8() {
        writer.writeString(0, "\u00e9l\u00e8ve");
        assertEquals("\u00e9l\u00e8ve", reader.readString(0));
    }

    @Test
    void roundTripBytes() {
        byte[] input = {1, 2, 3, 4, 5};
        writer.writeBytes(0, input);
        byte[] output = reader.readBytes(0);
        assertArrayEquals(input, output);
    }

    @Test
    void roundTripEmptyBytes() {
        byte[] input = {};
        writer.writeBytes(0, input);
        byte[] output = reader.readBytes(0);
        assertArrayEquals(input, output);
    }

    @Test
    void multipleStringFields() {
        // Simulate a record with two string fields at offsets 0 and 8
        writer.writeString(0, "first");
        writer.writeString(8, "second");

        assertEquals("first", reader.readString(0));
        assertEquals("second", reader.readString(8));
    }

    @Test
    void mixedFields() {
        // Simulate: i32 at 0, string at 4, f64 at 12
        writer.writeI32(0, 42);
        writer.writeString(4, "test");
        writer.writeF64(12, 2.718);

        assertEquals(42, reader.readI32(0));
        assertEquals("test", reader.readString(4));
        assertEquals(2.718, reader.readF64(12));
    }
}
