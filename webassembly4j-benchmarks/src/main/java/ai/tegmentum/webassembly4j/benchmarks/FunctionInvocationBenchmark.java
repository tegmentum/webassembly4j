package ai.tegmentum.webassembly4j.benchmarks;

import ai.tegmentum.webassembly4j.api.Engine;
import ai.tegmentum.webassembly4j.api.Function;
import ai.tegmentum.webassembly4j.api.Instance;
import ai.tegmentum.webassembly4j.api.Module;
import org.openjdk.jmh.annotations.*;

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
    private Function addFunction;
    private Function noopFunction;
    private Function fibFunction;
    private int counter;

    @Setup(Level.Trial)
    public void setup() {
        EngineVariant ev = EngineVariant.valueOf(variant);
        if (!BenchmarkSupport.isAvailable(ev)) {
            throw new IllegalStateException("Engine variant " + variant + " is not available");
        }

        engine = BenchmarkSupport.createEngine(ev);

        Module addModule = engine.loadModule(BenchmarkModules.ADD_MODULE);
        Instance addInstance = addModule.instantiate();
        addFunction = addInstance.function("add").orElseThrow();

        Module voidModule = engine.loadModule(BenchmarkModules.VOID_MODULE);
        Instance voidInstance = voidModule.instantiate();
        noopFunction = voidInstance.function("noop").orElseThrow();

        Module fibModule = engine.loadModule(BenchmarkModules.FIBONACCI_MODULE);
        Instance fibInstance = fibModule.instantiate();
        fibFunction = fibInstance.function("fibonacci").orElseThrow();

        counter = 0;
    }

    @TearDown(Level.Trial)
    public void teardown() {
        if (engine != null) {
            engine.close();
        }
    }

    @Benchmark
    public Object invokeSimpleAdd() {
        int a = counter++ & 0xFF;
        return addFunction.invoke(a, a + 1);
    }

    @Benchmark
    public Object invokeVoidFunction() {
        noopFunction.invoke();
        return counter++;
    }

    @Benchmark
    public Object invokeFibonacci() {
        int n = 15 + (counter++ & 0x7);
        return fibFunction.invoke(n);
    }
}
