package ai.tegmentum.webassembly4j.provider.wasmtime;

import ai.tegmentum.wasmtime4j.WasmRuntime;
import ai.tegmentum.wasmtime4j.component.ComponentEngine;
import ai.tegmentum.wasmtime4j.component.ComponentLinker;
import ai.tegmentum.webassembly4j.api.ComponentInstance;
import ai.tegmentum.webassembly4j.api.LinkingContext;
import ai.tegmentum.webassembly4j.api.exception.InstantiationException;
import ai.tegmentum.webassembly4j.api.exception.UnsupportedFeatureException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

final class WasmtimeComponentAdapter implements ai.tegmentum.webassembly4j.api.Component {

    private final WasmRuntime runtime;
    private final ai.tegmentum.wasmtime4j.Engine engine;
    private final ComponentEngine componentEngine;
    private final ai.tegmentum.wasmtime4j.component.Component nativeComponent;
    private final ai.tegmentum.wasmtime4j.Store store;

    WasmtimeComponentAdapter(WasmRuntime runtime,
                             ai.tegmentum.wasmtime4j.Engine engine,
                             ComponentEngine componentEngine,
                             ai.tegmentum.wasmtime4j.component.Component nativeComponent,
                             ai.tegmentum.wasmtime4j.Store store) {
        this.runtime = runtime;
        this.engine = engine;
        this.componentEngine = componentEngine;
        this.nativeComponent = nativeComponent;
        this.store = store;
    }

    @Override
    public ComponentInstance instantiate() {
        try {
            ComponentLinker<Object> linker = ComponentLinker.create(engine);
            linker.enableWasiPreview2();
            ai.tegmentum.wasmtime4j.component.ComponentInstance nativeInstance =
                    linker.instantiate(store, nativeComponent);
            return new WasmtimeComponentInstanceAdapter(nativeInstance);
        } catch (ai.tegmentum.wasmtime4j.exception.WasmException e) {
            throw new InstantiationException(
                    "Failed to instantiate WebAssembly component", e);
        }
    }

    @Override
    public ComponentInstance instantiate(LinkingContext linkingContext) {
        if (linkingContext == null) {
            return instantiate();
        }

        if (!linkingContext.hostFunctions().isEmpty()) {
            throw new UnsupportedFeatureException(
                    "Core host functions are not supported for component instantiation. "
                    + "Use unwrap() to access the native ComponentLinker for WIT-level imports.");
        }

        try {
            ComponentLinker<Object> linker = ComponentLinker.create(engine);

            if (linkingContext.wasiContext() != null) {
                linker.enableWasiPreview2();
            }

            ai.tegmentum.wasmtime4j.component.ComponentInstance nativeInstance =
                    linker.instantiate(store, nativeComponent);
            return new WasmtimeComponentInstanceAdapter(nativeInstance);
        } catch (ai.tegmentum.wasmtime4j.exception.WasmException e) {
            throw new InstantiationException(
                    "Failed to instantiate WebAssembly component with linking context", e);
        }
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
        nativeComponent.close();
        store.close();
        try {
            componentEngine.close();
        } catch (java.io.IOException e) {
            // ComponentEngine.close() is a Closeable; wrap if it ever throws
            throw new ai.tegmentum.webassembly4j.api.exception.WebAssemblyException(
                    "Failed to close component engine", e);
        }
    }
}
