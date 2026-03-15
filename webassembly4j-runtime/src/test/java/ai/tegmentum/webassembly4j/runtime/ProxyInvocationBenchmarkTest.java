package ai.tegmentum.webassembly4j.runtime;

import org.junit.jupiter.api.Test;

/**
 * Micro-benchmark for proxy invocation overhead.
 * Measures the per-call cost of invoking a WASM function through the proxy.
 */
class ProxyInvocationBenchmarkTest {

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

    interface Adder extends AutoCloseable {
        int add(int a, int b);
    }

    @Test
    void benchmarkProxyInvocation() throws Exception {
        try (Adder adder = WasmRuntime.load(Adder.class, ADD_MODULE)) {
            // Warmup
            for (int i = 0; i < WARMUP; i++) {
                adder.add(i, 1);
            }

            long start = System.nanoTime();
            int sum = 0;
            for (int i = 0; i < ITERATIONS; i++) {
                sum += adder.add(i, 1);
            }
            long elapsed = System.nanoTime() - start;
            System.out.printf("Proxy add(i32,i32)->i32: %d iterations in %d ms (%.0f ns/op) [sum=%d]%n",
                    ITERATIONS, elapsed / 1_000_000, (double) elapsed / ITERATIONS, sum);
        }
    }

    @Test
    void benchmarkDirectFunctionInvocation() {
        try (ai.tegmentum.webassembly4j.api.Engine engine =
                     ai.tegmentum.webassembly4j.api.WebAssembly.builder().build()) {
            ai.tegmentum.webassembly4j.api.Module module = engine.loadModule(ADD_MODULE);
            ai.tegmentum.webassembly4j.api.Instance instance = module.instantiate();
            ai.tegmentum.webassembly4j.api.Function fn = instance.function("add")
                    .orElseThrow(() -> new RuntimeException("no add"));

            // Warmup
            for (int i = 0; i < WARMUP; i++) {
                fn.invoke(i, 1);
            }

            long start = System.nanoTime();
            int sum = 0;
            for (int i = 0; i < ITERATIONS; i++) {
                Object result = fn.invoke(i, 1);
                sum += ((Number) result).intValue();
            }
            long elapsed = System.nanoTime() - start;
            System.out.printf("Direct fn.invoke(i32,i32): %d iterations in %d ms (%.0f ns/op) [sum=%d]%n",
                    ITERATIONS, elapsed / 1_000_000, (double) elapsed / ITERATIONS, sum);
            module.close();
        }
    }
}
