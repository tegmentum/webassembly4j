package ai.tegmentum.webassembly4j.runtime;

import ai.tegmentum.webassembly4j.api.Engine;
import ai.tegmentum.webassembly4j.api.WebAssembly;
import org.junit.jupiter.api.Test;

/**
 * Micro-benchmark for engine creation to measure the impact of
 * ServiceLoader caching. Each build() call triggers provider discovery.
 */
class EngineBuildBenchmarkTest {

    private static final int ITERATIONS = 1_000;
    private static final int WARMUP = 200;

    @Test
    void benchmarkEngineBuild() {
        // Warmup
        for (int i = 0; i < WARMUP; i++) {
            try (Engine engine = WebAssembly.builder().build()) {
                // just build and close
            }
        }

        long start = System.nanoTime();
        for (int i = 0; i < ITERATIONS; i++) {
            try (Engine engine = WebAssembly.builder().build()) {
                // just build and close
            }
        }
        long elapsed = System.nanoTime() - start;
        System.out.printf("Engine.build(): %d iterations in %d ms (%.0f us/op)%n",
                ITERATIONS, elapsed / 1_000_000, (double) elapsed / ITERATIONS / 1_000);
    }

    @Test
    void benchmarkEngineBuildAndLoadModule() {
        // Minimal WASM module: (func (export "add") (param i32 i32) (result i32))
        byte[] addModule = new byte[] {
            0x00, 0x61, 0x73, 0x6D, 0x01, 0x00, 0x00, 0x00,
            0x01, 0x07, 0x01, 0x60, 0x02, 0x7F, 0x7F, 0x01, 0x7F,
            0x03, 0x02, 0x01, 0x00,
            0x07, 0x07, 0x01, 0x03, 0x61, 0x64, 0x64, 0x00, 0x00,
            0x0A, 0x09, 0x01, 0x07, 0x00, 0x20, 0x00, 0x20, 0x01, 0x6A, 0x0B
        };

        // Warmup
        for (int i = 0; i < WARMUP; i++) {
            try (Engine engine = WebAssembly.builder().build()) {
                ai.tegmentum.webassembly4j.api.Module module = engine.loadModule(addModule);
                module.close();
            }
        }

        long start = System.nanoTime();
        for (int i = 0; i < ITERATIONS; i++) {
            try (Engine engine = WebAssembly.builder().build()) {
                ai.tegmentum.webassembly4j.api.Module module = engine.loadModule(addModule);
                module.close();
            }
        }
        long elapsed = System.nanoTime() - start;
        System.out.printf("Engine.build()+loadModule: %d iterations in %d ms (%.0f us/op)%n",
                ITERATIONS, elapsed / 1_000_000, (double) elapsed / ITERATIONS / 1_000);
    }
}
