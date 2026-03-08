package ai.tegmentum.webassembly4j.provider.graalwasm;

import ai.tegmentum.webassembly4j.api.Component;
import ai.tegmentum.webassembly4j.api.Engine;
import ai.tegmentum.webassembly4j.api.EngineCapabilities;
import ai.tegmentum.webassembly4j.api.EngineInfo;
import ai.tegmentum.webassembly4j.api.Module;
import ai.tegmentum.webassembly4j.api.config.WebAssemblyConfig;
import ai.tegmentum.webassembly4j.api.exception.UnsupportedFeatureException;
import ai.tegmentum.webassembly4j.api.exception.ValidationException;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.io.ByteSequence;

import java.util.Optional;

final class GraalWasmEngineAdapter implements Engine {

    private final WebAssemblyConfig config;

    private GraalWasmEngineAdapter(WebAssemblyConfig config) {
        this.config = config;
    }

    static GraalWasmEngineAdapter create(WebAssemblyConfig config) {
        return new GraalWasmEngineAdapter(config);
    }

    @Override
    public EngineInfo info() {
        return new GraalWasmEngineInfo();
    }

    @Override
    public EngineCapabilities capabilities() {
        return new GraalWasmEngineCapabilities();
    }

    @Override
    public Module loadModule(byte[] bytes) {
        try {
            Source source = Source.newBuilder("wasm",
                    ByteSequence.create(bytes), "module").build();
            // Validate the module by doing a trial eval
            Context validationContext = Context.newBuilder("wasm").build();
            try {
                validationContext.eval(source);
            } finally {
                validationContext.close();
            }
            return new GraalWasmModuleAdapter(bytes);
        } catch (Exception e) {
            throw new ValidationException("Failed to load WebAssembly module", e);
        }
    }

    @Override
    public Component loadComponent(byte[] bytes) {
        throw new UnsupportedFeatureException(
                "Component model is not supported by GraalWasm");
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
        // No shared resources to release
    }
}
