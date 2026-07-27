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

    /**
     * Typed extern-import definitions to wire during
     * {@link Module#instantiate(LinkingContext)}. Providers consume these to
     * satisfy imports of memories, tables, globals, and functions from
     * external instances (WASI shim wiring, cross-instance shared memory,
     * function-table plug-in registration, etc.).
     *
     * <p>Providers that do not support a given variant should throw at
     * {@code instantiate} time. Providers that do not support any variant
     * behave as if this list were empty.
     *
     * @return an unmodifiable list of extern-import definitions; empty by
     *         default so existing implementations remain valid.
     * @since 2.5.2
     */
    default List<ExternImportDefinition> externImports() {
        return Collections.emptyList();
    }

    /**
     * Caller-aware host functions to link into a module's imports. Each
     * definition wraps a {@link CallerAwareHostFunction} whose implementation
     * receives a {@link Caller} handle for scoped access to the calling
     * instance's exports, engine, and store — safe from within a host
     * callback frame (see
     * {@code doctrine/specs/f-webassembly4j-caller-aware-host-function-charter-2026-07-26.md}).
     *
     * <p>Providers that do not support caller-aware host functions throw
     * {@link ai.tegmentum.webassembly4j.api.exception.UnsupportedFeatureException}
     * at {@code instantiate} time when this list is non-empty; providers
     * that support it consume the list alongside {@link #hostFunctions()},
     * {@link #wasiContext()}, and {@link #externImports()}.
     *
     * @return an unmodifiable list of caller-aware host function definitions;
     *         empty by default so existing implementations remain valid.
     * @since 2.5.2
     */
    default List<CallerAwareHostFunctionDefinition> callerAwareHostFunctions() {
        return Collections.emptyList();
    }
}
