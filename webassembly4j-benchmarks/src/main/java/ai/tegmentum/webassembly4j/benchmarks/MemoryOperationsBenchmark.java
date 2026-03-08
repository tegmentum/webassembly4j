package ai.tegmentum.webassembly4j.benchmarks;

import ai.tegmentum.webassembly4j.api.Engine;
import ai.tegmentum.webassembly4j.api.Function;
import ai.tegmentum.webassembly4j.api.Instance;
import ai.tegmentum.webassembly4j.api.Memory;
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
public class MemoryOperationsBenchmark {

    @Param({"WASMTIME_JNI", "WASMTIME_PANAMA", "WAMR_JNI", "WAMR_PANAMA", "GRAALWASM", "CHICORY"})
    private String variant;

    @Param({"64", "1024", "4096", "65536"})
    private int dataSize;

    private Engine engine;
    private Instance instance;
    private Function storeFunction;
    private Function loadFunction;
    private Memory memory;
    private byte[] testData;
    private boolean available;

    @Setup(Level.Trial)
    public void setup() {
        EngineVariant ev = EngineVariant.valueOf(variant);
        available = BenchmarkSupport.isAvailable(ev);
        if (!available) return;

        engine = BenchmarkSupport.createEngine(ev);
        Module module = engine.loadModule(BenchmarkModules.MEMORY_MODULE);
        instance = module.instantiate();
        storeFunction = instance.function("store").orElseThrow();
        loadFunction = instance.function("load").orElseThrow();
        memory = instance.memory("memory").orElseThrow();

        testData = new byte[dataSize];
        for (int i = 0; i < dataSize; i++) {
            testData[i] = (byte) (i & 0xFF);
        }
    }

    @TearDown(Level.Trial)
    public void teardown() {
        if (engine != null) {
            engine.close();
        }
    }

    @Benchmark
    public void memoryWriteViaFunction(Blackhole bh) {
        if (!available) return;
        bh.consume(storeFunction.invoke(0, 42));
    }

    @Benchmark
    public void memoryReadViaFunction(Blackhole bh) {
        if (!available) return;
        bh.consume(loadFunction.invoke(0));
    }

    @Benchmark
    public void directMemoryWrite(Blackhole bh) {
        if (!available) return;
        memory.write(0, testData);
        bh.consume(testData);
    }

    @Benchmark
    public void directMemoryRead(Blackhole bh) {
        if (!available) return;
        bh.consume(memory.read(0, dataSize));
    }
}
