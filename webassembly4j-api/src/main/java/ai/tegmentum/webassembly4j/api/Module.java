package ai.tegmentum.webassembly4j.api;

import java.util.Optional;

public interface Module extends AutoCloseable {

    Instance instantiate();

    Instance instantiate(LinkingContext linkingContext);

    default <T> Optional<T> extension(Class<T> extensionType) {
        return Optional.empty();
    }

    @Override
    void close();
}
