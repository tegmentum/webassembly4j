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

    @Override
    void close();
}
