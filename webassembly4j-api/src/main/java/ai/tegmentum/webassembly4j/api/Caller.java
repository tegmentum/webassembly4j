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
 * <h2>Design alignment: wasmos {@code host:wasmtime@0.10.0} WIT</h2>
 *
 * <p>webassembly4j is a portable, provider-agnostic engine abstraction —
 * the same purpose wasmos's {@code host:wasmtime@0.10.0} WIT interface
 * serves for the WIT / component-model ecosystem. This {@code Caller}
 * interface's method shape mirrors the equivalent
 * {@code host:wasmtime@0.10.0} WIT primitives (see
 * {@code ~/git/wasmos/wit/}: {@code engine.wit}, {@code store.wit},
 * {@code module-compile.wit}, {@code linker.wit}, {@code instance.wit},
 * {@code refs.wit}). The two projects independently converged on the
 * same primitive set — validation that the surface is broadly useful
 * for any host that wants to drive dynamic composition from a callback
 * frame, not JIT-loader-bespoke.
 *
 * <p>Per {@code doctrine-wasmtime4j-caller-scoped-api-aligned-with-wasmos-wit-2026-07-27}
 * (which applies at the webassembly4j layer, not wasmtime4j — the JNI
 * layer stays wasmtime-specific): future caller-scoped additions to this
 * interface MUST mirror the equivalent method in wasmos WIT (kebab-case
 * → camelCase, {@code borrow<store>} removed since the caller carries
 * its own implicit store binding, {@code result<T, error>} → {@code T}
 * return + throws, {@code option<T>} → {@code Optional<T>},
 * {@code list<u8>} → {@code byte[]}). Deviations must be documented in
 * the method's Javadoc. Discoverable via {@code grep "@see wasmos"} across
 * this file.
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
     *
     * @see <a href="file://~/git/wasmos/wit/instance.wit">wasmos wit://instance.wit</a>
     *     — mirror of {@code host:wasmtime@0.10.0/instance.instance.get-memory}
     *     ({@code func(store: borrow&lt;store&gt;, name: string) -&gt; option&lt;wasm-memory&gt;}).
     */
    Optional<Memory> getMemory(String name);

    /**
     * Returns an exported table of the calling instance by name.
     *
     * @see <a href="file://~/git/wasmos/wit/instance.wit">wasmos wit://instance.wit</a>
     *     — mirror of {@code host:wasmtime@0.10.0/instance.instance.get-table}.
     */
    Optional<Table> getTable(String name);

    /**
     * Returns an exported function of the calling instance by name.
     *
     * @see <a href="file://~/git/wasmos/wit/instance.wit">wasmos wit://instance.wit</a>
     *     — mirror of {@code host:wasmtime@0.10.0/instance.instance.get-func}.
     */
    Optional<Function> getFunction(String name);

    /**
     * Returns an exported global of the calling instance by name.
     *
     * @see <a href="file://~/git/wasmos/wit/instance.wit">wasmos wit://instance.wit</a>
     *     — mirror of {@code host:wasmtime@0.10.0/instance.instance.get-global}.
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
     * @see <a href="file://~/git/wasmos/wit/module-compile.wit">wasmos wit://module-compile.wit</a>
     *     — mirror of {@code host:wasmtime@0.10.0/module-compile.from-binary}
     *     ({@code func(engine: borrow&lt;engine&gt;, bytes: list&lt;u8&gt;) -&gt; result&lt;module, error&gt;});
     *     Java-side takes only bytes since the engine is implicit in the caller
     *     binding.
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
     * @see <a href="file://~/git/wasmos/wit/linker.wit">wasmos wit://linker.wit</a>
     *     — mirror of {@code host:wasmtime@0.10.0/linker.linker.instantiate}
     *     ({@code func(store: borrow&lt;store&gt;, module: borrow&lt;module&gt;)
     *     -&gt; result&lt;instance, error&gt;}); Java-side collapses linker construction
     *     and {@code define-memory/table/global/func} calls into a single
     *     {@link LinkingContext} argument.
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
     * @see <a href="file://~/git/wasmos/wit/instance.wit">wasmos wit://instance.wit</a>
     *     — mirror of {@code host:wasmtime@0.10.0/instance.wasm-table.grow}
     *     ({@code func(store: borrow&lt;store&gt;, delta: u64, init: val) -&gt; result&lt;u64, error&gt;});
     *     added upstream via F-Wasmos-Guest-JIT-Loader r.2 (2026-07-27).
     *     Java-side returns {@code int} + {@code -1}-on-failure sentinel
     *     rather than {@code result} since {@code int} matches the WIT
     *     table's practical index range and the pre-existing method signature.
     */
    int growTable(Table table, int delta, Object init);

    /**
     * Write a value into a caller-visible table slot.
     *
     * @throws IllegalStateException if the callback has returned
     * @see <a href="file://~/git/wasmos/wit/instance.wit">wasmos wit://instance.wit</a>
     *     — mirror of {@code host:wasmtime@0.10.0/instance.wasm-table.set}
     *     ({@code func(store: borrow&lt;store&gt;, index: u64, value: val) -&gt; result&lt;_, error&gt;});
     *     added upstream via F-Wasmos-Guest-JIT-Loader r.2 (2026-07-27).
     */
    void setTableElement(Table table, int index, Object value);

    /**
     * Grow a caller-visible memory by {@code deltaPages} 64 KiB pages.
     *
     * @return the previous size in pages on success, {@code -1} on failure
     * @throws IllegalStateException if the callback has returned
     * @see <a href="file://~/git/wasmos/wit/instance.wit">wasmos wit://instance.wit</a>
     *     — mirror of {@code host:wasmtime@0.10.0/instance.wasm-memory.grow}
     *     ({@code func(store: borrow&lt;store&gt;, delta: u64) -&gt; result&lt;u64, error&gt;}).
     */
    long growMemory(Memory memory, long deltaPages);

    /**
     * Read a byte range from a caller-exported memory using the caller-scoped
     * native path (safe from within a host-callback frame).
     *
     * <p>Preferable to {@code getMemory(name).get().read(...)} from inside a
     * callback because the api-layer {@link Memory} adapter's native handle
     * may not be registered from the callback frame, causing native failures.
     * This method routes through the provider's scoped-caller memory-read
     * primitive (wasmtime4j-provider: uses {@code caller.get_export(name)
     * .into_memory() + Memory::read(&mut ctx, ...)}).
     *
     * @param memoryName name of the caller's exported memory (e.g. "memory")
     * @param offset byte offset into memory
     * @param length number of bytes to read
     * @return the byte range
     * @throws IllegalStateException if the callback has returned
     * @throws UnsupportedOperationException if the backend has not implemented
     *         scoped memory I/O
     * @see <a href="file://~/git/wasmos/wit/instance.wit">wasmos wit://instance.wit</a>
     *     — mirror of {@code host:wasmtime@0.10.0/instance.wasm-memory.read}
     *     ({@code func(store: borrow&lt;store&gt;, offset: u64, length: u64)
     *     -&gt; result&lt;list&lt;u8&gt;, error&gt;}); Java-side takes {@code memoryName}
     *     lookup rather than {@code borrow&lt;wasm-memory&gt;} since the caller
     *     resolves the export by name internally.
     * @since 2.5.2
     */
    default byte[] readMemory(String memoryName, long offset, int length) {
        throw new UnsupportedOperationException(
                "readMemory not implemented on this Caller backend");
    }

    /**
     * Write a byte array into a caller-exported memory using the caller-scoped
     * native path. Same safety guarantees as {@link #readMemory}.
     *
     * @throws IllegalStateException if the callback has returned
     * @throws UnsupportedOperationException if the backend has not implemented
     *         scoped memory I/O
     * @see <a href="file://~/git/wasmos/wit/instance.wit">wasmos wit://instance.wit</a>
     *     — mirror of {@code host:wasmtime@0.10.0/instance.wasm-memory.write}
     *     ({@code func(store: borrow&lt;store&gt;, offset: u64, data: list&lt;u8&gt;)
     *     -&gt; result&lt;_, error&gt;}).
     * @since 2.5.2
     */
    default void writeMemory(String memoryName, long offset, byte[] bytes) {
        throw new UnsupportedOperationException(
                "writeMemory not implemented on this Caller backend");
    }

    /**
     * Provider escape hatch — returns the underlying provider-specific
     * caller handle when {@code nativeType} is assignable from it.
     */
    <U> Optional<U> unwrap(Class<U> nativeType);
}
