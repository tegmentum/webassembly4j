package ai.tegmentum.webassembly4j.provider.chicory;

import ai.tegmentum.webassembly4j.api.exception.UnsupportedFeatureException;
import com.dylibso.chicory.wasm.WasmModule;

import java.util.function.Function;

/**
 * Provides runtime compilation support via the optional {@code chicory-compiler} dependency.
 * <p>
 * Uses reflection to avoid a hard dependency on {@code com.dylibso.chicory:compiler},
 * which is only needed when {@link ai.tegmentum.webassembly4j.provider.chicory.config.ChicoryConfig.ExecutionMode#COMPILE}
 * is selected.
 */
final class CompilerSupport {

    private static final String COMPILER_CLASS = "com.dylibso.chicory.compiler.MachineFactoryCompiler";

    private CompilerSupport() {}

    static Function<WasmModule, com.dylibso.chicory.runtime.Instance.Builder> createCompilingBuilderFactory() {
        try {
            Class.forName(COMPILER_CLASS);
        } catch (ClassNotFoundException e) {
            throw new UnsupportedFeatureException(
                    "Runtime compilation requires com.dylibso.chicory:compiler on the classpath. " +
                    "Add the dependency or use ExecutionMode.INTERPRET.");
        }
        return CompilerBridge::createBuilder;
    }

    /**
     * Isolated in a separate class so that {@code MachineFactoryCompiler} is only loaded
     * when actually needed (after the class presence check).
     */
    private static final class CompilerBridge {
        static com.dylibso.chicory.runtime.Instance.Builder createBuilder(WasmModule module) {
            return com.dylibso.chicory.runtime.Instance.builder(module)
                    .withMachineFactory(
                            com.dylibso.chicory.compiler.MachineFactoryCompiler::compile);
        }
    }
}
