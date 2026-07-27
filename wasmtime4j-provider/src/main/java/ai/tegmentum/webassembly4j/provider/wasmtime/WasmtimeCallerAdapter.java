package ai.tegmentum.webassembly4j.provider.wasmtime;

import ai.tegmentum.wasmtime4j.WasmFunction;
import ai.tegmentum.wasmtime4j.WasmMemory;
import ai.tegmentum.wasmtime4j.WasmRuntime;
import ai.tegmentum.wasmtime4j.WasmTable;
import ai.tegmentum.webassembly4j.api.Caller;
import ai.tegmentum.webassembly4j.api.Function;
import ai.tegmentum.webassembly4j.api.Global;
import ai.tegmentum.webassembly4j.api.Instance;
import ai.tegmentum.webassembly4j.api.LinkingContext;
import ai.tegmentum.webassembly4j.api.Memory;
import ai.tegmentum.webassembly4j.api.Module;
import ai.tegmentum.webassembly4j.api.Table;
import ai.tegmentum.webassembly4j.api.exception.ExecutionException;
import ai.tegmentum.webassembly4j.api.exception.LinkingException;

import java.util.Optional;

/**
 * Bridges wasmtime4j's caller-scoped host-callback handle
 * ({@link ai.tegmentum.wasmtime4j.func.Caller}) into the webassembly4j
 * {@link Caller} api. Instances are constructed by
 * {@link WasmtimeModuleAdapter} when it dispatches a
 * {@link ai.tegmentum.webassembly4j.api.CallerAwareHostFunctionDefinition}
 * and are only valid for the duration of the host callback that received
 * them — all scoped mutation methods route through the underlying
 * wasmtime4j Caller, which generation-checks use-after-return and throws
 * {@link IllegalStateException} on stale handles.
 *
 * <p>Because {@code ai.tegmentum.wasmtime4j.func.Caller} exposes no public
 * accessor for the caller's underlying {@link ai.tegmentum.wasmtime4j.Store},
 * this adapter carries only the {@link WasmRuntime} + engine references
 * needed to build caller-scoped modules and linkers; the store binding is
 * kept implicit in the native caller handle and used only via the borrow-
 * safe wasmtime4j scoped-mutation entrypoints. See charter
 * {@code f-webassembly4j-caller-aware-host-function-charter-2026-07-26.md}
 * for the reconciliation with the r.2 wasmtime4j implementation.
 */
final class WasmtimeCallerAdapter<T> implements Caller<T> {

    private final ai.tegmentum.wasmtime4j.func.Caller<T> nativeCaller;
    private final WasmRuntime runtime;
    private final ai.tegmentum.wasmtime4j.config.EngineConfig engineConfig;

    WasmtimeCallerAdapter(ai.tegmentum.wasmtime4j.func.Caller<T> nativeCaller,
                          WasmRuntime runtime,
                          ai.tegmentum.wasmtime4j.config.EngineConfig engineConfig) {
        this.nativeCaller = nativeCaller;
        this.runtime = runtime;
        this.engineConfig = engineConfig;
    }

    @Override
    public T data() {
        return nativeCaller.data();
    }

    @Override
    public Optional<Memory> getMemory(String name) {
        return nativeCaller.getMemory(name).map(WasmtimeMemoryAdapter::new);
    }

    @Override
    public Optional<Table> getTable(String name) {
        return nativeCaller.getTable(name).map(WasmtimeTableAdapter::new);
    }

    @Override
    public Optional<Function> getFunction(String name) {
        return nativeCaller.getFunction(name).map(WasmtimeFunctionAdapter::new);
    }

    @Override
    public Optional<Global> getGlobal(String name) {
        return nativeCaller.getGlobal(name).map(WasmtimeGlobalAdapter::new);
    }

    /**
     * Compile a module using the caller's engine. Returns a caller-scoped
     * {@link WasmtimeModuleAdapter} — it carries no owning {@link
     * ai.tegmentum.wasmtime4j.Store}, so the caller MUST route the
     * subsequent instantiation through {@link #instantiate(Module, LinkingContext)}
     * rather than calling the module's own {@code instantiate()} methods
     * (those throw for caller-scoped modules).
     */
    @Override
    public Module compileModule(byte[] wasmBytes) {
        try {
            ai.tegmentum.wasmtime4j.Module nativeModule = nativeCaller.compileModule(wasmBytes);
            ai.tegmentum.wasmtime4j.Engine callerEngine = nativeCaller.engine();
            return WasmtimeModuleAdapter.callerScoped(
                    runtime, callerEngine, nativeModule, engineConfig);
        } catch (IllegalStateException e) {
            throw e;
        } catch (ai.tegmentum.wasmtime4j.exception.WasmException e) {
            throw new ExecutionException(
                    "Caller-scoped compileModule failed: " + e.getMessage(), e);
        } catch (UnsupportedOperationException e) {
            throw e;
        }
    }

    /**
     * Instantiate a module in the caller's store via the borrow-safe
     * scoped-instantiate path. The module's imports are resolved through a
     * transient {@link ai.tegmentum.wasmtime4j.Linker} built from the
     * caller's engine; the pre-linked
     * {@link ai.tegmentum.wasmtime4j.InstancePre} is then passed to
     * {@link ai.tegmentum.wasmtime4j.func.Caller#instantiate(ai.tegmentum.wasmtime4j.InstancePre)}
     * which uses {@code caller.as_context_mut()} instead of re-acquiring
     * the store lock.
     *
     * <p>Only the host-function and caller-aware-host-function slices of
     * the passed {@link LinkingContext} are wired via the transient linker
     * — extern imports (memories, tables, globals) require a live store
     * binding that isn't reachable from a wasmtime4j Caller, so passing
     * non-empty {@code externImports()} throws {@link LinkingException}.
     * The typical JIT-install-loop case (compile + instantiate against
     * user host imports, then table-install into the caller's outer
     * table) is unaffected by this restriction.
     */
    @Override
    public Instance instantiate(Module module, LinkingContext imports) {
        if (module == null) {
            throw new IllegalArgumentException("module must not be null");
        }
        if (!(module instanceof WasmtimeModuleAdapter)) {
            throw new LinkingException(
                    "Caller.instantiate: module is not backed by a"
                            + " wasmtime4j-provider Module (got "
                            + module.getClass().getName() + ")");
        }
        ai.tegmentum.wasmtime4j.Module nativeModule =
                ((WasmtimeModuleAdapter) module).nativeModule();
        ai.tegmentum.wasmtime4j.Engine callerEngine = nativeCaller.engine();
        try {
            ai.tegmentum.wasmtime4j.Linker<Object> linker = runtime.createLinker(callerEngine);
            if (imports != null) {
                if (!imports.externImports().isEmpty()) {
                    throw new LinkingException(
                            "Caller.instantiate does not support externImports"
                                    + " (memory/table/global imports need a live"
                                    + " store binding that the caller-scoped path"
                                    + " does not expose). Provide these via"
                                    + " hostFunctions or caller-aware host"
                                    + " functions instead.");
                }
                WasmtimeModuleAdapter.defineHostFunctions(linker, imports.hostFunctions());
                // Nested caller-aware host functions on an inner module
                // instantiated inside a callback are permitted — they
                // will fire against the inner instance's own callback
                // context when it invokes them.
                for (ai.tegmentum.webassembly4j.api.CallerAwareHostFunctionDefinition def
                        : imports.callerAwareHostFunctions()) {
                    // Route through the same package-private helper used
                    // by WasmtimeModuleAdapter — the nested caller adapter
                    // is minted per-invocation from the wasmtime4j callback
                    // context, not from the outer caller.
                    defineNestedCallerAware(linker, def);
                }
            }
            ai.tegmentum.wasmtime4j.InstancePre pre = linker.instantiatePre(nativeModule);
            ai.tegmentum.wasmtime4j.Instance nativeInstance = nativeCaller.instantiate(pre);
            return new WasmtimeInstanceAdapter(nativeInstance, runtime, callerEngine);
        } catch (IllegalStateException e) {
            throw e;
        } catch (UnsupportedOperationException e) {
            throw e;
        } catch (ai.tegmentum.wasmtime4j.exception.WasmException e) {
            throw new LinkingException(
                    "Caller-scoped instantiate failed: " + e.getMessage(), e);
        }
    }

    /**
     * Register a caller-aware host function on the transient linker built
     * for the caller-scoped inner instantiate. Mirrors the wiring in
     * {@link WasmtimeModuleAdapter#defineCallerAwareHostFunctions} but is
     * inlined here so this path doesn't need a WasmtimeModuleAdapter
     * instance (there is no api-level module yet at the caller-scope).
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private void defineNestedCallerAware(
            ai.tegmentum.wasmtime4j.Linker<Object> linker,
            ai.tegmentum.webassembly4j.api.CallerAwareHostFunctionDefinition def)
            throws ai.tegmentum.wasmtime4j.exception.WasmException {
        ai.tegmentum.wasmtime4j.WasmValueType[] paramTypes =
                convertToWasmTypes(def.parameterTypes());
        ai.tegmentum.wasmtime4j.WasmValueType[] resultTypes =
                convertToWasmTypes(def.resultTypes());
        ai.tegmentum.wasmtime4j.type.FunctionType funcType =
                new ai.tegmentum.wasmtime4j.type.FunctionType(paramTypes, resultTypes);

        ai.tegmentum.wasmtime4j.func.HostFunction impl =
                new ai.tegmentum.wasmtime4j.func.HostFunction.CallerAwareHostFunction(
                        (ai.tegmentum.wasmtime4j.func.HostFunction
                                .MultiValueHostFunctionWithCaller<Object>) (innerCaller,
                                                                             wasmArgs) -> {
                            WasmtimeCallerAdapter adapter = new WasmtimeCallerAdapter(
                                    innerCaller, runtime, engineConfig);
                            Object[] javaArgs = extractWasmValues(wasmArgs);
                            ai.tegmentum.webassembly4j.api.CallerAwareHostFunction fn =
                                    (ai.tegmentum.webassembly4j.api.CallerAwareHostFunction)
                                            def.function();
                            @SuppressWarnings("unchecked")
                            Object[] results = fn.execute(adapter, javaArgs);
                            if (results == null || results.length == 0) {
                                return new ai.tegmentum.wasmtime4j.WasmValue[0];
                            }
                            return convertToWasmValues(results, def.resultTypes());
                        });
        linker.defineHostFunction(def.moduleName(), def.functionName(), funcType, impl);
    }

    private static ai.tegmentum.wasmtime4j.WasmValueType[] convertToWasmTypes(
            ai.tegmentum.webassembly4j.api.ValueType[] types) {
        ai.tegmentum.wasmtime4j.WasmValueType[] result =
                new ai.tegmentum.wasmtime4j.WasmValueType[types.length];
        for (int i = 0; i < types.length; i++) {
            result[i] = convertToWasmType(types[i]);
        }
        return result;
    }

    private static ai.tegmentum.wasmtime4j.WasmValueType convertToWasmType(
            ai.tegmentum.webassembly4j.api.ValueType type) {
        switch (type) {
            case I32: return ai.tegmentum.wasmtime4j.WasmValueType.I32;
            case I64: return ai.tegmentum.wasmtime4j.WasmValueType.I64;
            case F32: return ai.tegmentum.wasmtime4j.WasmValueType.F32;
            case F64: return ai.tegmentum.wasmtime4j.WasmValueType.F64;
            case V128: return ai.tegmentum.wasmtime4j.WasmValueType.V128;
            case FUNCREF: return ai.tegmentum.wasmtime4j.WasmValueType.FUNCREF;
            case EXTERNREF: return ai.tegmentum.wasmtime4j.WasmValueType.EXTERNREF;
            default: throw new IllegalArgumentException("Unknown type: " + type);
        }
    }

    private static Object[] extractWasmValues(ai.tegmentum.wasmtime4j.WasmValue[] wasmValues) {
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

    private static ai.tegmentum.wasmtime4j.WasmValue[] convertToWasmValues(
            Object[] values, ai.tegmentum.webassembly4j.api.ValueType[] types) {
        ai.tegmentum.wasmtime4j.WasmValue[] result =
                new ai.tegmentum.wasmtime4j.WasmValue[values.length];
        for (int i = 0; i < values.length; i++) {
            Number num = (Number) values[i];
            switch (types[i]) {
                case I32: result[i] =
                        ai.tegmentum.wasmtime4j.WasmValue.i32(num.intValue()); break;
                case I64: result[i] =
                        ai.tegmentum.wasmtime4j.WasmValue.i64(num.longValue()); break;
                case F32: result[i] =
                        ai.tegmentum.wasmtime4j.WasmValue.f32(num.floatValue()); break;
                case F64: result[i] =
                        ai.tegmentum.wasmtime4j.WasmValue.f64(num.doubleValue()); break;
                default: throw new IllegalArgumentException("Unsupported return type: " + types[i]);
            }
        }
        return result;
    }

    @Override
    public int growTable(Table table, int delta, Object init) {
        if (table == null) {
            throw new IllegalArgumentException("table must not be null");
        }
        WasmTable nativeTable = table.unwrap(WasmTable.class)
                .orElseThrow(() -> new LinkingException(
                        "Caller.growTable: table is not backed by a wasmtime4j"
                                + " WasmTable"));
        Object nativeInit = unwrapTableSlotValue(init);
        try {
            return nativeCaller.growTable(nativeTable, delta, nativeInit);
        } catch (IllegalStateException e) {
            throw e;
        } catch (UnsupportedOperationException e) {
            throw e;
        } catch (ai.tegmentum.wasmtime4j.exception.WasmException e) {
            throw new ExecutionException(
                    "Caller-scoped growTable failed: " + e.getMessage(), e);
        }
    }

    @Override
    public void setTableElement(Table table, int index, Object value) {
        if (table == null) {
            throw new IllegalArgumentException("table must not be null");
        }
        WasmTable nativeTable = table.unwrap(WasmTable.class)
                .orElseThrow(() -> new LinkingException(
                        "Caller.setTableElement: table is not backed by a"
                                + " wasmtime4j WasmTable"));
        Object nativeValue = unwrapTableSlotValue(value);
        try {
            nativeCaller.setTableElement(nativeTable, index, nativeValue);
        } catch (IllegalStateException e) {
            throw e;
        } catch (UnsupportedOperationException e) {
            throw e;
        } catch (ai.tegmentum.wasmtime4j.exception.WasmException e) {
            throw new ExecutionException(
                    "Caller-scoped setTableElement failed: " + e.getMessage(), e);
        }
    }

    @Override
    public long growMemory(Memory memory, long deltaPages) {
        if (memory == null) {
            throw new IllegalArgumentException("memory must not be null");
        }
        WasmMemory nativeMemory = memory.unwrap(WasmMemory.class)
                .orElseThrow(() -> new LinkingException(
                        "Caller.growMemory: memory is not backed by a wasmtime4j"
                                + " WasmMemory"));
        try {
            return nativeCaller.growMemory(nativeMemory, deltaPages);
        } catch (IllegalStateException e) {
            throw e;
        } catch (UnsupportedOperationException e) {
            throw e;
        } catch (ai.tegmentum.wasmtime4j.exception.WasmException e) {
            throw new ExecutionException(
                    "Caller-scoped growMemory failed: " + e.getMessage(), e);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public <U> Optional<U> unwrap(Class<U> nativeType) {
        if (nativeType.isInstance(nativeCaller)) {
            return Optional.of((U) nativeCaller);
        }
        return Optional.empty();
    }

    /**
     * Coerce a Java-side table slot value into the raw native handle
     * expected by wasmtime4j's caller-scoped table mutation entrypoints.
     * Nulls pass through unchanged (representing a null funcref /
     * externref); webassembly4j {@link Function} handles are unwrapped to
     * their underlying {@link WasmFunction}. Anything else is passed
     * through and will be validated by the native layer.
     */
    private static Object unwrapTableSlotValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Function) {
            return ((Function) value).unwrap(WasmFunction.class)
                    .orElseThrow(() -> new LinkingException(
                            "Caller table slot Function value is not backed by a"
                                    + " wasmtime4j WasmFunction"));
        }
        return value;
    }
}
