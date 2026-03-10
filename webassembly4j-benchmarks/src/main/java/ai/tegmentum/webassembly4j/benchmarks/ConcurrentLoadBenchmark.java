package ai.tegmentum.webassembly4j.benchmarks;

import ai.tegmentum.webassembly4j.api.Engine;
import ai.tegmentum.webassembly4j.api.Function;
import ai.tegmentum.webassembly4j.api.Instance;
import ai.tegmentum.webassembly4j.api.Module;
import org.openjdk.jmh.annotations.*;

import java.util.concurrent.TimeUnit;

/**
 * Measures throughput and contention behavior under concurrent load.
 * Each thread gets its own Instance but shares the Engine and Module,
 * matching real-world pooled usage patterns.
 *
 * <p>Run with varying thread counts via JMH's -t flag:</p>
 * <pre>
 *   -t 1    single-threaded baseline
 *   -t 4    moderate concurrency
 *   -t 8    high concurrency
 *   -t max  use all available cores
 * </pre>
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@State(Scope.Benchmark)
@Fork(value = 2, jvmArgs = {"--enable-native-access=ALL-UNNAMED"})
@Warmup(iterations = 3)
@Measurement(iterations = 5)
public class ConcurrentLoadBenchmark {

    @Param({"WASMTIME_JNI", "WASMTIME_PANAMA", "WAMR_JNI", "WAMR_PANAMA", "WAMR_LLVM_JIT_JNI", "WAMR_LLVM_JIT_PANAMA", "GRAALWASM", "CHICORY"})
    private String variant;

    private Engine engine;
    private Module addModule;
    private Module fibModule;

    @Setup(Level.Trial)
    public void setup() {
        EngineVariant ev = EngineVariant.valueOf(variant);
        if (!BenchmarkSupport.isAvailable(ev)) {
            throw new IllegalStateException("Engine variant " + variant + " is not available");
        }
        engine = BenchmarkSupport.createEngine(ev);
        addModule = engine.loadModule(BenchmarkModules.ADD_MODULE);
        fibModule = engine.loadModule(BenchmarkModules.FIBONACCI_MODULE);
    }

    @TearDown(Level.Trial)
    public void teardown() {
        if (addModule != null) addModule.close();
        if (fibModule != null) fibModule.close();
        if (engine != null) engine.close();
    }

    /**
     * Per-thread state: each thread gets its own Instance and Function handles.
     */
    @State(Scope.Thread)
    public static class ThreadState {
        Instance addInstance;
        Instance fibInstance;
        Function addFunction;
        Function fibFunction;
        int counter;

        @Setup(Level.Trial)
        public void setup(ConcurrentLoadBenchmark parent) {
            addInstance = parent.addModule.instantiate();
            addFunction = addInstance.function("add").orElseThrow();
            fibInstance = parent.fibModule.instantiate();
            fibFunction = fibInstance.function("fibonacci").orElseThrow();
            counter = 0;
        }

        @TearDown(Level.Trial)
        public void teardown() {
            // Instance lifecycle is managed by the Engine/Module
        }
    }

    @Benchmark
    public Object concurrentAdd(ThreadState state) {
        int a = state.counter++ & 0xFF;
        return state.addFunction.invoke(a, a + 1);
    }

    @Benchmark
    public Object concurrentFibonacci(ThreadState state) {
        int n = 15 + (state.counter++ & 0x7);
        return state.fibFunction.invoke(n);
    }

}
