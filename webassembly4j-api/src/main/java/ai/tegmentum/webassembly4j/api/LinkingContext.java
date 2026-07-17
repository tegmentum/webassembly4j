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

    /**
     * @return {@code true} if this linking context's provider ships a native
     *         library built with wasi:nn support. Callers should probe before
     *         invoking {@link DefaultLinkingContext.Builder#enableWasiNn(WasiNnConfig)};
     *         a false return means enableWasiNn will throw at instantiation time.
     *         Default {@code false} so providers that don't override see the safe
     *         (no-support) answer.
     * @since 2.4.1
     */
    default boolean supportsWasiNn() {
        return false;
    }
}
