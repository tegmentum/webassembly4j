package ai.tegmentum.webassembly4j.provider.wamr;

import ai.tegmentum.wamr4j.FunctionSignature;
import ai.tegmentum.wamr4j.WebAssemblyModule;
import ai.tegmentum.webassembly4j.api.HostFunctionDefinition;
import ai.tegmentum.webassembly4j.api.Instance;
import ai.tegmentum.webassembly4j.api.LinkingContext;
import ai.tegmentum.webassembly4j.api.Module;
import ai.tegmentum.webassembly4j.api.ValueType;
import ai.tegmentum.webassembly4j.api.exception.InstantiationException;
import ai.tegmentum.webassembly4j.api.exception.LinkingException;
import ai.tegmentum.webassembly4j.provider.wamr.config.WamrConfig;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class WamrModuleAdapter implements Module {

    private final WebAssemblyModule nativeModule;
    private final WamrConfig wamrConfig;

    WamrModuleAdapter(WebAssemblyModule nativeModule, WamrConfig wamrConfig) {
        this.nativeModule = nativeModule;
        this.wamrConfig = wamrConfig;
    }

    @Override
    public Instance instantiate() {
        try {
            ai.tegmentum.wamr4j.WebAssemblyInstance nativeInstance;
            if (wamrConfig != null && hasExtendedConfig()) {
                nativeInstance = nativeModule.instantiateEx(
                        wamrConfig.defaultStackSize().orElse(65536),
                        wamrConfig.hostManagedHeapSize().orElse(0),
                        wamrConfig.maxMemoryPages().orElse(0));
            } else {
                nativeInstance = nativeModule.instantiate();
            }
            return new WamrInstanceAdapter(nativeInstance);
        } catch (ai.tegmentum.wamr4j.exception.WasmRuntimeException e) {
            throw new InstantiationException(
                    "Failed to instantiate WebAssembly module", e);
        }
    }

    private boolean hasExtendedConfig() {
        return wamrConfig.defaultStackSize().isPresent()
                || wamrConfig.hostManagedHeapSize().isPresent()
                || wamrConfig.maxMemoryPages().isPresent();
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
            if (linkingContext.wasiContext() != null) {
                ai.tegmentum.webassembly4j.api.WasiContext wasiCtx = linkingContext.wasiContext();
                ai.tegmentum.wamr4j.WasiConfiguration wasiConfig =
                        new ai.tegmentum.wamr4j.WasiConfiguration();

                java.util.List<String> wasiArgs = wasiCtx.args();
                if (!wasiArgs.isEmpty()) {
                    wasiConfig.setArgs(wasiArgs.toArray(new String[0]));
                }

                java.util.Map<String, String> wasiEnv = wasiCtx.env();
                if (!wasiEnv.isEmpty()) {
                    String[] envVars = wasiEnv.entrySet().stream()
                            .map(e -> e.getKey() + "=" + e.getValue())
                            .toArray(String[]::new);
                    wasiConfig.setEnvVars(envVars);
                }

                java.util.List<String> preopenDirs = wasiCtx.preopenDirs();
                if (!preopenDirs.isEmpty()) {
                    wasiConfig.setPreopens(preopenDirs.toArray(new String[0]));
                }

                if (wasiCtx.inheritStdin()) {
                    wasiConfig.setStdinFd(0);
                }
                if (wasiCtx.inheritStdout()) {
                    wasiConfig.setStdoutFd(1);
                }
                if (wasiCtx.inheritStderr()) {
                    wasiConfig.setStderrFd(2);
                }

                nativeModule.configureWasi(wasiConfig);
            }

            if (!hostFunctions.isEmpty()) {
                Map<String, Map<String, Object>> imports = buildImports(hostFunctions);
                ai.tegmentum.wamr4j.WebAssemblyInstance nativeInstance =
                        nativeModule.instantiate(imports);
                return new WamrInstanceAdapter(nativeInstance);
            }

            return instantiate();
        } catch (ai.tegmentum.wamr4j.exception.WasmRuntimeException e) {
            throw new LinkingException("Failed to instantiate with linking context", e);
        }
    }

    private Map<String, Map<String, Object>> buildImports(
            List<HostFunctionDefinition> hostFunctions) {
        Map<String, Map<String, Object>> imports = new LinkedHashMap<>();

        for (HostFunctionDefinition def : hostFunctions) {
            FunctionSignature sig = new FunctionSignature(
                    convertToWamrTypes(def.parameterTypes()),
                    convertToWamrTypes(def.resultTypes()));

            ai.tegmentum.wamr4j.HostFunction hostFunc =
                    new ai.tegmentum.wamr4j.HostFunction(sig, args -> {
                        Object[] results = def.function().execute(args);
                        if (results == null || results.length == 0) {
                            return null;
                        }
                        return results.length == 1 ? results[0] : results;
                    });

            imports.computeIfAbsent(def.moduleName(), k -> new LinkedHashMap<>())
                    .put(def.functionName(), hostFunc);
        }

        return imports;
    }

    private static ai.tegmentum.wamr4j.ValueType[] convertToWamrTypes(ValueType[] types) {
        ai.tegmentum.wamr4j.ValueType[] result = new ai.tegmentum.wamr4j.ValueType[types.length];
        for (int i = 0; i < types.length; i++) {
            result[i] = convertToWamrType(types[i]);
        }
        return result;
    }

    private static ai.tegmentum.wamr4j.ValueType convertToWamrType(ValueType type) {
        switch (type) {
            case I32: return ai.tegmentum.wamr4j.ValueType.I32;
            case I64: return ai.tegmentum.wamr4j.ValueType.I64;
            case F32: return ai.tegmentum.wamr4j.ValueType.F32;
            case F64: return ai.tegmentum.wamr4j.ValueType.F64;
            default: throw new IllegalArgumentException("Unsupported WAMR type: " + type);
        }
    }

    @Override
    public void close() {
        nativeModule.close();
    }
}
