package ai.tegmentum.webassembly4j.provider.chicory;

import ai.tegmentum.webassembly4j.api.Component;
import ai.tegmentum.webassembly4j.api.Engine;
import ai.tegmentum.webassembly4j.api.EngineCapabilities;
import ai.tegmentum.webassembly4j.api.EngineInfo;
import ai.tegmentum.webassembly4j.api.Module;
import ai.tegmentum.webassembly4j.api.config.WebAssemblyConfig;
import ai.tegmentum.webassembly4j.api.exception.UnsupportedFeatureException;
import ai.tegmentum.webassembly4j.api.exception.ValidationException;
import com.dylibso.chicory.wasm.Parser;
import com.dylibso.chicory.wasm.WasmModule;

import java.util.Optional;

final class ChicoryEngineAdapter implements Engine {

    private final WebAssemblyConfig config;

    private ChicoryEngineAdapter(WebAssemblyConfig config) {
        this.config = config;
    }

    static ChicoryEngineAdapter create(WebAssemblyConfig config) {
        return new ChicoryEngineAdapter(config);
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
            return new ChicoryModuleAdapter(wasmModule);
        } catch (Exception e) {
            throw new ValidationException("Failed to load WebAssembly module", e);
        }
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
