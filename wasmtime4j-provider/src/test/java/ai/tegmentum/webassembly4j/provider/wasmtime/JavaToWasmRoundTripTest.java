package ai.tegmentum.webassembly4j.provider.wasmtime;

import ai.tegmentum.webassembly4j.api.DefaultLinkingContext;
import ai.tegmentum.webassembly4j.api.Engine;
import ai.tegmentum.webassembly4j.api.Function;
import ai.tegmentum.webassembly4j.api.Instance;
import ai.tegmentum.webassembly4j.api.Module;
import ai.tegmentum.webassembly4j.api.ValueType;
import ai.tegmentum.webassembly4j.api.config.WebAssemblyConfig;
import ai.tegmentum.webassembly4j.provider.wasmtime.config.WasmtimeConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Round-trip test: Java source compiled to WasmGC by GraalVM, then loaded and
 * executed by webassembly4j's wasmtime provider.
 * <p>
 * The {@code calculator.wasm} file was produced by:
 * <pre>{@code
 * public class Calculator {
 *     @WasmExport("add")     public static int add(int a, int b) { return a + b; }
 *     @WasmExport("multiply") public static int multiply(int a, int b) { return a * b; }
 *     @WasmExport("fibonacci") public static int fibonacci(int n) { ... }
 *     public static void main(String[] args) {}
 * }
 * }</pre>
 * Compiled with:
 * {@code mx web-image -H:+StandaloneWasm -H:Backend=WASMGC -H:-AutoRunVM Calculator}
 */
class JavaToWasmRoundTripTest {

    private static byte[] loadCalculatorWasm() throws IOException {
        try (InputStream is = JavaToWasmRoundTripTest.class.getResourceAsStream("/calculator.wasm")) {
            assertNotNull(is, "calculator.wasm not found on classpath");
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = is.read(buf)) != -1) {
                out.write(buf, 0, n);
            }
            return out.toByteArray();
        }
    }

    /**
     * Builds a {@link DefaultLinkingContext} that satisfies the 7 imports
     * required by the standalone WasmGC module.
     */
    private static DefaultLinkingContext buildStandaloneContext(List<int[]> printedChars) {
        DefaultLinkingContext.Builder ctx = DefaultLinkingContext.builder();

        String io = "graalvm:standalone/io@0.1.0";
        String compat = "graalvm:standalone/compat@0.1.0";
        String wasi = "graalvm:standalone/wasi@0.1.0";

        // io.print-buffer(fd: i32, ptr: i32, num_chars: i32)
        // Reads 16-bit chars from linear memory and collects them
        ctx.addHostFunction(io, "print-buffer",
                new ValueType[]{ValueType.I32, ValueType.I32, ValueType.I32}, new ValueType[]{},
                args -> {
                    int fd = ((Number) args[0]).intValue();
                    int ptr = ((Number) args[1]).intValue();
                    int numChars = ((Number) args[2]).intValue();
                    for (int i = 0; i < numChars; i++) {
                        printedChars.add(new int[]{fd, ptr + i * 2});
                    }
                    return new Object[]{};
                });

        // io.host-time-ms() -> f64
        ctx.addHostFunction(io, "host-time-ms",
                new ValueType[]{}, new ValueType[]{ValueType.F64},
                args -> new Object[]{(double) System.currentTimeMillis()});

        // compat.f64rem(a, b) -> f64
        ctx.addHostFunction(compat, "f64rem",
                new ValueType[]{ValueType.F64, ValueType.F64}, new ValueType[]{ValueType.F64},
                args -> new Object[]{((Number) args[0]).doubleValue() % ((Number) args[1]).doubleValue()});

        // compat.f64log(a) -> f64
        ctx.addHostFunction(compat, "f64log",
                new ValueType[]{ValueType.F64}, new ValueType[]{ValueType.F64},
                args -> new Object[]{Math.log(((Number) args[0]).doubleValue())});

        // compat.f64log10(a) -> f64
        ctx.addHostFunction(compat, "f64log10",
                new ValueType[]{ValueType.F64}, new ValueType[]{ValueType.F64},
                args -> new Object[]{Math.log10(((Number) args[0]).doubleValue())});

        // compat.f64pow(a, b) -> f64
        ctx.addHostFunction(compat, "f64pow",
                new ValueType[]{ValueType.F64, ValueType.F64}, new ValueType[]{ValueType.F64},
                args -> new Object[]{Math.pow(((Number) args[0]).doubleValue(), ((Number) args[1]).doubleValue())});

        // wasi.proc-exit(code: i32)
        ctx.addHostFunction(wasi, "proc-exit",
                new ValueType[]{ValueType.I32}, new ValueType[]{},
                args -> {
                    throw new RuntimeException("proc_exit(" + args[0] + ")");
                });

        return ctx.build();
    }

    static boolean runtimeAvailable() {
        try (Engine engine = WasmtimeEngineAdapter.create(null)) {
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Test
    @EnabledIf("runtimeAvailable")
    void addTwoNumbers() throws IOException {
        byte[] wasm = loadCalculatorWasm();

        WebAssemblyConfig config = WebAssemblyConfig.builder()
                .engineConfig(WasmtimeConfig.builder()
                        .wasmGc(true)
                        .wasmExceptions(true)
                        .wasmFunctionReferences(true)
                        .build())
                .build();

        List<int[]> printed = new ArrayList<>();
        try (Engine engine = WasmtimeEngineAdapter.create(config);
             Module module = engine.loadModule(wasm)) {

            Instance instance = module.instantiate(buildStandaloneContext(printed));
            Function add = instance.function("add").orElseThrow();

            assertEquals(7, add.invoke(3, 4));
            assertEquals(0, add.invoke(0, 0));
            assertEquals(-1, add.invoke(Integer.MAX_VALUE, Integer.MIN_VALUE));
            assertEquals(100, add.invoke(42, 58));
        }
    }

    @Test
    @EnabledIf("runtimeAvailable")
    void multiplyTwoNumbers() throws IOException {
        byte[] wasm = loadCalculatorWasm();

        WebAssemblyConfig config = WebAssemblyConfig.builder()
                .engineConfig(WasmtimeConfig.builder()
                        .wasmGc(true)
                        .wasmExceptions(true)
                        .wasmFunctionReferences(true)
                        .build())
                .build();

        List<int[]> printed = new ArrayList<>();
        try (Engine engine = WasmtimeEngineAdapter.create(config);
             Module module = engine.loadModule(wasm)) {

            Instance instance = module.instantiate(buildStandaloneContext(printed));
            Function multiply = instance.function("multiply").orElseThrow();

            assertEquals(12, multiply.invoke(3, 4));
            assertEquals(0, multiply.invoke(0, 999));
            assertEquals(100, multiply.invoke(10, 10));
        }
    }

    @Test
    @EnabledIf("runtimeAvailable")
    void fibonacciSequence() throws IOException {
        byte[] wasm = loadCalculatorWasm();

        WebAssemblyConfig config = WebAssemblyConfig.builder()
                .engineConfig(WasmtimeConfig.builder()
                        .wasmGc(true)
                        .wasmExceptions(true)
                        .wasmFunctionReferences(true)
                        .build())
                .build();

        List<int[]> printed = new ArrayList<>();
        try (Engine engine = WasmtimeEngineAdapter.create(config);
             Module module = engine.loadModule(wasm)) {

            Instance instance = module.instantiate(buildStandaloneContext(printed));
            Function fib = instance.function("fibonacci").orElseThrow();

            assertEquals(0, fib.invoke(0));
            assertEquals(1, fib.invoke(1));
            assertEquals(1, fib.invoke(2));
            assertEquals(2, fib.invoke(3));
            assertEquals(5, fib.invoke(5));
            assertEquals(55, fib.invoke(10));
            assertEquals(6765, fib.invoke(20));
        }
    }
}
