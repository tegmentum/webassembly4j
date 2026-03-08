package ai.tegmentum.webassembly4j.benchmarks;

import ai.tegmentum.webassembly4j.api.Engine;
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
public class ModuleLoadBenchmark {

    @Param({"WASMTIME_JNI", "WASMTIME_PANAMA", "WAMR_JNI", "WAMR_PANAMA", "GRAALWASM", "CHICORY"})
    private String variant;

    private Engine engine;
    private boolean available;

    @Setup(Level.Trial)
    public void setup() {
        EngineVariant ev = EngineVariant.valueOf(variant);
        available = BenchmarkSupport.isAvailable(ev);
        if (available) {
            engine = BenchmarkSupport.createEngine(ev);
        }
    }

    @TearDown(Level.Trial)
    public void teardown() {
        if (engine != null) {
            engine.close();
        }
    }

    @Benchmark
    public void loadSimpleModule(Blackhole bh) {
        if (!available) return;
        Module module = engine.loadModule(BenchmarkModules.ADD_MODULE);
        bh.consume(module);
        module.close();
    }

    @Benchmark
    public void loadComputeModule(Blackhole bh) {
        if (!available) return;
        Module module = engine.loadModule(BenchmarkModules.FIBONACCI_MODULE);
        bh.consume(module);
        module.close();
    }
}
