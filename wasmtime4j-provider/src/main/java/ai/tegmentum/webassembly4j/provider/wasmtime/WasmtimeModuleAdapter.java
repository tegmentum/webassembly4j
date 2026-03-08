package ai.tegmentum.webassembly4j.provider.wasmtime;

import ai.tegmentum.wasmtime4j.Linker;
import ai.tegmentum.wasmtime4j.WasmRuntime;
import ai.tegmentum.wasmtime4j.WasmValue;
import ai.tegmentum.wasmtime4j.WasmValueType;
import ai.tegmentum.wasmtime4j.type.FunctionType;
import ai.tegmentum.webassembly4j.api.HostFunctionDefinition;
import ai.tegmentum.webassembly4j.api.Instance;
import ai.tegmentum.webassembly4j.api.LinkingContext;
import ai.tegmentum.webassembly4j.api.Module;
import ai.tegmentum.webassembly4j.api.ValueType;
import ai.tegmentum.webassembly4j.api.exception.LinkingException;

import java.util.List;
import java.util.Optional;

final class WasmtimeModuleAdapter implements Module {

    private final WasmRuntime runtime;
    private final ai.tegmentum.wasmtime4j.Engine engine;
    private final ai.tegmentum.wasmtime4j.Module nativeModule;
    private final ai.tegmentum.wasmtime4j.Store store;
    private final ai.tegmentum.wasmtime4j.config.EngineConfig engineConfig;

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
    }

    @Override
    public Instance instantiate() {
        try {
            Linker<Object> linker = runtime.createLinker(engine);
            ai.tegmentum.wasmtime4j.Instance nativeInstance =
                    linker.instantiate(store, nativeModule);
            return new WasmtimeInstanceAdapter(nativeInstance);
        } catch (ai.tegmentum.wasmtime4j.exception.WasmException e) {
            throw new ai.tegmentum.webassembly4j.api.exception.InstantiationException(
                    "Failed to instantiate WebAssembly module", e);
        }
    }

    @Override
    public Instance instantiate(LinkingContext linkingContext) {
        if (linkingContext == null) {
            return instantiate();
        }

        List<HostFunctionDefinition> hostFunctions = linkingContext.hostFunctions();
        if (hostFunctions.isEmpty() && linkingContext.wasiContext() == null) {
            return instantiate();
        }

        try {
            Linker<Object> linker = runtime.createLinker(engine);

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

            ai.tegmentum.webassembly4j.api.WasiContext wasiCtx = linkingContext.wasiContext();
            if (wasiCtx != null) {
                linker.enableWasi();
            }

            ai.tegmentum.wasmtime4j.Instance nativeInstance =
                    linker.instantiate(store, nativeModule);
            return new WasmtimeInstanceAdapter(nativeInstance);
        } catch (ai.tegmentum.wasmtime4j.exception.WasmException e) {
            throw new LinkingException("Failed to instantiate with linking context", e);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> Optional<T> extension(Class<T> extensionType) {
        if (extensionType == ai.tegmentum.webassembly4j.api.capability.FuelController.class
                && engine.isFuelEnabled()) {
            return Optional.of((T) new WasmtimeFuelController(store));
        }
        return Optional.empty();
    }

    ai.tegmentum.wasmtime4j.Store store() {
        return store;
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
        store.close();
    }
}
