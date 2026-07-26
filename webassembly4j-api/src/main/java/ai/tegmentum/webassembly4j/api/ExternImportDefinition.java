package ai.tegmentum.webassembly4j.api;

/**
 * A typed extern-import definition attached to a {@link LinkingContext}.
 * Providers consume these during {@link Module#instantiate(LinkingContext)}
 * to wire cross-instance shared imports (memory, table, global, function).
 *
 * <p>Common Wasm-embedding patterns: WASI shim wiring (memory sharing across
 * a component boundary), function-table plug-in registration, cross-module
 * global state, guest-callable host functions passed through as first-class
 * imports.
 *
 * <p>The concrete implementations are a fixed set matching the WebAssembly
 * spec's {@code ExternType} — {@link MemoryImport}, {@link TableImport},
 * {@link GlobalImport}, {@link FunctionImport}. Sealing is not modelled at
 * the language level because this package targets Java 8; callers should
 * treat the set of implementations as closed and use {@code instanceof}
 * dispatch. Add-on Wasm proposals (typed function references, GC ref types)
 * may add variants in a future charter.
 *
 * <p>Provider support policy: a provider that cannot wire a given variant
 * (for example because the underlying value cannot be
 * {@link Memory#unwrap(Class) unwrapped} to a native handle) must throw at
 * {@code instantiate} time with a clear message identifying the variant and
 * import coordinates. Providers that support none of these variants should
 * behave as if {@link LinkingContext#externImports()} were empty.
 *
 * @since 2.5.2
 */
public interface ExternImportDefinition {

    /**
     * The importing module namespace as declared in the WebAssembly
     * import statement (e.g. {@code "env"}).
     */
    String moduleName();

    /**
     * The import field name as declared in the WebAssembly import statement
     * (e.g. {@code "memory"} in {@code (import "env" "memory" ...)}).
     */
    String name();
}
