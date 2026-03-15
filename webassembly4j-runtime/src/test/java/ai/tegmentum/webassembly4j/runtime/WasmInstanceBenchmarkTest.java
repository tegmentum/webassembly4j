package ai.tegmentum.webassembly4j.runtime;

import org.junit.jupiter.api.Test;

/**
 * Benchmarks for WasmInstance.call() vs direct Function.invoke().
 * Measures the per-call overhead of string-based function lookup
 * in the compile-once-run-many pattern.
 */
class WasmInstanceBenchmarkTest {

    private static final int ITERATIONS = 2_000_000;
    private static final int WARMUP = 500_000;

    // (func (export "add") (param i32 i32) (result i32) local.get 0 local.get 1 i32.add)
    private static final byte[] ADD_MODULE = new byte[] {
        0x00, 0x61, 0x73, 0x6D, 0x01, 0x00, 0x00, 0x00,
        0x01, 0x07, 0x01, 0x60, 0x02, 0x7F, 0x7F, 0x01, 0x7F,
        0x03, 0x02, 0x01, 0x00,
        0x07, 0x07, 0x01, 0x03, 0x61, 0x64, 0x64, 0x00, 0x00,
        0x0A, 0x09, 0x01, 0x07, 0x00, 0x20, 0x00, 0x20, 0x01, 0x6A, 0x0B
    };

    @Test
    void benchmarkWasmInstanceCall() {
        WasmModule module = WasmRuntime.compile(ADD_MODULE);
        WasmInstance instance = module.newInstance();

        for (int i = 0; i < WARMUP; i++) {
            instance.call("add", int.class, i, 1);
        }

        long start = System.nanoTime();
        int sum = 0;
        for (int i = 0; i < ITERATIONS; i++) {
            sum += instance.call("add", int.class, i, 1);
        }
        long elapsed = System.nanoTime() - start;
        System.out.printf("WasmInstance.call(\"add\"): %.0f ns/op [sum=%d]%n",
                (double) elapsed / ITERATIONS, sum);

        instance.close();
        module.close();
    }

    @Test
    void benchmarkWasmModuleCall() {
        WasmModule module = WasmRuntime.compile(ADD_MODULE);

        for (int i = 0; i < WARMUP; i++) {
            module.call("add", int.class, i, 1);
        }

        long start = System.nanoTime();
        int sum = 0;
        for (int i = 0; i < ITERATIONS; i++) {
            sum += module.call("add", int.class, i, 1);
        }
        long elapsed = System.nanoTime() - start;
        System.out.printf("WasmModule.call(\"add\"): %.0f ns/op [sum=%d]%n",
                (double) elapsed / ITERATIONS, sum);

        module.close();
    }

    @Test
    void benchmarkWasmInstanceDirectFn() {
        WasmModule module = WasmRuntime.compile(ADD_MODULE);
        WasmInstance instance = module.newInstance();
        ai.tegmentum.webassembly4j.api.Function fn =
                instance.unwrap().function("add").orElseThrow();

        for (int i = 0; i < WARMUP; i++) {
            fn.invoke(i, 1);
        }

        long start = System.nanoTime();
        int sum = 0;
        for (int i = 0; i < ITERATIONS; i++) {
            sum += ((Number) fn.invoke(i, 1)).intValue();
        }
        long elapsed = System.nanoTime() - start;
        System.out.printf("Direct fn.invoke():      %.0f ns/op [sum=%d]%n",
                (double) elapsed / ITERATIONS, sum);

        instance.close();
        module.close();
    }
}
