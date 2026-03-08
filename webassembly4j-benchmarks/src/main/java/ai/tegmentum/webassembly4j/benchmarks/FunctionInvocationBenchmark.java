package ai.tegmentum.webassembly4j.benchmarks;

import ai.tegmentum.webassembly4j.api.Engine;
import ai.tegmentum.webassembly4j.api.Function;
import ai.tegmentum.webassembly4j.api.Instance;
import ai.tegmentum.webassembly4j.api.Module;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@State(Scope.Thread)
@Fork(value = 2, jvmArgs = {"--enable-native-access=ALL-UNNAMED"})
@Warmup(iterations = 3)
@Measurement(iterations = 5)
public class FunctionInvocationBenchmark {

    @Param({"WASMTIME_JNI", "WASMTIME_PANAMA", "WAMR_JNI", "WAMR_PANAMA", "GRAALWASM", "CHICORY"})
    private String variant;

    private Engine engine;
    private Instance addInstance;
    private Instance voidInstance;
    private Instance fibInstance;
    private Function addFunction;
    private Function noopFunction;
    private Function fibFunction;
    private boolean available;

    @Setup(Level.Trial)
    public void setup() {
        EngineVariant ev = EngineVariant.valueOf(variant);
        available = BenchmarkSupport.isAvailable(ev);
        if (!available) return;

        engine = BenchmarkSupport.createEngine(ev);

        Module addModule = engine.loadModule(BenchmarkModules.ADD_MODULE);
        addInstance = addModule.instantiate();
        addFunction = addInstance.function("add").orElseThrow();

        Module voidModule = engine.loadModule(BenchmarkModules.VOID_MODULE);
        voidInstance = voidModule.instantiate();
        noopFunction = voidInstance.function("noop").orElseThrow();

        Module fibModule = engine.loadModule(BenchmarkModules.FIBONACCI_MODULE);
        fibInstance = fibModule.instantiate();
        fibFunction = fibInstance.function("fibonacci").orElseThrow();
    }

    @TearDown(Level.Trial)
    public void teardown() {
        if (engine != null) {
            engine.close();
        }
    }

    @Benchmark
    public void invokeSimpleAdd(Blackhole bh) {
        if (!available) return;
        bh.consume(addFunction.invoke(3, 4));
    }

    @Benchmark
    public void invokeVoidFunction(Blackhole bh) {
        if (!available) return;
        bh.consume(noopFunction.invoke());
    }

    @Benchmark
    public void invokeFibonacci(Blackhole bh) {
        if (!available) return;
        bh.consume(fibFunction.invoke(20));
    }
}
