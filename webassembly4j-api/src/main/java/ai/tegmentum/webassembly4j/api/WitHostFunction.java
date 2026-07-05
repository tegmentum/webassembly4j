package ai.tegmentum.webassembly4j.api;

/**
 * Host implementation of a WIT-typed function imported by a component.
 *
 * <p>Unlike {@link HostFunction}, whose arguments and results are core-module i32/i64
 * values, WIT host functions exchange richer typed values with the guest — records,
 * variants, options, results, lists, etc.
 *
 * <p><b>Concrete value type is provider-specific.</b> The {@code Object[]} entries are
 * the values in the provider's native WIT representation:
 * <ul>
 *   <li>Wasmtime provider:
 *       {@code ai.tegmentum.wasmtime4j.component.ComponentVal}
 *       (built via {@code ComponentValFactory.INSTANCE.createXxx(...)}).</li>
 * </ul>
 * A cross-provider WIT value abstraction is not yet part of this API; write host
 * functions against the provider you plan to use.
 *
 * <p>Registered on a {@link LinkingContext} via {@link
 * DefaultLinkingContext.Builder#addWitHostFunction(String, WitHostFunction)}.
 */
@FunctionalInterface
public interface WitHostFunction {

    /**
     * Invoked when the component calls the imported WIT function.
     *
     * @param args provider-native WIT value tree, one entry per declared parameter.
     * @return provider-native WIT value tree, matching the declared results tuple.
     *         A function with no return value returns an empty array.
     */
    Object[] execute(Object[] args);
}
