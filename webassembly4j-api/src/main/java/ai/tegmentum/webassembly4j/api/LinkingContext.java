package ai.tegmentum.webassembly4j.api;

import java.util.Collections;
import java.util.List;

public interface LinkingContext {

    default List<HostFunctionDefinition> hostFunctions() {
        return Collections.emptyList();
    }

    /**
     * WIT-typed host functions to link into a component's imports. Providers may
     * ignore these when instantiating a core module. See {@link WitHostFunction}
     * for the value-type contract.
     */
    default List<WitHostFunctionDefinition> witHostFunctions() {
        return Collections.emptyList();
    }

    default WasiContext wasiContext() {
        return null;
    }

    /**
     * Optional WASI-NN configuration. When non-null, providers that support
     * wasi:nn enable the interface on their component linker before
     * instantiation; providers that do not surface
     * {@link ai.tegmentum.webassembly4j.api.exception.UnsupportedFeatureException}.
     * Defaults to {@code null} (WASI-NN disabled).
     */
    default WasiNnConfig wasiNnConfig() {
        return null;
    }
}
