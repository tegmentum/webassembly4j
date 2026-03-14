package ai.tegmentum.webassembly4j.runtime.marshal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Micro-benchmark for MemoryWriter/MemoryReader to measure the impact of
 * allocation optimizations. Not a JMH benchmark, but sufficient to detect
 * order-of-magnitude changes in the marshalling hot path.
 */
class MemoryWriterReaderBenchmarkTest {

    private static final int ITERATIONS = 2_000_000;
    private static final int WARMUP = 500_000;

    private StringCodecTest.TestMemory memory;
    private BumpAllocator allocator;
    private MemoryWriter writer;
    private MemoryReader reader;

    @BeforeEach
    void setUp() {
        memory = new StringCodecTest.TestMemory(65536);
        allocator = new BumpAllocator(256, 65536);
        writer = new MemoryWriter(memory, allocator);
        reader = new MemoryReader(memory);
    }

    @Test
    void benchmarkWriteI32() {
        // Warmup
        for (int i = 0; i < WARMUP; i++) {
            writer.writeI32(0, i);
        }

        long start = System.nanoTime();
        for (int i = 0; i < ITERATIONS; i++) {
            writer.writeI32(0, i);
        }
        long elapsed = System.nanoTime() - start;
        System.out.printf("writeI32: %d iterations in %d ms (%.1f ns/op)%n",
                ITERATIONS, elapsed / 1_000_000, (double) elapsed / ITERATIONS);
    }

    @Test
    void benchmarkWriteI64() {
        for (int i = 0; i < WARMUP; i++) {
            writer.writeI64(0, i);
        }

        long start = System.nanoTime();
        for (int i = 0; i < ITERATIONS; i++) {
            writer.writeI64(0, i);
        }
        long elapsed = System.nanoTime() - start;
        System.out.printf("writeI64: %d iterations in %d ms (%.1f ns/op)%n",
                ITERATIONS, elapsed / 1_000_000, (double) elapsed / ITERATIONS);
    }

    @Test
    void benchmarkWriteF64() {
        for (int i = 0; i < WARMUP; i++) {
            writer.writeF64(0, i * 0.1);
        }

        long start = System.nanoTime();
        for (int i = 0; i < ITERATIONS; i++) {
            writer.writeF64(0, i * 0.1);
        }
        long elapsed = System.nanoTime() - start;
        System.out.printf("writeF64: %d iterations in %d ms (%.1f ns/op)%n",
                ITERATIONS, elapsed / 1_000_000, (double) elapsed / ITERATIONS);
    }

    @Test
    void benchmarkReadI32() {
        writer.writeI32(0, 42);
        for (int i = 0; i < WARMUP; i++) {
            reader.readI32(0);
        }

        long start = System.nanoTime();
        int sum = 0;
        for (int i = 0; i < ITERATIONS; i++) {
            sum += reader.readI32(0);
        }
        long elapsed = System.nanoTime() - start;
        System.out.printf("readI32:  %d iterations in %d ms (%.1f ns/op) [sum=%d]%n",
                ITERATIONS, elapsed / 1_000_000, (double) elapsed / ITERATIONS, sum);
    }

    @Test
    void benchmarkReadI64() {
        writer.writeI64(0, 42L);
        for (int i = 0; i < WARMUP; i++) {
            reader.readI64(0);
        }

        long start = System.nanoTime();
        long sum = 0;
        for (int i = 0; i < ITERATIONS; i++) {
            sum += reader.readI64(0);
        }
        long elapsed = System.nanoTime() - start;
        System.out.printf("readI64:  %d iterations in %d ms (%.1f ns/op) [sum=%d]%n",
                ITERATIONS, elapsed / 1_000_000, (double) elapsed / ITERATIONS, sum);
    }

    @Test
    void benchmarkReadF64() {
        writer.writeF64(0, 3.14);
        for (int i = 0; i < WARMUP; i++) {
            reader.readF64(0);
        }

        long start = System.nanoTime();
        double sum = 0;
        for (int i = 0; i < ITERATIONS; i++) {
            sum += reader.readF64(0);
        }
        long elapsed = System.nanoTime() - start;
        System.out.printf("readF64:  %d iterations in %d ms (%.1f ns/op) [sum=%.1f]%n",
                ITERATIONS, elapsed / 1_000_000, (double) elapsed / ITERATIONS, sum);
    }

    @Test
    void benchmarkWriteBool() {
        for (int i = 0; i < WARMUP; i++) {
            writer.writeBool(0, i % 2 == 0);
        }

        long start = System.nanoTime();
        for (int i = 0; i < ITERATIONS; i++) {
            writer.writeBool(0, i % 2 == 0);
        }
        long elapsed = System.nanoTime() - start;
        System.out.printf("writeBool: %d iterations in %d ms (%.1f ns/op)%n",
                ITERATIONS, elapsed / 1_000_000, (double) elapsed / ITERATIONS);
    }

    @Test
    void benchmarkMixedWriteRead() {
        // Simulate a typical marshalling cycle: write params, read results
        for (int i = 0; i < WARMUP; i++) {
            writer.writeI32(0, i);
            writer.writeI32(4, i + 1);
            reader.readI32(0);
        }

        long start = System.nanoTime();
        int sum = 0;
        for (int i = 0; i < ITERATIONS; i++) {
            writer.writeI32(0, i);
            writer.writeI32(4, i + 1);
            sum += reader.readI32(0);
        }
        long elapsed = System.nanoTime() - start;
        System.out.printf("mixed(2xwriteI32+readI32): %d iterations in %d ms (%.1f ns/op) [sum=%d]%n",
                ITERATIONS, elapsed / 1_000_000, (double) elapsed / ITERATIONS, sum);
    }
}
