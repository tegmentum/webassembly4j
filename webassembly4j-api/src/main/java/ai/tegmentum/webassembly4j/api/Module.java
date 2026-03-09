package ai.tegmentum.webassembly4j.api;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

public interface Module extends AutoCloseable {

    Instance instantiate();

    Instance instantiate(LinkingContext linkingContext);

    /**
     * Returns descriptors for all exports declared by this module.
     * Providers that do not support introspection return an empty list.
     *
     * @return an unmodifiable list of export descriptors
     */
    default List<ExportDescriptor> exports() {
        return Collections.emptyList();
    }

    /**
     * Returns descriptors for all imports required by this module.
     * Providers that do not support introspection return an empty list.
     *
     * @return an unmodifiable list of import descriptors
     */
    default List<ImportDescriptor> imports() {
        return Collections.emptyList();
    }

    default <T> Optional<T> extension(Class<T> extensionType) {
        return Optional.empty();
    }

    @Override
    void close();
}
