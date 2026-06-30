package ai.tegmentum.webassembly4j.provider.wasmtime;

import ai.tegmentum.wasmtime4j.WasmRuntime;
import ai.tegmentum.wasmtime4j.component.ComponentEngine;
import ai.tegmentum.wasmtime4j.component.ComponentLinker;
import ai.tegmentum.wasmtime4j.wasi.DirPerms;
import ai.tegmentum.wasmtime4j.wasi.FilePerms;
import ai.tegmentum.wasmtime4j.wasi.WasiPreview2Config;
import ai.tegmentum.webassembly4j.api.ComponentInstance;
import ai.tegmentum.webassembly4j.api.LinkingContext;
import ai.tegmentum.webassembly4j.api.WasiContext;
import ai.tegmentum.webassembly4j.api.config.ComponentConfig;
import ai.tegmentum.webassembly4j.api.exception.InstantiationException;
import ai.tegmentum.webassembly4j.api.exception.UnsupportedFeatureException;

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

final class WasmtimeComponentAdapter implements ai.tegmentum.webassembly4j.api.Component {

    private final WasmRuntime runtime;
    private final ai.tegmentum.wasmtime4j.Engine engine;
    private final ComponentEngine componentEngine;
    private final ai.tegmentum.wasmtime4j.component.Component nativeComponent;
    // Stores are created per instantiation (so ComponentConfig limits can be applied) and
    // closed together with the component.
    private final List<ai.tegmentum.wasmtime4j.Store> stores = new ArrayList<>();

    WasmtimeComponentAdapter(WasmRuntime runtime,
                             ai.tegmentum.wasmtime4j.Engine engine,
                             ComponentEngine componentEngine,
                             ai.tegmentum.wasmtime4j.component.Component nativeComponent) {
        this.runtime = runtime;
        this.engine = engine;
        this.componentEngine = componentEngine;
        this.nativeComponent = nativeComponent;
    }

    @Override
    public ComponentInstance instantiate() {
        return doInstantiate(null, null);
    }

    @Override
    public ComponentInstance instantiate(LinkingContext linkingContext) {
        return doInstantiate(linkingContext, null);
    }

    @Override
    public ComponentInstance instantiate(ComponentConfig config) {
        return doInstantiate(null, config);
    }

    @Override
    public ComponentInstance instantiate(LinkingContext linkingContext, ComponentConfig config) {
        return doInstantiate(linkingContext, config);
    }

    private ComponentInstance doInstantiate(LinkingContext linkingContext, ComponentConfig config) {
        if (linkingContext != null && !linkingContext.hostFunctions().isEmpty()) {
            throw new UnsupportedFeatureException(
                    "Core host functions are not supported for component instantiation. "
                    + "Use unwrap() to access the native ComponentLinker for WIT-level imports.");
        }
        try {
            ai.tegmentum.wasmtime4j.Store store = newStore(config);
            ComponentLinker<Object> linker = ComponentLinker.create(engine);

            WasiContext wasi = linkingContext != null ? linkingContext.wasiContext() : null;
            if (wasi != null) {
                // Forward the capability policy (preopens / env / stdio) into the component's
                // WASI Preview 2 context. Network is denied unless explicitly granted (not yet
                // expressible through WasiContext), matching deny-by-default.
                linker.enableWasiPreview2(toWasiPreview2Config(wasi));
            } else {
                linker.enableWasiPreview2();
            }

            ai.tegmentum.wasmtime4j.component.ComponentInstance nativeInstance =
                    linker.instantiate(store, nativeComponent);
            return new WasmtimeComponentInstanceAdapter(nativeInstance);
        } catch (ai.tegmentum.wasmtime4j.exception.WasmException e) {
            throw new InstantiationException(
                    "Failed to instantiate WebAssembly component", e);
        }
    }

    /** Create a store, applying ComponentConfig resource limits (memory/table/instances + fuel/epoch). */
    private ai.tegmentum.wasmtime4j.Store newStore(ComponentConfig config)
            throws ai.tegmentum.wasmtime4j.exception.WasmException {
        ai.tegmentum.wasmtime4j.Store store;
        if (config != null && hasStoreLimits(config)) {
            ai.tegmentum.wasmtime4j.config.StoreLimits.Builder lim =
                    ai.tegmentum.wasmtime4j.config.StoreLimits.builder();
            config.maxMemoryBytes().ifPresent(lim::memorySize);
            config.maxTableElements().ifPresent(lim::tableElements);
            config.maxInstances().ifPresent(lim::instances);
            config.maxTables().ifPresent(lim::tables);
            config.maxMemories().ifPresent(lim::memories);
            lim.trapOnGrowFailure(config.trapOnGrowFailure());
            store = runtime.createStore(engine, lim.build());
        } else {
            store = engine.createStore();
        }
        // Fuel: when the engine meters fuel, the store's remaining fuel is what the component
        // provider carries down as the per-instance compute cap. Set the requested cap, or
        // MAX_VALUE ("unlimited") when none is given — otherwise a metered store defaults to 0 fuel
        // and the component would trap on its first instruction.
        if (engine.isFuelEnabled()) {
            long fuel = (config != null && config.fuelLimit().isPresent())
                    ? config.fuelLimit().getAsLong()
                    : Long.MAX_VALUE;
            store.setFuel(fuel);
        }
        if (config != null && config.epochDeadline().isPresent()
                && engine.isEpochInterruptionEnabled()) {
            store.setEpochDeadline(config.epochDeadline().getAsLong());
        }
        stores.add(store);
        return store;
    }

    private static boolean hasStoreLimits(ComponentConfig c) {
        return c.maxMemoryBytes().isPresent() || c.maxTableElements().isPresent()
                || c.maxInstances().isPresent() || c.maxTables().isPresent()
                || c.maxMemories().isPresent();
    }

    /**
     * Translate the provider-agnostic WasiContext into wasmtime4j's WasiPreview2Config.
     * Package-private for unit testing the mapping.
     */
    static WasiPreview2Config toWasiPreview2Config(WasiContext wasi) {
        WasiPreview2Config.Builder b = WasiPreview2Config.builder();
        if (wasi.args() != null && !wasi.args().isEmpty()) {
            b.args(wasi.args().toArray(new String[0]));
        }
        if (wasi.env() != null && !wasi.env().isEmpty()) {
            b.env(wasi.env());
        }
        if (wasi.inheritStdin()) {
            b.inheritStdin();
        }
        if (wasi.inheritStdout()) {
            b.inheritStdout();
        }
        if (wasi.inheritStderr()) {
            b.inheritStderr();
        }
        // Preopens: host path == guest path (a granular guest-remap / per-dir-perms form is a
        // follow-on WasiContext API extension). Granting a dir grants full access within it.
        if (wasi.preopenDirs() != null) {
            for (String dir : wasi.preopenDirs()) {
                b.preopenDir(Paths.get(dir), dir, DirPerms.all(), FilePerms.all());
            }
        }
        return b.build();
    }

    @Override
    public List<String> exportedInterfaces() {
        try {
            Set<String> interfaces = nativeComponent.getExportedInterfaces();
            return Collections.unmodifiableList(new ArrayList<>(interfaces));
        } catch (ai.tegmentum.wasmtime4j.exception.WasmException e) {
            return Collections.emptyList();
        }
    }

    @Override
    public List<String> importedInterfaces() {
        try {
            Set<String> interfaces = nativeComponent.getImportedInterfaces();
            return Collections.unmodifiableList(new ArrayList<>(interfaces));
        } catch (ai.tegmentum.wasmtime4j.exception.WasmException e) {
            return Collections.emptyList();
        }
    }

    @Override
    public boolean exportsInterface(String name) {
        try {
            return nativeComponent.exportsInterface(name);
        } catch (ai.tegmentum.wasmtime4j.exception.WasmException e) {
            return false;
        }
    }

    @Override
    public boolean importsInterface(String name) {
        try {
            return nativeComponent.importsInterface(name);
        } catch (ai.tegmentum.wasmtime4j.exception.WasmException e) {
            return false;
        }
    }

    @Override
    public byte[] serialize() {
        try {
            return nativeComponent.serialize();
        } catch (ai.tegmentum.wasmtime4j.exception.WasmException e) {
            throw new ai.tegmentum.webassembly4j.api.exception.WebAssemblyException(
                    "Failed to serialize component", e);
        }
    }

    @Override
    public void close() {
        for (ai.tegmentum.wasmtime4j.Store s : stores) {
            try {
                s.close();
            } catch (RuntimeException ignored) {
                // best effort
            }
        }
        nativeComponent.close();
        try {
            componentEngine.close();
        } catch (java.io.IOException e) {
            throw new ai.tegmentum.webassembly4j.api.exception.WebAssemblyException(
                    "Failed to close component engine", e);
        }
    }
}
