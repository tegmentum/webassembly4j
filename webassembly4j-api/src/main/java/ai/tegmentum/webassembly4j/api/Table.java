package ai.tegmentum.webassembly4j.api;

import java.util.Optional;
import java.util.OptionalInt;

/**
 * A WebAssembly table exported by (or imported into) an instance.
 *
 * <p>Beyond {@link #size()} and the escape hatch {@link #unwrap(Class)},
 * this interface exposes the four WebAssembly-spec table operations
 * ({@code table.get}, {@code table.set}, {@code table.grow}, and the
 * {@code max} field of {@code table.type()}) so that api-layer consumers
 * can perform basic dynamic table management without dropping to a
 * provider-specific native type.
 *
 * <h2>Provider support</h2>
 *
 * <p>{@link #maxSize()}, {@link #get(int)}, {@link #set(int, Object)}, and
 * {@link #grow(int, Object)} carry {@code default} bodies that throw
 * {@link UnsupportedOperationException}. Provider adapters override the
 * ones they can honour. Consumers that need to be portable across
 * providers should either catch {@link UnsupportedOperationException} at
 * the call site or gate on {@link EngineCapabilities} where an equivalent
 * capability flag exists; note that this interface itself does NOT
 * currently publish a per-operation capability probe — {@code try/catch}
 * remains the portable escape.
 *
 * <h2>Element wrapping</h2>
 *
 * <p>The {@code Object} return / accept type on {@link #get(int)} and
 * {@link #set(int, Object)} is provider-specific until a spec-based
 * {@code Ref} abstraction lands (deferred). For {@code funcref}-typed
 * tables, a {@code null} slot is legal and denotes "uninitialized".
 */
public interface Table {

    int size();

    /**
     * @return the maximum table size, or {@link OptionalInt#empty()} if the
     *         table has no declared maximum (unbounded).
     */
    default OptionalInt maxSize() {
        throw new UnsupportedOperationException(
            "Table.maxSize() not implemented by this provider");
    }

    /**
     * @return the element at {@code index}, or {@code null} if the slot is
     *         uninitialized. Element wrapping is provider-specific until a
     *         spec-based {@code Ref} abstraction lands.
     * @throws IndexOutOfBoundsException if {@code index >= size()}
     */
    default Object get(int index) {
        throw new UnsupportedOperationException(
            "Table.get(int) not implemented by this provider");
    }

    /**
     * Sets the element at {@code index}. {@code value} must be assignable to
     * the table's element type; {@code null} is accepted for funcref-typed
     * tables as an uninitialized slot.
     *
     * @throws IndexOutOfBoundsException if {@code index >= size()}
     */
    default void set(int index, Object value) {
        throw new UnsupportedOperationException(
            "Table.set(int, Object) not implemented by this provider");
    }

    /**
     * Grows the table by {@code delta} elements initialized to {@code init}.
     *
     * @return the pre-grow size (which is also the index of the first newly-
     *         added slot), or -1 if growth would exceed {@link #maxSize()}.
     */
    default int grow(int delta, Object init) {
        throw new UnsupportedOperationException(
            "Table.grow(int, Object) not implemented by this provider");
    }

    <T> Optional<T> unwrap(Class<T> nativeType);
}
