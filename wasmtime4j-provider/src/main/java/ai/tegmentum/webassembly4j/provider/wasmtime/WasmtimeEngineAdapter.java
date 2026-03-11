package ai.tegmentum.webassembly4j.provider.wasmtime;

import ai.tegmentum.wasmtime4j.WasmRuntime;
import ai.tegmentum.wasmtime4j.factory.WasmRuntimeFactory;
import ai.tegmentum.webassembly4j.api.Component;
import ai.tegmentum.webassembly4j.api.Engine;
import ai.tegmentum.webassembly4j.api.EngineCapabilities;
import ai.tegmentum.webassembly4j.api.EngineInfo;
import ai.tegmentum.webassembly4j.api.Module;
import ai.tegmentum.wasmtime4j.component.ComponentEngine;
import ai.tegmentum.webassembly4j.api.config.CommonConfig;
import ai.tegmentum.webassembly4j.api.config.OptimizationLevel;
import ai.tegmentum.webassembly4j.api.config.WebAssemblyConfig;
import ai.tegmentum.webassembly4j.api.exception.ConfigurationException;
import ai.tegmentum.webassembly4j.api.exception.UnsupportedFeatureException;
import ai.tegmentum.webassembly4j.provider.wasmtime.config.CraneliftOptLevel;
import ai.tegmentum.webassembly4j.provider.wasmtime.config.WasmtimeConfig;

import java.util.Optional;

final class WasmtimeEngineAdapter implements Engine {

    private final WasmRuntime runtime;
    private final ai.tegmentum.wasmtime4j.Engine engine;
    private final ai.tegmentum.wasmtime4j.config.EngineConfig nativeConfig;

    private WasmtimeEngineAdapter(WasmRuntime runtime, ai.tegmentum.wasmtime4j.Engine engine,
                                  ai.tegmentum.wasmtime4j.config.EngineConfig nativeConfig) {
        this.runtime = runtime;
        this.engine = engine;
        this.nativeConfig = nativeConfig;
    }

    static WasmtimeEngineAdapter create(WebAssemblyConfig config) {
        ai.tegmentum.wasmtime4j.config.EngineConfig nativeConfig =
                new ai.tegmentum.wasmtime4j.config.EngineConfig();

        if (config != null) {
            applyCommonConfig(nativeConfig, config.commonConfig());
            config.engineConfig().ifPresent(ec -> {
                if (ec instanceof WasmtimeConfig) {
                    applyWasmtimeConfig(nativeConfig, (WasmtimeConfig) ec, config.commonConfig());
                }
            });
        }

        try {
            WasmRuntime runtime = WasmRuntimeFactory.create();
            ai.tegmentum.wasmtime4j.Engine engine = runtime.createEngine(nativeConfig);
            return new WasmtimeEngineAdapter(runtime, engine, nativeConfig);
        } catch (ai.tegmentum.wasmtime4j.exception.WasmException e) {
            throw new ai.tegmentum.webassembly4j.api.exception.WebAssemblyException(
                    "Failed to create Wasmtime engine", e);
        }
    }

    private static void applyCommonConfig(ai.tegmentum.wasmtime4j.config.EngineConfig nativeConfig,
                                          CommonConfig common) {
        if (common == null) {
            return;
        }
        common.optimizationLevel().ifPresent(level -> {
            switch (level) {
                case NONE:
                    nativeConfig.optimizationLevel(
                            ai.tegmentum.wasmtime4j.config.OptimizationLevel.NONE);
                    break;
                case SPEED:
                    nativeConfig.optimizationLevel(
                            ai.tegmentum.wasmtime4j.config.OptimizationLevel.SPEED);
                    break;
                case SIZE:
                    nativeConfig.optimizationLevel(
                            ai.tegmentum.wasmtime4j.config.OptimizationLevel.SIZE);
                    break;
            }
        });
        common.debug().ifPresent(nativeConfig::debugInfo);
        common.fuelLimit().ifPresent(fuel -> nativeConfig.consumeFuel(true));
    }

    private static void applyWasmtimeConfig(ai.tegmentum.wasmtime4j.config.EngineConfig nativeConfig,
                                            WasmtimeConfig wasmtimeConfig,
                                            CommonConfig common) {
        // Conflict detection
        if (common != null) {
            detectOptimizationConflict(common, wasmtimeConfig);
            detectDebugConflict(common, wasmtimeConfig);
            detectFuelConflict(common, wasmtimeConfig);
        }

        wasmtimeConfig.consumeFuel().ifPresent(nativeConfig::consumeFuel);
        wasmtimeConfig.epochInterruption().ifPresent(nativeConfig::epochInterruption);
        wasmtimeConfig.debugInfo().ifPresent(nativeConfig::debugInfo);
        wasmtimeConfig.parallelCompilation().ifPresent(nativeConfig::parallelCompilation);
        wasmtimeConfig.wasmThreads().ifPresent(nativeConfig::wasmThreads);
        wasmtimeConfig.wasmMultiMemory().ifPresent(nativeConfig::wasmMultiMemory);
        wasmtimeConfig.wasmComponentModel().ifPresent(nativeConfig::wasmComponentModel);
        wasmtimeConfig.wasmGc().ifPresent(nativeConfig::wasmGc);
        wasmtimeConfig.craneliftOptLevel().ifPresent(level -> {
            switch (level) {
                case NONE:
                    nativeConfig.optimizationLevel(
                            ai.tegmentum.wasmtime4j.config.OptimizationLevel.NONE);
                    break;
                case SPEED:
                    nativeConfig.optimizationLevel(
                            ai.tegmentum.wasmtime4j.config.OptimizationLevel.SPEED);
                    break;
                case SIZE:
                    nativeConfig.optimizationLevel(
                            ai.tegmentum.wasmtime4j.config.OptimizationLevel.SIZE);
                    break;
                case SPEED_AND_SIZE:
                    nativeConfig.optimizationLevel(
                            ai.tegmentum.wasmtime4j.config.OptimizationLevel.SPEED_AND_SIZE);
                    break;
            }
        });
    }

    private static void detectOptimizationConflict(CommonConfig common, WasmtimeConfig wasmtime) {
        if (common.optimizationLevel().isPresent() && wasmtime.craneliftOptLevel().isPresent()) {
            OptimizationLevel commonLevel = common.optimizationLevel().get();
            CraneliftOptLevel engineLevel = wasmtime.craneliftOptLevel().get();
            if (!isCompatible(commonLevel, engineLevel)) {
                throw new ConfigurationException(
                        "Conflicting optimization: common=" + commonLevel
                                + ", engine craneliftOptLevel=" + engineLevel);
            }
        }
    }

    private static void detectDebugConflict(CommonConfig common, WasmtimeConfig wasmtime) {
        if (common.debug().isPresent() && wasmtime.debugInfo().isPresent()) {
            if (!common.debug().get().equals(wasmtime.debugInfo().get())) {
                throw new ConfigurationException(
                        "Conflicting debug config: common=" + common.debug().get()
                                + ", engine=" + wasmtime.debugInfo().get());
            }
        }
    }

    private static void detectFuelConflict(CommonConfig common, WasmtimeConfig wasmtime) {
        if (common.fuelLimit().isPresent() && wasmtime.consumeFuel().isPresent()
                && !wasmtime.consumeFuel().get()) {
            throw new ConfigurationException(
                    "Common config sets fuelLimit but engine config disables consumeFuel");
        }
    }

    private static boolean isCompatible(OptimizationLevel common, CraneliftOptLevel engine) {
        switch (common) {
            case NONE: return engine == CraneliftOptLevel.NONE;
            case SPEED: return engine == CraneliftOptLevel.SPEED
                    || engine == CraneliftOptLevel.SPEED_AND_SIZE;
            case SIZE: return engine == CraneliftOptLevel.SIZE
                    || engine == CraneliftOptLevel.SPEED_AND_SIZE;
            default: return false;
        }
    }

    @Override
    public EngineInfo info() {
        return new WasmtimeEngineInfo();
    }

    @Override
    public EngineCapabilities capabilities() {
        return new WasmtimeEngineCapabilities(engine);
    }

    @Override
    public Module loadModule(byte[] bytes) {
        try {
            ai.tegmentum.wasmtime4j.Module nativeModule = engine.compileModule(bytes);
            ai.tegmentum.wasmtime4j.Store store = engine.createStore();
            return new WasmtimeModuleAdapter(runtime, engine, nativeModule, store, nativeConfig);
        } catch (ai.tegmentum.wasmtime4j.exception.WasmException e) {
            throw new ai.tegmentum.webassembly4j.api.exception.WebAssemblyException(
                    "Failed to load WebAssembly module", e);
        }
    }

    @Override
    public Component loadComponent(byte[] bytes) {
        try {
            ComponentEngine componentEngine = runtime.createComponentEngine();
            ai.tegmentum.wasmtime4j.component.Component nativeComponent =
                    componentEngine.compileComponent(bytes);
            ai.tegmentum.wasmtime4j.Store store = engine.createStore();
            return new WasmtimeComponentAdapter(
                    runtime, engine, componentEngine, nativeComponent, store);
        } catch (ai.tegmentum.wasmtime4j.exception.WasmException e) {
            throw new ai.tegmentum.webassembly4j.api.exception.WebAssemblyException(
                    "Failed to load WebAssembly component", e);
        }
    }

    @Override
    public <T> Optional<T> extension(Class<T> extensionType) {
        if (extensionType == ai.tegmentum.webassembly4j.api.capability.FuelController.class
                && engine.isFuelEnabled()) {
            // FuelController is per-store, not per-engine; return empty for engine level
            return Optional.empty();
        }
        if (extensionType == ai.tegmentum.webassembly4j.api.capability.EpochController.class
                && engine.isEpochInterruptionEnabled()) {
            @SuppressWarnings("unchecked")
            T controller = (T) (ai.tegmentum.webassembly4j.api.capability.EpochController)
                    engine::incrementEpoch;
            return Optional.of(controller);
        }
        return Optional.empty();
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> Optional<T> unwrap(Class<T> nativeType) {
        if (nativeType.isInstance(engine)) {
            return Optional.of((T) engine);
        }
        if (nativeType.isInstance(runtime)) {
            return Optional.of((T) runtime);
        }
        return Optional.empty();
    }

    @Override
    public void close() {
        engine.close();
        runtime.close();
    }
}
