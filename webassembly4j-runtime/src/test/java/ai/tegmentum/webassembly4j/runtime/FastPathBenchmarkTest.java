package ai.tegmentum.webassembly4j.runtime;

import ai.tegmentum.webassembly4j.api.Engine;
import ai.tegmentum.webassembly4j.api.Function;
import ai.tegmentum.webassembly4j.api.Instance;
import ai.tegmentum.webassembly4j.api.WebAssembly;
import org.junit.jupiter.api.Test;

/**
 * Benchmarks for function signatures that hit vs miss the Chicory FastPath.
 */
class FastPathBenchmarkTest {

    private static final int ITERATIONS = 2_000_000;
    private static final int WARMUP = 500_000;

    // (func (export "add") (param i32 i32) (result i32) local.get 0 local.get 1 i32.add)
    // Signature: II_I — covered by FastPath
    private static final byte[] ADD_MODULE = new byte[] {
        0x00, 0x61, 0x73, 0x6D, 0x01, 0x00, 0x00, 0x00,
        0x01, 0x07, 0x01, 0x60, 0x02, 0x7F, 0x7F, 0x01, 0x7F,
        0x03, 0x02, 0x01, 0x00,
        0x07, 0x07, 0x01, 0x03, 0x61, 0x64, 0x64, 0x00, 0x00,
        0x0A, 0x09, 0x01, 0x07, 0x00, 0x20, 0x00, 0x20, 0x01, 0x6A, 0x0B
    };

    // (func (export "store2") (param i32 i32))
    // Signature: II_V — NOT covered by FastPath, hits generic
    private static final byte[] STORE2_MODULE = new byte[] {
        0x00, 0x61, 0x73, 0x6d, 0x01, 0x00, 0x00, 0x00, 0x01, 0x06, 0x01, 0x60,
        0x02, 0x7f, 0x7f, 0x00, 0x03, 0x02, 0x01, 0x00, 0x07, 0x0a, 0x01, 0x06,
        0x73, 0x74, 0x6f, 0x72, 0x65, 0x32, 0x00, 0x00, 0x0a, 0x04, 0x01, 0x02,
        0x00, 0x0b
    };

    // (func (export "noop"))
    // Signature: V_V — covered by FastPath
    private static final byte[] NOOP_MODULE = new byte[] {
        0x00, 0x61, 0x73, 0x6D, 0x01, 0x00, 0x00, 0x00,
        0x01, 0x04, 0x01, 0x60, 0x00, 0x00,
        0x03, 0x02, 0x01, 0x00,
        0x07, 0x08, 0x01, 0x04, 0x6E, 0x6F, 0x6F, 0x70, 0x00, 0x00,
        0x0A, 0x04, 0x01, 0x02, 0x00, 0x0B
    };

    @Test
    void benchmarkFastPathII_I() {
        try (Engine engine = WebAssembly.builder().build()) {
            ai.tegmentum.webassembly4j.api.Module module = engine.loadModule(ADD_MODULE);
            Instance instance = module.instantiate();
            Function fn = instance.function("add").orElseThrow();

            for (int i = 0; i < WARMUP; i++) fn.invoke(i, 1);

            long start = System.nanoTime();
            int sum = 0;
            for (int i = 0; i < ITERATIONS; i++) {
                sum += ((Number) fn.invoke(i, 1)).intValue();
            }
            long elapsed = System.nanoTime() - start;
            System.out.printf("FastPath  II_I (add):  %.0f ns/op [sum=%d]%n",
                    (double) elapsed / ITERATIONS, sum);
            module.close();
        }
    }

    @Test
    void benchmarkGenericII_V() {
        try (Engine engine = WebAssembly.builder().build()) {
            ai.tegmentum.webassembly4j.api.Module module = engine.loadModule(STORE2_MODULE);
            Instance instance = module.instantiate();
            Function fn = instance.function("store2").orElseThrow();

            for (int i = 0; i < WARMUP; i++) fn.invoke(i, 1);

            long start = System.nanoTime();
            for (int i = 0; i < ITERATIONS; i++) {
                fn.invoke(i, 1);
            }
            long elapsed = System.nanoTime() - start;
            System.out.printf("II_V   (store2):       %.0f ns/op%n",
                    (double) elapsed / ITERATIONS);
            module.close();
        }
    }

    @Test
    void benchmarkFastPathV_V() {
        try (Engine engine = WebAssembly.builder().build()) {
            ai.tegmentum.webassembly4j.api.Module module = engine.loadModule(NOOP_MODULE);
            Instance instance = module.instantiate();
            Function fn = instance.function("noop").orElseThrow();

            for (int i = 0; i < WARMUP; i++) fn.invoke();

            long start = System.nanoTime();
            for (int i = 0; i < ITERATIONS; i++) {
                fn.invoke();
            }
            long elapsed = System.nanoTime() - start;
            System.out.printf("FastPath  V_V  (noop): %.0f ns/op%n",
                    (double) elapsed / ITERATIONS);
            module.close();
        }
    }
}
