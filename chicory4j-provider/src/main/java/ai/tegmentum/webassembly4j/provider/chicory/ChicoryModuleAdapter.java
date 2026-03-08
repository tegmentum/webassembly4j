package ai.tegmentum.webassembly4j.provider.chicory;

import ai.tegmentum.webassembly4j.api.HostFunctionDefinition;
import ai.tegmentum.webassembly4j.api.Instance;
import ai.tegmentum.webassembly4j.api.LinkingContext;
import ai.tegmentum.webassembly4j.api.Module;
import ai.tegmentum.webassembly4j.api.ValueType;
import ai.tegmentum.webassembly4j.api.exception.InstantiationException;
import ai.tegmentum.webassembly4j.api.exception.LinkingException;
import com.dylibso.chicory.runtime.HostFunction;
import com.dylibso.chicory.runtime.ImportValues;
import com.dylibso.chicory.wasm.WasmModule;
import com.dylibso.chicory.wasm.types.Value;

import java.util.ArrayList;
import java.util.List;

final class ChicoryModuleAdapter implements Module {

    private final WasmModule wasmModule;

    ChicoryModuleAdapter(WasmModule wasmModule) {
        this.wasmModule = wasmModule;
    }

    @Override
    public Instance instantiate() {
        try {
            com.dylibso.chicory.runtime.Instance nativeInstance =
                    com.dylibso.chicory.runtime.Instance.builder(wasmModule).build();
            return new ChicoryInstanceAdapter(nativeInstance);
        } catch (Exception e) {
            throw new InstantiationException(
                    "Failed to instantiate WebAssembly module", e);
        }
    }

    @Override
    public Instance instantiate(LinkingContext linkingContext) {
        if (linkingContext == null) {
            return instantiate();
        }

        List<HostFunctionDefinition> hostFunctions = linkingContext.hostFunctions();
        if (hostFunctions.isEmpty()) {
            return instantiate();
        }

        try {
            ImportValues.Builder importBuilder = ImportValues.builder();
            List<com.dylibso.chicory.runtime.ImportFunction> functions = new ArrayList<>();

            for (HostFunctionDefinition def : hostFunctions) {
                List<com.dylibso.chicory.wasm.types.ValueType> paramTypes =
                        convertToChicoryTypes(def.parameterTypes());
                List<com.dylibso.chicory.wasm.types.ValueType> returnTypes =
                        convertToChicoryTypes(def.resultTypes());

                HostFunction hostFunc = new HostFunction(
                        def.moduleName(), def.functionName(),
                        paramTypes, returnTypes,
                        (instance, args) -> {
                            Object[] javaArgs = extractLongValues(args, def.parameterTypes());
                            Object[] results = def.function().execute(javaArgs);
                            if (results == null || results.length == 0) {
                                return new long[0];
                            }
                            return convertToLongs(results, def.resultTypes());
                        });
                functions.add(hostFunc);
            }

            importBuilder.withFunctions(functions);
            ImportValues imports = importBuilder.build();

            com.dylibso.chicory.runtime.Instance nativeInstance =
                    com.dylibso.chicory.runtime.Instance.builder(wasmModule)
                            .withImportValues(imports)
                            .build();
            return new ChicoryInstanceAdapter(nativeInstance);
        } catch (Exception e) {
            throw new LinkingException("Failed to instantiate with linking context", e);
        }
    }

    private static List<com.dylibso.chicory.wasm.types.ValueType> convertToChicoryTypes(
            ValueType[] types) {
        List<com.dylibso.chicory.wasm.types.ValueType> result = new ArrayList<>(types.length);
        for (ValueType type : types) {
            result.add(convertToChicoryType(type));
        }
        return result;
    }

    private static com.dylibso.chicory.wasm.types.ValueType convertToChicoryType(ValueType type) {
        switch (type) {
            case I32: return com.dylibso.chicory.wasm.types.ValueType.I32;
            case I64: return com.dylibso.chicory.wasm.types.ValueType.I64;
            case F32: return com.dylibso.chicory.wasm.types.ValueType.F32;
            case F64: return com.dylibso.chicory.wasm.types.ValueType.F64;
            case V128: return com.dylibso.chicory.wasm.types.ValueType.V128;
            case FUNCREF: return com.dylibso.chicory.wasm.types.ValueType.FuncRef;
            case EXTERNREF: return com.dylibso.chicory.wasm.types.ValueType.ExternRef;
            default: throw new IllegalArgumentException("Unknown type: " + type);
        }
    }

    private static Object[] extractLongValues(long[] args, ValueType[] types) {
        Object[] result = new Object[args.length];
        for (int i = 0; i < args.length; i++) {
            switch (types[i]) {
                case I32: result[i] = (int) args[i]; break;
                case I64: result[i] = args[i]; break;
                case F32: result[i] = Value.longToFloat(args[i]); break;
                case F64: result[i] = Value.longToDouble(args[i]); break;
                default: result[i] = args[i]; break;
            }
        }
        return result;
    }

    private static long[] convertToLongs(Object[] values, ValueType[] types) {
        long[] result = new long[values.length];
        for (int i = 0; i < values.length; i++) {
            Number num = (Number) values[i];
            switch (types[i]) {
                case I32: result[i] = num.intValue(); break;
                case I64: result[i] = num.longValue(); break;
                case F32: result[i] = Value.floatToLong(num.floatValue()); break;
                case F64: result[i] = Value.doubleToLong(num.doubleValue()); break;
                default: result[i] = num.longValue(); break;
            }
        }
        return result;
    }

    @Override
    public void close() {
        // No native resources to release
    }
}
