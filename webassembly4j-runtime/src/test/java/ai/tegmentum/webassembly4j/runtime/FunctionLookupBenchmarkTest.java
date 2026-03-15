package ai.tegmentum.webassembly4j.runtime;

import ai.tegmentum.webassembly4j.api.Engine;
import ai.tegmentum.webassembly4j.api.Function;
import ai.tegmentum.webassembly4j.api.Instance;
import ai.tegmentum.webassembly4j.api.WebAssembly;
import org.junit.jupiter.api.Test;

/**
 * Micro-benchmark for function lookup overhead.
 * Measures the cost of repeated instance.function() calls which
 * create new adapter objects vs reusing a cached reference.
 */
class FunctionLookupBenchmarkTest {

    private static final int ITERATIONS = 1_000_000;
    private static final int WARMUP = 200_000;

    // (func (export "add") (param i32 i32) (result i32) local.get 0 local.get 1 i32.add)
    private static final byte[] ADD_MODULE = new byte[] {
        0x00, 0x61, 0x73, 0x6D, 0x01, 0x00, 0x00, 0x00,
        0x01, 0x07, 0x01, 0x60, 0x02, 0x7F, 0x7F, 0x01, 0x7F,
        0x03, 0x02, 0x01, 0x00,
        0x07, 0x07, 0x01, 0x03, 0x61, 0x64, 0x64, 0x00, 0x00,
        0x0A, 0x09, 0x01, 0x07, 0x00, 0x20, 0x00, 0x20, 0x01, 0x6A, 0x0B
    };

    @Test
    void benchmarkRepeatedFunctionLookupAndInvoke() {
        try (Engine engine = WebAssembly.builder().build()) {
            ai.tegmentum.webassembly4j.api.Module module = engine.loadModule(ADD_MODULE);
            Instance instance = module.instantiate();

            // Warmup
            for (int i = 0; i < WARMUP; i++) {
                Function fn = instance.function("add").orElseThrow();
                fn.invoke(i, 1);
            }

            long start = System.nanoTime();
            int sum = 0;
            for (int i = 0; i < ITERATIONS; i++) {
                Function fn = instance.function("add").orElseThrow();
                sum += ((Number) fn.invoke(i, 1)).intValue();
            }
            long elapsed = System.nanoTime() - start;
            System.out.printf("lookup+invoke per call: %d iterations in %d ms (%.0f ns/op) [sum=%d]%n",
                    ITERATIONS, elapsed / 1_000_000, (double) elapsed / ITERATIONS, sum);

            module.close();
        }
    }

    @Test
    void benchmarkCachedFunctionInvoke() {
        try (Engine engine = WebAssembly.builder().build()) {
            ai.tegmentum.webassembly4j.api.Module module = engine.loadModule(ADD_MODULE);
            Instance instance = module.instantiate();
            Function fn = instance.function("add").orElseThrow();

            // Warmup
            for (int i = 0; i < WARMUP; i++) {
                fn.invoke(i, 1);
            }

            long start = System.nanoTime();
            int sum = 0;
            for (int i = 0; i < ITERATIONS; i++) {
                sum += ((Number) fn.invoke(i, 1)).intValue();
            }
            long elapsed = System.nanoTime() - start;
            System.out.printf("cached fn invoke:       %d iterations in %d ms (%.0f ns/op) [sum=%d]%n",
                    ITERATIONS, elapsed / 1_000_000, (double) elapsed / ITERATIONS, sum);

            module.close();
        }
    }
}
