package ai.tegmentum.webassembly4j.api;

import java.util.Optional;

public interface Engine extends AutoCloseable {

    EngineInfo info();

    EngineCapabilities capabilities();

    Module loadModule(byte[] bytes);

    Component loadComponent(byte[] bytes);

    <T> Optional<T> extension(Class<T> extensionType);

    <T> Optional<T> unwrap(Class<T> nativeType);

    @Override
    void close();
}
