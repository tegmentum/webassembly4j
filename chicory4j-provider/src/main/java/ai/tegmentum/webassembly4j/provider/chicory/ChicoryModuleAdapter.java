package ai.tegmentum.webassembly4j.provider.chicory;

import ai.tegmentum.webassembly4j.api.ExportDescriptor;
import ai.tegmentum.webassembly4j.api.HostFunctionDefinition;
import ai.tegmentum.webassembly4j.api.ImportDescriptor;
import ai.tegmentum.webassembly4j.api.Instance;
import ai.tegmentum.webassembly4j.api.LinkingContext;
import ai.tegmentum.webassembly4j.api.Module;
import ai.tegmentum.webassembly4j.api.ValueType;
import ai.tegmentum.webassembly4j.api.exception.InstantiationException;
import ai.tegmentum.webassembly4j.api.exception.LinkingException;
import ai.tegmentum.webassembly4j.api.exception.UnsupportedFeatureException;
import com.dylibso.chicory.runtime.HostFunction;
import com.dylibso.chicory.runtime.ImportValues;
import com.dylibso.chicory.wasm.WasmModule;
import com.dylibso.chicory.wasm.types.Export;
import com.dylibso.chicory.wasm.types.ExternalType;
import com.dylibso.chicory.wasm.types.FunctionImport;
import com.dylibso.chicory.wasm.types.FunctionType;
import com.dylibso.chicory.wasm.types.Import;
import com.dylibso.chicory.wasm.types.ValType;
import com.dylibso.chicory.wasm.types.Value;

import java.util.ArrayList;
import java.util.Collections;
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

        if (linkingContext.wasiContext() != null) {
            throw new UnsupportedFeatureException(
                    "WASI is not supported by the Chicory provider");
        }

        List<HostFunctionDefinition> hostFunctions = linkingContext.hostFunctions();
        if (hostFunctions.isEmpty()) {
            return instantiate();
        }

        try {
            ImportValues.Builder importBuilder = ImportValues.builder();
            List<com.dylibso.chicory.runtime.ImportFunction> functions = new ArrayList<>();

            for (HostFunctionDefinition def : hostFunctions) {
                List<ValType> paramTypes = convertToChicoryTypes(def.parameterTypes());
                List<ValType> returnTypes = convertToChicoryTypes(def.resultTypes());

                HostFunction hostFunc = new HostFunction(
                        def.moduleName(), def.functionName(),
                        FunctionType.of(paramTypes, returnTypes),
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

    private static List<ValType> convertToChicoryTypes(ValueType[] types) {
        List<ValType> result = new ArrayList<>(types.length);
        for (ValueType type : types) {
            result.add(convertToChicoryType(type));
        }
        return result;
    }

    private static ValType convertToChicoryType(ValueType type) {
        switch (type) {
            case I32: return ValType.I32;
            case I64: return ValType.I64;
            case F32: return ValType.F32;
            case F64: return ValType.F64;
            case V128: return ValType.V128;
            case FUNCREF: return ValType.FuncRef;
            case EXTERNREF: return ValType.ExternRef;
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
    public List<ExportDescriptor> exports() {
        int count = wasmModule.exportSection().exportCount();
        if (count == 0) {
            return Collections.emptyList();
        }
        List<ExportDescriptor> result = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            Export export = wasmModule.exportSection().getExport(i);
            result.add(convertExport(export));
        }
        return Collections.unmodifiableList(result);
    }

    @Override
    public List<ImportDescriptor> imports() {
        int count = wasmModule.importSection().importCount();
        if (count == 0) {
            return Collections.emptyList();
        }
        List<ImportDescriptor> result = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            Import imp = wasmModule.importSection().getImport(i);
            result.add(convertImport(imp));
        }
        return Collections.unmodifiableList(result);
    }

    private ExportDescriptor convertExport(Export export) {
        ExternalType type = export.exportType();
        switch (type) {
            case FUNCTION:
                return convertFunctionExport(export);
            case MEMORY:
                return ExportDescriptor.memory(export.name());
            case TABLE:
                return ExportDescriptor.table(export.name());
            case GLOBAL:
                return ExportDescriptor.global(export.name(), ValueType.I32);
            default:
                return ExportDescriptor.memory(export.name());
        }
    }

    private ExportDescriptor convertFunctionExport(Export export) {
        int index = export.index();
        int importedFuncCount = wasmModule.importSection().count(ExternalType.FUNCTION);
        if (index < importedFuncCount) {
            // This export refers to an imported function
            return ExportDescriptor.function(export.name(),
                    new ValueType[0], new ValueType[0]);
        }
        int localIndex = index - importedFuncCount;
        if (localIndex < wasmModule.functionSection().functionCount()) {
            FunctionType funcType = wasmModule.functionSection()
                    .getFunctionType(localIndex, wasmModule.typeSection());
            return ExportDescriptor.function(export.name(),
                    convertChicoryTypes(funcType.params()),
                    convertChicoryTypes(funcType.returns()));
        }
        return ExportDescriptor.function(export.name(),
                new ValueType[0], new ValueType[0]);
    }

    private ImportDescriptor convertImport(Import imp) {
        ExternalType type = imp.importType();
        switch (type) {
            case FUNCTION:
                FunctionImport funcImport = (FunctionImport) imp;
                int typeIdx = funcImport.typeIndex();
                if (typeIdx < wasmModule.typeSection().typeCount()) {
                    FunctionType funcType = wasmModule.typeSection().getType(typeIdx);
                    return ImportDescriptor.function(imp.module(), imp.name(),
                            convertChicoryTypes(funcType.params()),
                            convertChicoryTypes(funcType.returns()));
                }
                return ImportDescriptor.function(imp.module(), imp.name(),
                        new ValueType[0], new ValueType[0]);
            case MEMORY:
                return ImportDescriptor.memory(imp.module(), imp.name());
            case TABLE:
                return ImportDescriptor.table(imp.module(), imp.name());
            case GLOBAL:
                return ImportDescriptor.global(imp.module(), imp.name(), ValueType.I32);
            default:
                return ImportDescriptor.memory(imp.module(), imp.name());
        }
    }

    private static ValueType[] convertChicoryTypes(List<ValType> types) {
        ValueType[] result = new ValueType[types.size()];
        for (int i = 0; i < types.size(); i++) {
            result[i] = convertChicoryType(types.get(i));
        }
        return result;
    }

    private static ValueType convertChicoryType(ValType type) {
        if (type .equals(ValType.I32)) return ValueType.I32;
        if (type .equals(ValType.I64)) return ValueType.I64;
        if (type .equals(ValType.F32)) return ValueType.F32;
        if (type .equals(ValType.F64)) return ValueType.F64;
        if (type .equals(ValType.V128)) return ValueType.V128;
        if (type .equals(ValType.FuncRef)) return ValueType.FUNCREF;
        if (type .equals(ValType.ExternRef)) return ValueType.EXTERNREF;
        return ValueType.I32;
    }

    @Override
    public void close() {
        // No native resources to release
    }
}
