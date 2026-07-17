package ai.tegmentum.webassembly4j.api;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * A compiled WebAssembly component (Component Model).
 *
 * <p>Components are higher-level than core modules and use WIT interfaces
 * for typed imports and exports. They support rich types (strings, records,
 * variants, etc.) rather than just numeric values.
 */
public interface Component extends AutoCloseable {

    /**
     * Creates a new instance of this component with no imports.
     */
    ComponentInstance instantiate();

    /**
     * Creates a new instance of this component with the given linking context.
     */
    ComponentInstance instantiate(LinkingContext linkingContext);

    /**
     * Creates a new instance of this component with the given configuration.
     * Providers that do not support component configuration ignore the config
     * and delegate to {@link #instantiate()}.
     */
    default ComponentInstance instantiate(ai.tegmentum.webassembly4j.api.config.ComponentConfig config) {
        return instantiate();
    }

    /**
     * Creates a new instance of this component with the given linking context
     * and configuration. Providers that do not support component configuration
     * ignore the config and delegate to {@link #instantiate(LinkingContext)}.
     */
    default ComponentInstance instantiate(LinkingContext linkingContext,
                                         ai.tegmentum.webassembly4j.api.config.ComponentConfig config) {
        return instantiate(linkingContext);
    }

    /**
     * Returns the names of all exported interfaces.
     * Providers that do not support introspection return an empty list.
     */
    default List<String> exportedInterfaces() {
        return Collections.emptyList();
    }

    /**
     * Returns the names of all imported interfaces.
     * Providers that do not support introspection return an empty list.
     */
    default List<String> importedInterfaces() {
        return Collections.emptyList();
    }

    /**
     * Returns whether this component exports the named interface.
     */
    default boolean exportsInterface(String name) {
        return exportedInterfaces().contains(name);
    }

    /**
     * Returns whether this component imports the named interface.
     */
    default boolean importsInterface(String name) {
        return importedInterfaces().contains(name);
    }

    /**
     * Serializes this component for later deserialization.
     * Not all providers support serialization; unsupported providers
     * throw {@link ai.tegmentum.webassembly4j.api.exception.UnsupportedFeatureException}.
     *
     * @return the serialized bytes
     */
    default byte[] serialize() {
        throw new ai.tegmentum.webassembly4j.api.exception.UnsupportedFeatureException(
                "Component serialization is not supported by this provider");
    }

    /**
     * Returns an optional extension capability for this component.
     */
    default <T> Optional<T> extension(Class<T> extensionType) {
        return Optional.empty();
    }

    /**
     * @return {@code true} if this component's provider ships a native library
     *         built with wasi:nn support. Callers should probe before configuring
     *         a {@link LinkingContext} with
     *         {@link DefaultLinkingContext.Builder#enableWasiNn(WasiNnConfig)};
     *         a false return means {@link #instantiate(LinkingContext)} will
     *         throw at wasi:nn linker-enable time. Default {@code false} so
     *         providers that don't override see the safe (no-support) answer.
     * @since 2.4.1
     */
    default boolean supportsWasiNn() {
        return false;
    }

    @Override
    void close();
}
