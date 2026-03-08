package ai.tegmentum.webassembly4j.api;

import java.util.Optional;

public interface Table {

    int size();

    <T> Optional<T> unwrap(Class<T> nativeType);
}
