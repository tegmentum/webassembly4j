package ai.tegmentum.webassembly4j.provider.endive;

import ai.tegmentum.webassembly4j.api.exception.UnsupportedFeatureException;
import run.endive.wasm.WasmModule;

import java.util.function.Function;

/**
 * Provides runtime compilation support via the optional {@code endive-compiler} dependency.
 * <p>
 * Uses reflection to avoid a hard dependency on {@code run.endive:compiler},
 * which is only needed when {@link ai.tegmentum.webassembly4j.provider.endive.config.EndiveConfig.ExecutionMode#COMPILE}
 * is selected.
 */
final class CompilerSupport {

    private static final String COMPILER_CLASS = "run.endive.compiler.MachineFactoryCompiler";

    private CompilerSupport() {}

    static Function<WasmModule, run.endive.runtime.Instance.Builder> createCompilingBuilderFactory() {
        try {
            Class.forName(COMPILER_CLASS);
        } catch (ClassNotFoundException e) {
            throw new UnsupportedFeatureException(
                    "Runtime compilation requires run.endive:compiler on the classpath. " +
                    "Add the dependency or use ExecutionMode.INTERPRET.");
        }
        return CompilerBridge::createBuilder;
    }

    /**
     * Isolated in a separate class so that {@code MachineFactoryCompiler} is only loaded
     * when actually needed (after the class presence check).
     */
    private static final class CompilerBridge {
        static run.endive.runtime.Instance.Builder createBuilder(WasmModule module) {
            return run.endive.runtime.Instance.builder(module)
                    .withMachineFactory(
                            run.endive.compiler.MachineFactoryCompiler::compile);
        }
    }
}
