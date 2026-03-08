package ai.tegmentum.webassembly4j.benchmarks;

import ai.tegmentum.webassembly4j.api.DefaultLinkingContext;
import ai.tegmentum.webassembly4j.api.Engine;
import ai.tegmentum.webassembly4j.api.Function;
import ai.tegmentum.webassembly4j.api.Instance;
import ai.tegmentum.webassembly4j.api.Module;
import ai.tegmentum.webassembly4j.api.ValueType;
import org.openjdk.jmh.annotations.*;

import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@State(Scope.Thread)
@Fork(value = 2, jvmArgs = {"--enable-native-access=ALL-UNNAMED"})
@Warmup(iterations = 3)
@Measurement(iterations = 5)
public class HostFunctionBenchmark {

    @Param({"WASMTIME_JNI", "WASMTIME_PANAMA", "WAMR_JNI", "WAMR_PANAMA", "WAMR_LLVM_JIT_JNI", "WAMR_LLVM_JIT_PANAMA", "GRAALWASM", "CHICORY"})
    private String variant;

    private Engine engine;
    private Function callHostFunction;
    private int counter;

    @Setup(Level.Trial)
    public void setup() {
        EngineVariant ev = EngineVariant.valueOf(variant);
        if (!BenchmarkSupport.isAvailable(ev)) {
            throw new IllegalStateException("Engine variant " + variant + " is not available");
        }

        engine = BenchmarkSupport.createEngine(ev);
        Module module = engine.loadModule(BenchmarkModules.IMPORT_MODULE);

        DefaultLinkingContext ctx = DefaultLinkingContext.builder()
                .addHostFunction("env", "add_offset",
                        new ValueType[] { ValueType.I32 },
                        new ValueType[] { ValueType.I32 },
                        args -> new Object[] { ((Number) args[0]).intValue() + 100 })
                .build();

        Instance instance = module.instantiate(ctx);
        callHostFunction = instance.function("call_host").orElseThrow();
        counter = 0;
    }

    @TearDown(Level.Trial)
    public void teardown() {
        if (engine != null) {
            engine.close();
        }
    }

    @Benchmark
    public Object hostFunctionRoundTrip() {
        return callHostFunction.invoke(counter++);
    }
}
