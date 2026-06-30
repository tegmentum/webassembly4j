package ai.tegmentum.webassembly4j.provider.endive;

import ai.tegmentum.webassembly4j.api.Component;
import ai.tegmentum.webassembly4j.api.Engine;
import ai.tegmentum.webassembly4j.api.EngineCapabilities;
import ai.tegmentum.webassembly4j.api.EngineInfo;
import ai.tegmentum.webassembly4j.api.Module;
import ai.tegmentum.webassembly4j.api.config.WebAssemblyConfig;
import ai.tegmentum.webassembly4j.api.exception.UnsupportedFeatureException;
import ai.tegmentum.webassembly4j.api.exception.ValidationException;
import ai.tegmentum.webassembly4j.provider.endive.config.EndiveConfig;
import run.endive.wasm.Parser;
import run.endive.wasm.WasmModule;

import java.util.Optional;
import java.util.function.Function;

final class EndiveEngineAdapter implements Engine {

    private final WebAssemblyConfig config;
    private final EndiveConfig.ExecutionMode executionMode;

    private EndiveEngineAdapter(WebAssemblyConfig config, EndiveConfig.ExecutionMode executionMode) {
        this.config = config;
        this.executionMode = executionMode;
    }

    static EndiveEngineAdapter create(WebAssemblyConfig config) {
        EndiveConfig.ExecutionMode mode = EndiveConfig.ExecutionMode.INTERPRET;
        if (config != null) {
            Optional<? extends ai.tegmentum.webassembly4j.api.config.EngineConfig> ec = config.engineConfig();
            if (ec.isPresent() && ec.get() instanceof EndiveConfig) {
                mode = ((EndiveConfig) ec.get()).executionMode();
            }
        }
        return new EndiveEngineAdapter(config, mode);
    }

    @Override
    public EngineInfo info() {
        return new EndiveEngineInfo();
    }

    @Override
    public EngineCapabilities capabilities() {
        return new EndiveEngineCapabilities();
    }

    @Override
    public Module loadModule(byte[] bytes) {
        try {
            WasmModule wasmModule = Parser.parse(bytes);
            Function<WasmModule, run.endive.runtime.Instance.Builder> builderFactory =
                    createBuilderFactory(wasmModule);
            return new EndiveModuleAdapter(wasmModule, builderFactory);
        } catch (Exception e) {
            throw new ValidationException("Failed to load WebAssembly module", e);
        }
    }

    private Function<WasmModule, run.endive.runtime.Instance.Builder> createBuilderFactory(
            WasmModule wasmModule) {
        if (executionMode == EndiveConfig.ExecutionMode.COMPILE) {
            return CompilerSupport.createCompilingBuilderFactory();
        }
        return m -> run.endive.runtime.Instance.builder(m);
    }

    @Override
    public Component loadComponent(byte[] bytes) {
        throw new UnsupportedFeatureException(
                "Component model is not supported by Endive");
    }

    @Override
    public <T> Optional<T> extension(Class<T> extensionType) {
        return Optional.empty();
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> Optional<T> unwrap(Class<T> nativeType) {
        return Optional.empty();
    }

    @Override
    public void close() {
        // No native resources to release
    }
}
