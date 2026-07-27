package ai.tegmentum.webassembly4j.provider.wasmtime;

import ai.tegmentum.wasmtime4j.Linker;
import ai.tegmentum.wasmtime4j.WasmRuntime;
import ai.tegmentum.wasmtime4j.WasmValue;
import ai.tegmentum.wasmtime4j.WasmValueType;
import ai.tegmentum.wasmtime4j.func.HostFunction;
import ai.tegmentum.wasmtime4j.type.ExportType;
import ai.tegmentum.wasmtime4j.type.FuncType;
import ai.tegmentum.wasmtime4j.type.FunctionType;
import ai.tegmentum.wasmtime4j.type.ImportType;
import ai.tegmentum.wasmtime4j.type.WasmType;
import ai.tegmentum.wasmtime4j.type.WasmTypeKind;
import ai.tegmentum.webassembly4j.api.CallerAwareHostFunction;
import ai.tegmentum.webassembly4j.api.CallerAwareHostFunctionDefinition;
import ai.tegmentum.webassembly4j.api.ExportDescriptor;
import ai.tegmentum.webassembly4j.api.ExternImportDefinition;
import ai.tegmentum.webassembly4j.api.ExternType;
import ai.tegmentum.webassembly4j.api.FunctionImport;
import ai.tegmentum.webassembly4j.api.GlobalImport;
import ai.tegmentum.webassembly4j.api.HostFunctionDefinition;
import ai.tegmentum.webassembly4j.api.ImportDescriptor;
import ai.tegmentum.webassembly4j.api.Instance;
import ai.tegmentum.webassembly4j.api.LinkingContext;
import ai.tegmentum.webassembly4j.api.MemoryImport;
import ai.tegmentum.webassembly4j.api.Module;
import ai.tegmentum.webassembly4j.api.TableImport;
import ai.tegmentum.webassembly4j.api.ValueType;
import ai.tegmentum.webassembly4j.api.exception.LinkingException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

final class WasmtimeModuleAdapter implements Module {

    private final WasmRuntime runtime;
    private final ai.tegmentum.wasmtime4j.Engine engine;
    private final ai.tegmentum.wasmtime4j.Module nativeModule;
    private final ai.tegmentum.wasmtime4j.Store store;
    private final ai.tegmentum.wasmtime4j.config.EngineConfig engineConfig;
    private final boolean callerScoped;

    WasmtimeModuleAdapter(WasmRuntime runtime,
                          ai.tegmentum.wasmtime4j.Engine engine,
                          ai.tegmentum.wasmtime4j.Module nativeModule,
                          ai.tegmentum.wasmtime4j.Store store,
                          ai.tegmentum.wasmtime4j.config.EngineConfig engineConfig) {
        this.runtime = runtime;
        this.engine = engine;
        this.nativeModule = nativeModule;
        this.store = store;
        this.engineConfig = engineConfig;
        this.callerScoped = false;
    }

    private WasmtimeModuleAdapter(WasmRuntime runtime,
                                  ai.tegmentum.wasmtime4j.Engine engine,
                                  ai.tegmentum.wasmtime4j.Module nativeModule,
                                  ai.tegmentum.wasmtime4j.config.EngineConfig engineConfig) {
        this.runtime = runtime;
        this.engine = engine;
        this.nativeModule = nativeModule;
        this.store = null;
        this.engineConfig = engineConfig;
        this.callerScoped = true;
    }

    /**
     * Construct a caller-scoped Module wrapper — used only by
     * {@link WasmtimeCallerAdapter#compileModule(byte[])} to hand back a
     * Module that borrows the caller's implicit store. Caller-scoped
     * modules must be instantiated via
     * {@link WasmtimeCallerAdapter#instantiate(Module, LinkingContext)};
     * their own {@code instantiate()} paths throw
     * {@link IllegalStateException} because they have no owning store.
     */
    static WasmtimeModuleAdapter callerScoped(
            WasmRuntime runtime,
            ai.tegmentum.wasmtime4j.Engine engine,
            ai.tegmentum.wasmtime4j.Module nativeModule,
            ai.tegmentum.wasmtime4j.config.EngineConfig engineConfig) {
        return new WasmtimeModuleAdapter(runtime, engine, nativeModule, engineConfig);
    }

    @Override
    public Instance instantiate() {
        if (callerScoped) {
            throw new IllegalStateException(
                    "This Module was produced by Caller.compileModule and has no"
                            + " owning store. Instantiate it via"
                            + " Caller.instantiate(Module, LinkingContext) instead.");
        }
        try {
            Linker<Object> linker = runtime.createLinker(engine);
            ai.tegmentum.wasmtime4j.Instance nativeInstance =
                    linker.instantiate(store, nativeModule);
            return new WasmtimeInstanceAdapter(nativeInstance, runtime, engine);
        } catch (ai.tegmentum.wasmtime4j.exception.WasmException e) {
            throw new ai.tegmentum.webassembly4j.api.exception.InstantiationException(
                    "Failed to instantiate WebAssembly module", e);
        }
    }

    @Override
    public Instance instantiate(LinkingContext linkingContext) {
        if (callerScoped) {
            throw new IllegalStateException(
                    "This Module was produced by Caller.compileModule and has no"
                            + " owning store. Instantiate it via"
                            + " Caller.instantiate(Module, LinkingContext) instead.");
        }
        if (linkingContext == null) {
            return instantiate();
        }

        List<HostFunctionDefinition> hostFunctions = linkingContext.hostFunctions();
        List<ExternImportDefinition> externImports = linkingContext.externImports();
        List<CallerAwareHostFunctionDefinition> callerAwareHostFunctions =
                linkingContext.callerAwareHostFunctions();
        if (hostFunctions.isEmpty()
                && linkingContext.wasiContext() == null
                && externImports.isEmpty()
                && callerAwareHostFunctions.isEmpty()) {
            return instantiate();
        }

        try {
            Linker<Object> linker = runtime.createLinker(engine);

            defineHostFunctions(linker, hostFunctions);

            defineCallerAwareHostFunctions(linker, callerAwareHostFunctions);

            ai.tegmentum.webassembly4j.api.WasiContext wasiCtx = linkingContext.wasiContext();
            if (wasiCtx != null) {
                ai.tegmentum.wasmtime4j.wasi.WasiContext nativeWasi = runtime.createWasiContext();
                configureNativeWasi(nativeWasi, wasiCtx);
                @SuppressWarnings("unchecked")
                Linker<ai.tegmentum.wasmtime4j.wasi.WasiContext> wasiLinker =
                        (Linker<ai.tegmentum.wasmtime4j.wasi.WasiContext>) (Linker<?>) linker;
                runtime.addWasiToLinker(wasiLinker, nativeWasi);
            }

            defineExternImports(linker, externImports);

            ai.tegmentum.wasmtime4j.Instance nativeInstance =
                    linker.instantiate(store, nativeModule);
            return new WasmtimeInstanceAdapter(nativeInstance, runtime, engine);
        } catch (ai.tegmentum.wasmtime4j.exception.WasmException e) {
            throw new LinkingException("Failed to instantiate with linking context", e);
        }
    }

    /**
     * Wire non-caller-aware host functions into the linker. Extracted so it
     * can be shared with the caller-scoped instantiate path in
     * {@link WasmtimeCallerAdapter}.
     */
    static void defineHostFunctions(Linker<Object> linker,
                                     List<HostFunctionDefinition> hostFunctions)
            throws ai.tegmentum.wasmtime4j.exception.WasmException {
        for (HostFunctionDefinition def : hostFunctions) {
            FunctionType funcType = new FunctionType(
                    convertToWasmTypes(def.parameterTypes()),
                    convertToWasmTypes(def.resultTypes()));

            linker.defineHostFunction(def.moduleName(), def.functionName(), funcType,
                    wasmArgs -> {
                        Object[] javaArgs = extractWasmValues(wasmArgs);
                        Object[] results = def.function().execute(javaArgs);
                        if (results == null || results.length == 0) {
                            return new WasmValue[0];
                        }
                        return convertToWasmValues(results, def.resultTypes());
                    });
        }
    }

    /**
     * Wire caller-aware host functions into the linker. Each definition is
     * registered as a wasmtime4j {@code CallerAwareHostFunction} — the
     * subclass of {@link HostFunction} whose {@code execute} runs with the
     * native {@link ai.tegmentum.wasmtime4j.func.Caller} available. The
     * native caller is looked up via wasmtime4j's
     * {@code CallerContextProvider} SPI (already wired for JNI in
     * wasmtime4j r.2), wrapped in a {@link WasmtimeCallerAdapter}, and
     * handed to the user's {@link CallerAwareHostFunction} implementation.
     */
    void defineCallerAwareHostFunctions(
            Linker<Object> linker,
            List<CallerAwareHostFunctionDefinition> callerAwareHostFunctions)
            throws ai.tegmentum.wasmtime4j.exception.WasmException {
        for (CallerAwareHostFunctionDefinition def : callerAwareHostFunctions) {
            FunctionType funcType = new FunctionType(
                    convertToWasmTypes(def.parameterTypes()),
                    convertToWasmTypes(def.resultTypes()));

            @SuppressWarnings({"unchecked", "rawtypes"})
            HostFunction impl = new HostFunction.CallerAwareHostFunction(
                    (HostFunction.MultiValueHostFunctionWithCaller<Object>) (nativeCaller,
                                                                              wasmArgs) -> {
                        @SuppressWarnings({"rawtypes"})
                        WasmtimeCallerAdapter callerAdapter = new WasmtimeCallerAdapter(
                                nativeCaller, runtime, engineConfig);
                        Object[] javaArgs = extractWasmValues(wasmArgs);
                        @SuppressWarnings({"unchecked", "rawtypes"})
                        CallerAwareHostFunction fn =
                                (CallerAwareHostFunction) def.function();
                        @SuppressWarnings("unchecked")
                        Object[] results = fn.execute(callerAdapter, javaArgs);
                        if (results == null || results.length == 0) {
                            return new WasmValue[0];
                        }
                        return convertToWasmValues(results, def.resultTypes());
                    });

            linker.defineHostFunction(def.moduleName(), def.functionName(), funcType, impl);
        }
    }

    /**
     * Wire typed {@link ExternImportDefinition} values into the wasmtime
     * linker. Each variant is unwrapped to its native wasmtime4j handle;
     * cross-provider values (memories/tables/globals produced by a different
     * provider adapter) throw {@link LinkingException} with clear coordinates.
     * {@link FunctionImport} is not yet supported and throws
     * {@link UnsupportedOperationException} — the api-layer {@link
     * ai.tegmentum.webassembly4j.api.Function Function} interface currently
     * has no {@code unwrap(Class)} method, so there is no way to reach the
     * underlying {@code WasmFunction}.
     */
    private void defineExternImports(Linker<Object> linker,
                                      List<ExternImportDefinition> externImports)
            throws ai.tegmentum.wasmtime4j.exception.WasmException {
        for (ExternImportDefinition def : externImports) {
            if (def instanceof MemoryImport) {
                MemoryImport mi = (MemoryImport) def;
                ai.tegmentum.wasmtime4j.WasmMemory nativeMemory =
                        mi.memory().unwrap(ai.tegmentum.wasmtime4j.WasmMemory.class)
                                .orElseThrow(() -> new LinkingException(
                                        "MemoryImport " + mi.moduleName() + "::" + mi.name()
                                                + " memory is not a wasmtime4j-backed instance"));
                linker.defineMemory(store, mi.moduleName(), mi.name(), nativeMemory);
            } else if (def instanceof TableImport) {
                TableImport ti = (TableImport) def;
                ai.tegmentum.wasmtime4j.WasmTable nativeTable =
                        ti.table().unwrap(ai.tegmentum.wasmtime4j.WasmTable.class)
                                .orElseThrow(() -> new LinkingException(
                                        "TableImport " + ti.moduleName() + "::" + ti.name()
                                                + " table is not a wasmtime4j-backed instance"));
                linker.defineTable(store, ti.moduleName(), ti.name(), nativeTable);
            } else if (def instanceof GlobalImport) {
                GlobalImport gi = (GlobalImport) def;
                ai.tegmentum.wasmtime4j.WasmGlobal nativeGlobal =
                        gi.global().unwrap(ai.tegmentum.wasmtime4j.WasmGlobal.class)
                                .orElseThrow(() -> new LinkingException(
                                        "GlobalImport " + gi.moduleName() + "::" + gi.name()
                                                + " global is not a wasmtime4j-backed instance"));
                linker.defineGlobal(store, gi.moduleName(), gi.name(), nativeGlobal);
            } else if (def instanceof FunctionImport) {
                FunctionImport fi = (FunctionImport) def;
                throw new UnsupportedOperationException(
                        "FunctionImport " + fi.moduleName() + "::" + fi.name()
                                + " is not yet supported by wasmtime4j-provider — the"
                                + " api-layer Function interface has no unwrap(Class)"
                                + " accessor. Use LinkingContext addHostFunction instead,"
                                + " or wait for the follow-up charter that adds"
                                + " Function.unwrap.");
            } else {
                throw new LinkingException(
                        "Unknown ExternImportDefinition variant: "
                                + (def == null ? "null" : def.getClass().getName()));
            }
        }
    }

    @Override
    public List<ExportDescriptor> exports() {
        List<ExportType> nativeExports = nativeModule.getExports();
        List<ExportDescriptor> result = new ArrayList<>(nativeExports.size());
        for (ExportType export : nativeExports) {
            result.add(convertExport(export));
        }
        return Collections.unmodifiableList(result);
    }

    @Override
    public List<ImportDescriptor> imports() {
        List<ImportType> nativeImports = nativeModule.getImports();
        List<ImportDescriptor> result = new ArrayList<>(nativeImports.size());
        for (ImportType imp : nativeImports) {
            result.add(convertImport(imp));
        }
        return Collections.unmodifiableList(result);
    }

    private static ExportDescriptor convertExport(ExportType export) {
        WasmType type = export.getType();
        WasmTypeKind kind = type.getKind();
        switch (kind) {
            case FUNCTION:
                FuncType funcType = (FuncType) type;
                return ExportDescriptor.function(export.getName(),
                        convertWasmValueTypes(funcType.getParams()),
                        convertWasmValueTypes(funcType.getResults()));
            case MEMORY:
                return ExportDescriptor.memory(export.getName());
            case TABLE:
                return ExportDescriptor.table(export.getName());
            case GLOBAL:
                return ExportDescriptor.global(export.getName(), ValueType.I32);
            default:
                return ExportDescriptor.memory(export.getName());
        }
    }

    private static ImportDescriptor convertImport(ImportType imp) {
        WasmType type = imp.getType();
        WasmTypeKind kind = type.getKind();
        switch (kind) {
            case FUNCTION:
                FuncType funcType = (FuncType) type;
                return ImportDescriptor.function(imp.getModuleName(), imp.getName(),
                        convertWasmValueTypes(funcType.getParams()),
                        convertWasmValueTypes(funcType.getResults()));
            case MEMORY:
                return ImportDescriptor.memory(imp.getModuleName(), imp.getName());
            case TABLE:
                return ImportDescriptor.table(imp.getModuleName(), imp.getName());
            case GLOBAL:
                return ImportDescriptor.global(imp.getModuleName(), imp.getName(), ValueType.I32);
            default:
                return ImportDescriptor.memory(imp.getModuleName(), imp.getName());
        }
    }

    private static ValueType[] convertWasmValueTypes(List<WasmValueType> types) {
        ValueType[] result = new ValueType[types.size()];
        for (int i = 0; i < types.size(); i++) {
            result[i] = convertFromWasmValueType(types.get(i));
        }
        return result;
    }

    private static ValueType convertFromWasmValueType(WasmValueType type) {
        if (type == WasmValueType.I32) return ValueType.I32;
        if (type == WasmValueType.I64) return ValueType.I64;
        if (type == WasmValueType.F32) return ValueType.F32;
        if (type == WasmValueType.F64) return ValueType.F64;
        if (type == WasmValueType.V128) return ValueType.V128;
        if (type == WasmValueType.FUNCREF) return ValueType.FUNCREF;
        if (type == WasmValueType.EXTERNREF) return ValueType.EXTERNREF;
        return ValueType.I32;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> Optional<T> extension(Class<T> extensionType) {
        if (extensionType == ai.tegmentum.webassembly4j.api.capability.FuelController.class
                && engine.isFuelEnabled()) {
            if (callerScoped) {
                // Caller-scoped modules borrow the caller's store; the
                // FuelController on the caller's store is reached via the
                // Caller api, not through this Module handle.
                return Optional.empty();
            }
            return Optional.of((T) new WasmtimeFuelController(store));
        }
        return Optional.empty();
    }

    /**
     * Provider escape — returns the underlying native handle when the
     * caller wants to reach the wasmtime4j-native Module directly (used by
     * {@link WasmtimeCallerAdapter#instantiate(Module, LinkingContext)} to
     * bridge back to the wasmtime4j linker).
     */
    @SuppressWarnings("unchecked")
    <T> Optional<T> unwrap(Class<T> nativeType) {
        if (nativeType.isInstance(nativeModule)) {
            return Optional.of((T) nativeModule);
        }
        return Optional.empty();
    }

    ai.tegmentum.wasmtime4j.Store store() {
        return store;
    }

    ai.tegmentum.wasmtime4j.Module nativeModule() {
        return nativeModule;
    }

    private static void configureNativeWasi(ai.tegmentum.wasmtime4j.wasi.WasiContext nativeWasi,
                                              ai.tegmentum.webassembly4j.api.WasiContext wasiCtx) {
        List<String> args = wasiCtx.args();
        if (!args.isEmpty()) {
            nativeWasi.setArgv(args.toArray(new String[0]));
        }

        java.util.Map<String, String> env = wasiCtx.env();
        if (!env.isEmpty()) {
            nativeWasi.setEnv(env);
        }

        if (wasiCtx.inheritStdin() || wasiCtx.inheritStdout() || wasiCtx.inheritStderr()) {
            nativeWasi.inheritStdio();
        }

        List<String> preopenDirs = wasiCtx.preopenDirs();
        for (String dir : preopenDirs) {
            try {
                nativeWasi.preopenedDir(java.nio.file.Paths.get(dir), dir);
            } catch (ai.tegmentum.wasmtime4j.exception.WasmException e) {
                throw new LinkingException("Failed to preopen directory: " + dir, e);
            }
        }
    }

    private static WasmValueType[] convertToWasmTypes(ValueType[] types) {
        WasmValueType[] result = new WasmValueType[types.length];
        for (int i = 0; i < types.length; i++) {
            result[i] = convertToWasmType(types[i]);
        }
        return result;
    }

    private static WasmValueType convertToWasmType(ValueType type) {
        switch (type) {
            case I32: return WasmValueType.I32;
            case I64: return WasmValueType.I64;
            case F32: return WasmValueType.F32;
            case F64: return WasmValueType.F64;
            case V128: return WasmValueType.V128;
            case FUNCREF: return WasmValueType.FUNCREF;
            case EXTERNREF: return WasmValueType.EXTERNREF;
            default: throw new IllegalArgumentException("Unknown type: " + type);
        }
    }

    private static Object[] extractWasmValues(WasmValue[] wasmValues) {
        Object[] result = new Object[wasmValues.length];
        for (int i = 0; i < wasmValues.length; i++) {
            switch (wasmValues[i].getType()) {
                case I32: result[i] = wasmValues[i].asInt(); break;
                case I64: result[i] = wasmValues[i].asLong(); break;
                case F32: result[i] = wasmValues[i].asFloat(); break;
                case F64: result[i] = wasmValues[i].asDouble(); break;
                default: result[i] = wasmValues[i]; break;
            }
        }
        return result;
    }

    private static WasmValue[] convertToWasmValues(Object[] values, ValueType[] types) {
        WasmValue[] result = new WasmValue[values.length];
        for (int i = 0; i < values.length; i++) {
            Number num = (Number) values[i];
            switch (types[i]) {
                case I32: result[i] = WasmValue.i32(num.intValue()); break;
                case I64: result[i] = WasmValue.i64(num.longValue()); break;
                case F32: result[i] = WasmValue.f32(num.floatValue()); break;
                case F64: result[i] = WasmValue.f64(num.doubleValue()); break;
                default: throw new IllegalArgumentException("Unsupported return type: " + types[i]);
            }
        }
        return result;
    }

    @Override
    public void close() {
        nativeModule.close();
        if (store != null && !callerScoped) {
            store.close();
        }
    }
}
