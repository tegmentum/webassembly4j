package ai.tegmentum.webassembly4j.runtime;

import org.junit.jupiter.api.Test;

/**
 * Micro-benchmark for proxy creation overhead.
 * Measures the cost of repeatedly creating proxies for the same interface,
 * as in the compile-once-run-many pattern.
 */
class ProxyCreationBenchmarkTest {

    private static final int ITERATIONS = 100_000;
    private static final int WARMUP = 50_000;

    // (func (export "add") (param i32 i32) (result i32) local.get 0 local.get 1 i32.add)
    private static final byte[] ADD_MODULE = new byte[] {
        0x00, 0x61, 0x73, 0x6D, 0x01, 0x00, 0x00, 0x00,
        0x01, 0x07, 0x01, 0x60, 0x02, 0x7F, 0x7F, 0x01, 0x7F,
        0x03, 0x02, 0x01, 0x00,
        0x07, 0x07, 0x01, 0x03, 0x61, 0x64, 0x64, 0x00, 0x00,
        0x0A, 0x09, 0x01, 0x07, 0x00, 0x20, 0x00, 0x20, 0x01, 0x6A, 0x0B
    };

    interface Adder extends AutoCloseable {
        int add(int a, int b);
    }

    @Test
    void benchmarkRepeatedProxyCreation() throws Exception {
        WasmModule module = WasmRuntime.compile(ADD_MODULE);

        // Warmup
        for (int i = 0; i < WARMUP; i++) {
            try (Adder adder = module.bind(Adder.class)) {
                adder.add(1, 2);
            }
        }

        long start = System.nanoTime();
        int sum = 0;
        for (int i = 0; i < ITERATIONS; i++) {
            try (Adder adder = module.bind(Adder.class)) {
                sum += adder.add(i, 1);
            }
        }
        long elapsed = System.nanoTime() - start;
        System.out.printf("bind+invoke+close: %d iterations in %d ms (%.0f ns/op) [sum=%d]%n",
                ITERATIONS, elapsed / 1_000_000, (double) elapsed / ITERATIONS, sum);

        module.close();
    }
}
