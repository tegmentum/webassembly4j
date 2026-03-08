package ai.tegmentum.webassembly4j.provider.wamr;

import ai.tegmentum.wamr4j.RuntimeFactory;
import ai.tegmentum.wamr4j.WebAssemblyRuntime;
import ai.tegmentum.webassembly4j.api.Component;
import ai.tegmentum.webassembly4j.api.Engine;
import ai.tegmentum.webassembly4j.api.EngineCapabilities;
import ai.tegmentum.webassembly4j.api.EngineInfo;
import ai.tegmentum.webassembly4j.api.Module;
import ai.tegmentum.webassembly4j.api.config.WebAssemblyConfig;
import ai.tegmentum.webassembly4j.api.exception.UnsupportedFeatureException;
import ai.tegmentum.webassembly4j.provider.wamr.config.WamrConfig;
import ai.tegmentum.webassembly4j.provider.wamr.config.WamrRunningMode;

import java.util.Optional;

final class WamrEngineAdapter implements Engine {

    private final WebAssemblyRuntime runtime;
    private final WamrConfig wamrConfig;

    private WamrEngineAdapter(WebAssemblyRuntime runtime, WamrConfig wamrConfig) {
        this.runtime = runtime;
        this.wamrConfig = wamrConfig;
    }

    static WamrEngineAdapter create(WebAssemblyConfig config) {
        WamrConfig wamrConfig = null;
        if (config != null) {
            wamrConfig = config.engineConfig()
                    .filter(ec -> ec instanceof WamrConfig)
                    .map(ec -> (WamrConfig) ec)
                    .orElse(null);
        }

        try {
            WebAssemblyRuntime runtime = RuntimeFactory.createRuntime();

            if (wamrConfig != null) {
                applyRuntimeConfig(runtime, wamrConfig);
            }

            return new WamrEngineAdapter(runtime, wamrConfig);
        } catch (ai.tegmentum.wamr4j.exception.WebAssemblyException e) {
            throw new ai.tegmentum.webassembly4j.api.exception.WebAssemblyException(
                    "Failed to create WAMR engine", e);
        }
    }

    private static void applyRuntimeConfig(WebAssemblyRuntime runtime, WamrConfig wamrConfig) {
        if (runtime instanceof ai.tegmentum.wamr4j.WamrRuntimeExtensions) {
            ai.tegmentum.wamr4j.WamrRuntimeExtensions ext =
                    (ai.tegmentum.wamr4j.WamrRuntimeExtensions) runtime;
            wamrConfig.runningMode().ifPresent(mode ->
                    ext.setDefaultRunningMode(toNativeRunningMode(mode)));
        }
    }

    private static ai.tegmentum.wamr4j.RunningMode toNativeRunningMode(WamrRunningMode mode) {
        switch (mode) {
            case INTERP: return ai.tegmentum.wamr4j.RunningMode.INTERP;
            case FAST_JIT: return ai.tegmentum.wamr4j.RunningMode.FAST_JIT;
            case LLVM_JIT: return ai.tegmentum.wamr4j.RunningMode.LLVM_JIT;
            case MULTI_TIER_JIT: return ai.tegmentum.wamr4j.RunningMode.MULTI_TIER_JIT;
            default: return ai.tegmentum.wamr4j.RunningMode.INTERP;
        }
    }

    @Override
    public EngineInfo info() {
        return new WamrEngineInfo(runtime.getVersion());
    }

    @Override
    public EngineCapabilities capabilities() {
        return new WamrEngineCapabilities();
    }

    @Override
    public Module loadModule(byte[] bytes) {
        try {
            ai.tegmentum.wamr4j.WebAssemblyModule nativeModule = runtime.compile(bytes);
            return new WamrModuleAdapter(nativeModule, wamrConfig);
        } catch (ai.tegmentum.wamr4j.exception.WebAssemblyException e) {
            throw new ai.tegmentum.webassembly4j.api.exception.WebAssemblyException(
                    "Failed to load WebAssembly module", e);
        }
    }

    @Override
    public Component loadComponent(byte[] bytes) {
        throw new UnsupportedFeatureException(
                "Component model is not supported by WAMR");
    }

    @Override
    public <T> Optional<T> extension(Class<T> extensionType) {
        return Optional.empty();
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> Optional<T> unwrap(Class<T> nativeType) {
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
