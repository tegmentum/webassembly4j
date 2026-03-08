package ai.tegmentum.webassembly4j.api;

import java.util.Optional;

public interface Instance {

    Optional<Function> function(String name);

    Optional<Memory> memory(String name);

    Optional<Table> table(String name);

    Optional<Global> global(String name);

    <T> Optional<T> unwrap(Class<T> nativeType);
}
