package ai.tegmentum.webassembly4j.runtime.marshal;

import ai.tegmentum.webassembly4j.api.Memory;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies that optimized MemoryWriter/MemoryReader avoid per-call allocations
 * by counting write/read calls to the underlying Memory.
 */
class MemoryWriterAllocationTest {

    @Test
    void writeI32UsesSlicedWrite() {
        CountingMemory mem = new CountingMemory(4096);
        MemoryWriter writer = new MemoryWriter(mem, new BumpAllocator(0, 4096));

        writer.writeI32(0, 42);

        // Should use write(offset, bytes, srcOffset, length) — the sliced overload
        assertEquals(1, mem.slicedWriteCount.get(),
                "writeI32 should use sliced write to avoid byte[] allocation");
        assertEquals(0, mem.fullWriteCount.get(),
                "writeI32 should not use full-array write");
    }

    @Test
    void writeI64UsesSlicedWrite() {
        CountingMemory mem = new CountingMemory(4096);
        MemoryWriter writer = new MemoryWriter(mem, new BumpAllocator(0, 4096));

        writer.writeI64(0, 42L);

        assertEquals(1, mem.slicedWriteCount.get());
        assertEquals(0, mem.fullWriteCount.get());
    }

    @Test
    void writeBoolUsesStaticArray() {
        CountingMemory mem = new CountingMemory(4096);
        MemoryWriter writer = new MemoryWriter(mem, new BumpAllocator(0, 4096));

        writer.writeBool(0, true);
        writer.writeBool(1, false);

        // writeBool uses static TRUE_BYTE/FALSE_BYTE arrays via full write
        assertEquals(2, mem.fullWriteCount.get());
    }

    @Test
    void readI32UsesSlicedRead() {
        CountingMemory mem = new CountingMemory(4096);
        MemoryWriter writer = new MemoryWriter(mem, new BumpAllocator(0, 4096));
        writer.writeI32(0, 42);

        MemoryReader reader = new MemoryReader(mem);
        int value = reader.readI32(0);

        assertEquals(42, value);
        assertTrue(mem.slicedReadCount.get() > 0,
                "readI32 should use sliced read to avoid byte[] allocation");
    }

    @Test
    void multipleWritesReuseBuffer() {
        CountingMemory mem = new CountingMemory(4096);
        MemoryWriter writer = new MemoryWriter(mem, new BumpAllocator(0, 4096));

        for (int i = 0; i < 100; i++) {
            writer.writeI32(0, i);
        }

        assertEquals(100, mem.slicedWriteCount.get());
        assertEquals(0, mem.fullWriteCount.get());
    }

    /**
     * Memory implementation that counts different write/read method calls.
     */
    private static class CountingMemory implements Memory {
        private final byte[] data;
        final AtomicInteger fullWriteCount = new AtomicInteger();
        final AtomicInteger slicedWriteCount = new AtomicInteger();
        final AtomicInteger slicedReadCount = new AtomicInteger();

        CountingMemory(int size) {
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
            fullWriteCount.incrementAndGet();
            System.arraycopy(bytes, 0, data, (int) offset, bytes.length);
        }

        @Override
        public void write(long offset, byte[] bytes, int srcOffset, int length) {
            slicedWriteCount.incrementAndGet();
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
            slicedReadCount.incrementAndGet();
            System.arraycopy(data, (int) offset, dest, destOffset, length);
        }

        @Override
        public <T> Optional<T> unwrap(Class<T> nativeType) {
            return Optional.empty();
        }
    }
}
