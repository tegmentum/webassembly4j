package ai.tegmentum.webassembly4j.api;

import java.util.Optional;

public interface Instance {

    Optional<Function> function(String name);

    Optional<Memory> memory(String name);

    Optional<Table> table(String name);

    Optional<Global> global(String name);

    /**
     * Returns an optional extension capability for this instance.
     *
     * <p>Extensions provide optional features that not all runtimes support.
     * For example, {@code instance.extension(GcExtension.class)} returns
     * GC support if the runtime implements it.
     *
     * @param extensionType the extension interface class
     * @param <T> the extension type
     * @return the extension if supported, or empty
     */
    default <T> Optional<T> extension(Class<T> extensionType) {
        return Optional.empty();
    }

    <T> Optional<T> unwrap(Class<T> nativeType);
}
