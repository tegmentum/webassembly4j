package ai.tegmentum.webassembly4j.api;

import java.util.Optional;

public interface Global {

    ValueType type();

    Object get();

    void set(Object value);

    boolean mutable();

    <T> Optional<T> unwrap(Class<T> nativeType);
}
