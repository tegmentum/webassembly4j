package ai.tegmentum.webassembly4j.provider.wasm3;

import ai.tegmentum.wasm34j.RuntimeFactory;
import ai.tegmentum.wasm34j.WebAssemblyRuntime;
import ai.tegmentum.webassembly4j.api.Component;
import ai.tegmentum.webassembly4j.api.Engine;
import ai.tegmentum.webassembly4j.api.EngineCapabilities;
import ai.tegmentum.webassembly4j.api.EngineInfo;
import ai.tegmentum.webassembly4j.api.Module;
import ai.tegmentum.webassembly4j.api.config.WebAssemblyConfig;
import ai.tegmentum.webassembly4j.api.exception.UnsupportedFeatureException;

import java.util.Optional;

/** {@link Engine} backed by a wasm34j {@link WebAssemblyRuntime}. */
final class Wasm3EngineAdapter implements Engine {

    private final WebAssemblyRuntime runtime;

    private Wasm3EngineAdapter(final WebAssemblyRuntime runtime) {
        this.runtime = runtime;
    }

    static Wasm3EngineAdapter create(final WebAssemblyConfig config) {
        // wasm3 exposes no engine-specific configuration today; common config is honored by
        // wasm34j's defaults. The config argument is accepted for SPI symmetry.
        try {
            return new Wasm3EngineAdapter(RuntimeFactory.create());
        } catch (final ai.tegmentum.wasm34j.exception.WasmException e) {
            throw new ai.tegmentum.webassembly4j.api.exception.WebAssemblyException(
                    "Failed to create wasm3 engine", e);
        }
    }

    @Override
    public EngineInfo info() {
        return new Wasm3EngineInfo(runtime.engineVersion());
    }

    @Override
    public EngineCapabilities capabilities() {
        return new Wasm3EngineCapabilities();
    }

    @Override
    public Module loadModule(final byte[] bytes) {
        try {
            return new Wasm3ModuleAdapter(runtime.compile(bytes));
        } catch (final ai.tegmentum.wasm34j.exception.WasmException e) {
            throw new ai.tegmentum.webassembly4j.api.exception.WebAssemblyException(
                    "Failed to load WebAssembly module", e);
        }
    }

    @Override
    public Component loadComponent(final byte[] bytes) {
        throw new UnsupportedFeatureException("Component model is not supported by wasm3");
    }

    @Override
    public <T> Optional<T> extension(final Class<T> extensionType) {
        return Optional.empty();
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> Optional<T> unwrap(final Class<T> nativeType) {
        if (nativeType.isInstance(runtime)) {
            return Optional.of((T) runtime);
        }
        return Optional.empty();
    }

    @Override
    public void close() {
        runtime.close();
    }
}
