package ai.tegmentum.webassembly4j.provider.chicory;

import ai.tegmentum.webassembly4j.api.Component;
import ai.tegmentum.webassembly4j.api.Engine;
import ai.tegmentum.webassembly4j.api.EngineCapabilities;
import ai.tegmentum.webassembly4j.api.EngineInfo;
import ai.tegmentum.webassembly4j.api.Module;
import ai.tegmentum.webassembly4j.api.config.WebAssemblyConfig;
import ai.tegmentum.webassembly4j.api.exception.UnsupportedFeatureException;
import ai.tegmentum.webassembly4j.api.exception.ValidationException;
import ai.tegmentum.webassembly4j.provider.chicory.config.ChicoryConfig;
import com.dylibso.chicory.wasm.Parser;
import com.dylibso.chicory.wasm.WasmModule;

import java.util.Optional;
import java.util.function.Function;

final class ChicoryEngineAdapter implements Engine {

    private final WebAssemblyConfig config;
    private final ChicoryConfig.ExecutionMode executionMode;

    private ChicoryEngineAdapter(WebAssemblyConfig config, ChicoryConfig.ExecutionMode executionMode) {
        this.config = config;
        this.executionMode = executionMode;
    }

    static ChicoryEngineAdapter create(WebAssemblyConfig config) {
        ChicoryConfig.ExecutionMode mode = ChicoryConfig.ExecutionMode.INTERPRET;
        if (config != null) {
            Optional<? extends ai.tegmentum.webassembly4j.api.config.EngineConfig> ec = config.engineConfig();
            if (ec.isPresent() && ec.get() instanceof ChicoryConfig) {
                mode = ((ChicoryConfig) ec.get()).executionMode();
            }
        }
        return new ChicoryEngineAdapter(config, mode);
    }

    @Override
    public EngineInfo info() {
        return new ChicoryEngineInfo();
    }

    @Override
    public EngineCapabilities capabilities() {
        return new ChicoryEngineCapabilities();
    }

    @Override
    public Module loadModule(byte[] bytes) {
        try {
            WasmModule wasmModule = Parser.parse(bytes);
            Function<WasmModule, com.dylibso.chicory.runtime.Instance.Builder> builderFactory =
                    createBuilderFactory(wasmModule);
            return new ChicoryModuleAdapter(wasmModule, builderFactory);
        } catch (Exception e) {
            throw new ValidationException("Failed to load WebAssembly module", e);
        }
    }

    private Function<WasmModule, com.dylibso.chicory.runtime.Instance.Builder> createBuilderFactory(
            WasmModule wasmModule) {
        if (executionMode == ChicoryConfig.ExecutionMode.COMPILE) {
            return CompilerSupport.createCompilingBuilderFactory();
        }
        return m -> com.dylibso.chicory.runtime.Instance.builder(m);
    }

    @Override
    public Component loadComponent(byte[] bytes) {
        throw new UnsupportedFeatureException(
                "Component model is not supported by Chicory");
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
