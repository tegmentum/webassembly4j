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
public class EndToEndBenchmark {

    @Param({"WASMTIME_JNI", "WASMTIME_PANAMA", "WAMR_JNI", "WAMR_PANAMA", "GRAALWASM", "CHICORY"})
    private String variant;

    private EngineVariant engineVariant;
    private int counter;

    @Setup(Level.Trial)
    public void setup() {
        engineVariant = EngineVariant.valueOf(variant);
        if (!BenchmarkSupport.isAvailable(engineVariant)) {
            throw new IllegalStateException("Engine variant " + variant + " is not available");
        }
        counter = 0;
    }

    @Benchmark
    public Object fullLifecycle() {
        int a = counter++;
        try (Engine engine = BenchmarkSupport.createEngine(engineVariant)) {
            Module module = engine.loadModule(BenchmarkModules.ADD_MODULE);
            Instance instance = module.instantiate();
            Function add = instance.function("add").orElseThrow();
            Object result = add.invoke(a, a + 1);
            module.close();
            return result;
        }
    }
}
