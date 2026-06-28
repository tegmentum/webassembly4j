package ai.tegmentum.webassembly4j.provider.wasm3;

import ai.tegmentum.wasm34j.FunctionType;
import ai.tegmentum.wasm34j.WasmImports;
import ai.tegmentum.wasm34j.WasmValue;
import ai.tegmentum.wasm34j.WebAssemblyModule;
import ai.tegmentum.webassembly4j.api.HostFunctionDefinition;
import ai.tegmentum.webassembly4j.api.Instance;
import ai.tegmentum.webassembly4j.api.LinkingContext;
import ai.tegmentum.webassembly4j.api.Module;
import ai.tegmentum.webassembly4j.api.ValueType;
import ai.tegmentum.webassembly4j.api.exception.InstantiationException;
import ai.tegmentum.webassembly4j.api.exception.LinkingException;

import java.util.List;

/** {@link Module} backed by a wasm34j {@link WebAssemblyModule}. */
final class Wasm3ModuleAdapter implements Module {

    private final WebAssemblyModule nativeModule;

    Wasm3ModuleAdapter(final WebAssemblyModule nativeModule) {
        this.nativeModule = nativeModule;
    }

    @Override
    public Instance instantiate() {
        try {
            return new Wasm3InstanceAdapter(nativeModule.instantiate());
        } catch (final ai.tegmentum.wasm34j.exception.WasmException e) {
            throw new InstantiationException("Failed to instantiate WebAssembly module", e);
        }
    }

    @Override
    public Instance instantiate(final LinkingContext linkingContext) {
        if (linkingContext == null) {
            return instantiate();
        }
        final List<HostFunctionDefinition> hostFunctions = linkingContext.hostFunctions();
        if (hostFunctions.isEmpty()) {
            return instantiate();
        }
        try {
            return new Wasm3InstanceAdapter(nativeModule.instantiate(buildImports(hostFunctions)));
        } catch (final ai.tegmentum.wasm34j.exception.WasmException e) {
            throw new LinkingException("Failed to instantiate with linking context", e);
        }
    }

    private static WasmImports buildImports(final List<HostFunctionDefinition> hostFunctions) {
        final WasmImports.Builder builder = WasmImports.builder();
        for (final HostFunctionDefinition def : hostFunctions) {
            final ValueType[] resultTypes = def.resultTypes();
            final FunctionType type = FunctionType.of(
                    Wasm3Types.toNative(def.parameterTypes()), Wasm3Types.toNative(resultTypes));

            builder.function(def.moduleName(), def.functionName(), type, args -> {
                final Object[] in = new Object[args.length];
                for (int i = 0; i < args.length; i++) {
                    in[i] = args[i].boxed();
                }
                Object[] out = def.function().execute(in);
                if (out == null) {
                    out = new Object[0];
                }
                final WasmValue[] results = new WasmValue[out.length];
                for (int i = 0; i < out.length; i++) {
                    results[i] = Wasm3Types.toWasmValue(resultTypes[i], out[i]);
                }
                return results;
            });
        }
        return builder.build();
    }

    @Override
    public void close() {
        nativeModule.close();
    }
}
