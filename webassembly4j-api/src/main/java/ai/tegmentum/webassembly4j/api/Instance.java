package ai.tegmentum.webassembly4j.api;

import java.util.LinkedHashMap;
import java.util.Map;
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

    /**
     * Retrieves the values of multiple global variables in a single batch.
     *
     * <p>Amortizes native crossing overhead when reading many globals. Results are
     * returned in a map preserving the input order. Fails fast on the first
     * non-existent global name.
     *
     * @param globalNames the names of the exported global variables
     * @return a map of global name to its current value, in input order
     * @throws IllegalArgumentException if globalNames is null or any name is not found
     */
    default Map<String, Object> getGlobals(final String... globalNames) {
        if (globalNames == null) {
            throw new IllegalArgumentException("Global names array cannot be null");
        }
        final Map<String, Object> result = new LinkedHashMap<>();
        for (final String name : globalNames) {
            final Global g = global(name)
                .orElseThrow(() -> new IllegalArgumentException("Global not found: " + name));
            result.put(name, g.get());
        }
        return result;
    }

    /**
     * Sets the values of multiple global variables in a single batch.
     *
     * <p>Amortizes native crossing overhead when writing many globals. Operations
     * are applied in iteration order; fails fast on the first error.
     *
     * @param globals a map of global name to new value
     * @throws IllegalArgumentException if globals is null or any name is not found
     */
    default void setGlobals(final Map<String, Object> globals) {
        if (globals == null) {
            throw new IllegalArgumentException("Globals map cannot be null");
        }
        for (final Map.Entry<String, Object> entry : globals.entrySet()) {
            final Global g = global(entry.getKey())
                .orElseThrow(() -> new IllegalArgumentException(
                    "Global not found: " + entry.getKey()));
            g.set(entry.getValue());
        }
    }

    <T> Optional<T> unwrap(Class<T> nativeType);
}
