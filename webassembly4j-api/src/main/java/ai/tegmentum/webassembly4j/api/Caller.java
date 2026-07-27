package ai.tegmentum.webassembly4j.api;

import java.util.Optional;

/**
 * Scoped access to the caller's instance during a host callback. Providers
 * that support caller-aware host functions expose the caller via this
 * interface; the reference is only valid for the callback duration.
 *
 * <p>Consumers use this to safely read exports of the calling instance,
 * compile / instantiate additional modules against the caller's engine and
 * store, and mutate caller-visible tables and memories from within the
 * callback frame without triggering reentrant store-lock corruption. Any
 * attempt to invoke a method after the callback returns throws
 * {@link IllegalStateException} (providers enforce this via a generation
 * counter kept in the underlying store).
 *
 * <p>Not every provider surfaces every capability. Providers that do not
 * support caller-aware host functions at all return an empty list from
 * {@link LinkingContext#callerAwareHostFunctions()} consumption paths and
 * never construct a {@code Caller}. Providers that support some but not all
 * scoped methods raise {@link UnsupportedOperationException} on the
 * unsupported entrypoints.
 *
 * @param <T> the type of caller-associated data (provider-specific store
 *            data slot; typically {@code Void} when no data is used)
 * @since 2.5.2
 */
public interface Caller<T> {

    /**
     * Returns the caller-associated data (provider-specific; may be null when
     * the underlying store carries no user data).
     */
    T data();

    /**
     * Returns an exported memory of the calling instance by name.
     */
    Optional<Memory> getMemory(String name);

    /**
     * Returns an exported table of the calling instance by name.
     */
    Optional<Table> getTable(String name);

    /**
     * Returns an exported function of the calling instance by name.
     */
    Optional<Function> getFunction(String name);

    /**
     * Returns an exported global of the calling instance by name.
     */
    Optional<Global> getGlobal(String name);

    /**
     * Compile a {@link Module} using the caller's engine. Safe inside a
     * callback — providers route through the caller-scoped path so the
     * resulting module shares the caller's engine and can be instantiated
     * into the caller's store without cross-engine errors.
     *
     * @param wasmBytes WebAssembly binary bytes (or WAT text on providers
     *                  that accept it)
     * @return the compiled module
     * @throws IllegalStateException if the callback has returned
     */
    Module compileModule(byte[] wasmBytes);

    /**
     * Instantiate a module in the caller's store using the given imports.
     * Safe inside a callback — the provider borrows the caller's live store
     * context instead of re-entering the store lock.
     *
     * @param module  the module to instantiate; must have been produced by
     *                the same provider (typically via
     *                {@link #compileModule(byte[])})
     * @param imports linking context providing imports; may be null for a
     *                no-import instantiation
     * @return the newly created instance
     * @throws IllegalStateException if the callback has returned
     */
    Instance instantiate(Module module, LinkingContext imports);

    /**
     * Grow a caller-visible table by {@code delta} slots, filling new slots
     * with {@code init}. Provider-specific rules apply to the {@code init}
     * value's type (typically funcref must be a {@link Function} handle or
     * null; externref rules vary).
     *
     * @return the previous size on success, {@code -1} on failure
     * @throws IllegalStateException if the callback has returned
     */
    int growTable(Table table, int delta, Object init);

    /**
     * Write a value into a caller-visible table slot.
     *
     * @throws IllegalStateException if the callback has returned
     */
    void setTableElement(Table table, int index, Object value);

    /**
     * Grow a caller-visible memory by {@code deltaPages} 64 KiB pages.
     *
     * @return the previous size in pages on success, {@code -1} on failure
     * @throws IllegalStateException if the callback has returned
     */
    long growMemory(Memory memory, long deltaPages);

    /**
     * Provider escape hatch — returns the underlying provider-specific
     * caller handle when {@code nativeType} is assignable from it.
     */
    <U> Optional<U> unwrap(Class<U> nativeType);
}
