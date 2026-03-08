package ai.tegmentum.webassembly4j.benchmarks;

import ai.tegmentum.webassembly4j.api.Engine;
import ai.tegmentum.webassembly4j.api.Function;
import ai.tegmentum.webassembly4j.api.Instance;
import ai.tegmentum.webassembly4j.api.Memory;
import ai.tegmentum.webassembly4j.api.Module;
import org.openjdk.jmh.annotations.*;

import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@State(Scope.Thread)
@Fork(value = 2, jvmArgs = {"--enable-native-access=ALL-UNNAMED"})
@Warmup(iterations = 3)
@Measurement(iterations = 5)
public class MemoryOperationsBenchmark {

    @Param({"WASMTIME_JNI", "WASMTIME_PANAMA", "WAMR_JNI", "WAMR_PANAMA", "WAMR_LLVM_JIT_JNI", "WAMR_LLVM_JIT_PANAMA", "GRAALWASM", "CHICORY"})
    private String variant;

    @Param({"64", "1024", "4096", "65536"})
    private int dataSize;

    private Engine engine;
    private Function storeFunction;
    private Function loadFunction;
    private Memory memory;
    private byte[] testData;
    private int counter;

    @Setup(Level.Trial)
    public void setup() {
        EngineVariant ev = EngineVariant.valueOf(variant);
        if (!BenchmarkSupport.isAvailable(ev)) {
            throw new IllegalStateException("Engine variant " + variant + " is not available");
        }

        engine = BenchmarkSupport.createEngine(ev);
        Module module = engine.loadModule(BenchmarkModules.MEMORY_MODULE);
        Instance instance = module.instantiate();
        storeFunction = instance.function("store").orElseThrow();
        loadFunction = instance.function("load").orElseThrow();
        memory = instance.memory("memory").orElseThrow();

        testData = new byte[dataSize];
        for (int i = 0; i < dataSize; i++) {
            testData[i] = (byte) (i & 0xFF);
        }
        counter = 0;
    }

    @TearDown(Level.Trial)
    public void teardown() {
        if (engine != null) {
            engine.close();
        }
    }

    @Benchmark
    public Object memoryWriteViaFunction() {
        int val = counter++;
        return storeFunction.invoke(0, val);
    }

    @Benchmark
    public Object memoryReadViaFunction() {
        return loadFunction.invoke(counter++ & 0xFFFC);
    }

    @Benchmark
    public void directMemoryWrite() {
        testData[0] = (byte) counter++;
        memory.write(0, testData);
    }

    @Benchmark
    public byte[] directMemoryRead() {
        return memory.read(0, dataSize);
    }
}
