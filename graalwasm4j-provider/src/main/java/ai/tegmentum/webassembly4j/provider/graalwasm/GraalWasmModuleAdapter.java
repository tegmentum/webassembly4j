package ai.tegmentum.webassembly4j.provider.graalwasm;

import ai.tegmentum.webassembly4j.api.HostFunctionDefinition;
import ai.tegmentum.webassembly4j.api.Instance;
import ai.tegmentum.webassembly4j.api.LinkingContext;
import ai.tegmentum.webassembly4j.api.Module;
import ai.tegmentum.webassembly4j.api.exception.InstantiationException;
import ai.tegmentum.webassembly4j.api.exception.LinkingException;
import ai.tegmentum.webassembly4j.api.exception.UnsupportedFeatureException;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.PolyglotAccess;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.io.ByteSequence;

import java.io.IOException;
import java.util.List;

final class GraalWasmModuleAdapter implements Module {

    private final byte[] wasmBytes;

    GraalWasmModuleAdapter(byte[] wasmBytes) {
        this.wasmBytes = wasmBytes.clone();
    }

    @Override
    public Instance instantiate() {
        try {
            // Allow access to the "wasm" polyglot bindings so the host can reach
            // the exported WebAssembly object (e.g. its mem_grow executable).
            Context context = Context.newBuilder("wasm")
                    .allowPolyglotAccess(PolyglotAccess.newBuilder()
                            .allowBindingsAccess("wasm")
                            .build())
                    .build();
            Source source = Source.newBuilder("wasm",
                    ByteSequence.create(wasmBytes), "module").build();
            Value moduleInstance = context.eval(source);
            return new GraalWasmInstanceAdapter(context, moduleInstance);
        } catch (IOException e) {
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
                    "WASI is not supported by the GraalWasm provider");
        }

        List<HostFunctionDefinition> hostFunctions = linkingContext.hostFunctions();
        if (hostFunctions.isEmpty()) {
            return instantiate();
        }

        throw new LinkingException(
                "GraalWasm Polyglot API does not support Java host function linking. "
                + "Use a native WASM import module instead.");
    }

    @Override
    public void close() {
        // Nothing to close
    }
}
