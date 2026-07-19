package ai.tegmentum.webassembly4j.api;

import ai.tegmentum.webassembly4j.api.component.async.ConcurrentTask;
import ai.tegmentum.webassembly4j.api.exception.UnsupportedFeatureException;

import java.util.List;

/**
 * A component model instance. Extends the core {@link Instance} interface
 * with component-specific capabilities such as typed function invocation
 * using WIT-level types.
 *
 * <p>Component instances may not support core module exports (memory, table,
 * global) directly. Those methods will return empty for pure component instances.
 */
public interface ComponentInstance extends Instance {

    /**
     * Invokes an exported function by name with the given arguments.
     * Arguments and return values use Java types that map naturally to WIT types:
     * <ul>
     *   <li>bool → Boolean</li>
     *   <li>s8/s16/s32 → Integer, s64 → Long</li>
     *   <li>u8/u16/u32 → Integer, u64 → Long</li>
     *   <li>f32 → Float, f64 → Double</li>
     *   <li>char → Character</li>
     *   <li>string → String</li>
     *   <li>list → List</li>
     *   <li>record → Map&lt;String, Object&gt;</li>
     *   <li>tuple → List</li>
     *   <li>option → null or value</li>
     *   <li>result → the ok value (throws on error)</li>
     *   <li>enum → String</li>
     *   <li>flags → Set&lt;String&gt;</li>
     *   <li>list&lt;u8&gt; → byte[] (optimization; providers may also accept/return List&lt;Integer&gt;)</li>
     * </ul>
     *
     * @param functionName the exported function name
     * @param args the function arguments
     * @return the function result, or null for void functions
     * @throws ai.tegmentum.webassembly4j.api.exception.ExecutionException if invocation fails
     */
    Object invoke(String functionName, Object... args);

    /**
     * Typed variant of {@link #invoke} that returns the raw WIT value tree from the
     * provider instead of unwrapping it to a natural Java shape via
     * {@code WitValue.toJava()}. Symmetric with the input side, which already
     * requires typed values for records/variants/options/results.
     *
     * <p>Callers doing round-trip WIT marshalling (typed in, typed out) should prefer
     * this over {@link #invoke} — it saves an extra pass through {@code toJava()}
     * and preserves precise WIT typing that the Java-shape unwrap loses (e.g. u64
     * width, enum discriminants, option's inner type).
     *
     * <p>The return type is {@code Object} rather than a stronger provider-specific
     * WIT type to keep this interface provider-neutral; the concrete type is the
     * provider's WIT value tree (for wasmtime, {@code ai.tegmentum.wasmtime4j.wit.WitValue}).
     *
     * <p>Default implementation delegates to {@link #invoke}. Providers with a
     * typed native invoke path should override.
     *
     * @param functionName the exported function name
     * @param args the function arguments
     * @return the provider's WIT value, or {@code null} for void functions
     * @throws ai.tegmentum.webassembly4j.api.exception.ExecutionException if invocation fails
     */
    default Object invokeWit(String functionName, Object... args) {
        return invoke(functionName, args);
    }

    /**
     * Adopts a resource-shaped value returned from a prior {@link #invokeWit} call and
     * returns a {@link WitCallableResource} bound to this instance, so method invocations
     * on the resource can be written as {@code res.invokeMethod("m", args...)} rather than
     * manually threading the receiver through {@link #invokeWit}.
     *
     * <p>The passed value must be the provider's WIT resource type (for wasmtime,
     * {@code ai.tegmentum.wasmtime4j.wit.WitResource}). The returned handle owns the
     * lifecycle: closing it drops the underlying store-side resource.
     *
     * <p>Default implementation throws — providers that host resources through a native
     * store (currently only wasmtime4j) override.
     *
     * @param resource the provider-specific resource value (typically the return of an
     *     {@link #invokeWit} call whose signature declared {@code own<T>})
     * @return a callable handle bound to this instance
     * @throws UnsupportedFeatureException if the provider does not host callable resources
     * @throws IllegalArgumentException if {@code resource} is not of the provider's expected
     *     resource type or has no native backing
     * @since 2.4.0
     */
    default WitCallableResource asCallableResource(Object resource) {
        throw new UnsupportedFeatureException(
                "Provider does not host callable resources; wrap the receiver manually via invokeWit");
    }

    /**
     * Returns whether this instance exports a function with the given name.
     */
    boolean hasFunction(String name);

    /**
     * Returns the names of all exported functions.
     */
    List<String> exportedFunctions();

    /**
     * Returns the names of all exported interfaces.
     */
    List<String> exportedInterfaces();

    /**
     * Returns whether this instance exports a named interface.
     */
    boolean exportsInterface(String name);

    /**
     * Invokes an exported function and returns the result as a byte array.
     * This is an optimization for functions that return {@code list<u8>},
     * avoiding per-element boxing overhead.
     *
     * <p>Default implementation calls {@link #invoke(String, Object...)} and
     * converts the result.
     *
     * @param functionName the exported function name
     * @param args the function arguments
     * @return the result as a byte array
     * @throws ai.tegmentum.webassembly4j.api.exception.ExecutionException if invocation fails
     * @throws ClassCastException if the result is not a list of bytes
     */
    @SuppressWarnings("unchecked")
    default byte[] invokeBytes(String functionName, Object... args) {
        Object result = invoke(functionName, args);
        if (result instanceof byte[]) {
            return (byte[]) result;
        }
        List<? extends Number> list = (List<? extends Number>) result;
        byte[] bytes = new byte[list.size()];
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] = list.get(i).byteValue();
        }
        return bytes;
    }

    /**
     * Executes a task within a concurrent scope, allowing multiple async
     * component function invocations to run concurrently.
     *
     * @param task the concurrent task to execute
     * @param <T> the result type
     * @return the task result
     * @throws UnsupportedFeatureException if the provider does not support
     *         concurrent component execution
     */
    default <T> T runConcurrent(ConcurrentTask<T> task) {
        throw new UnsupportedFeatureException(
                "Concurrent component execution not supported");
    }

    /**
     * Decrement the underlying store's remaining fuel by {@code amount}.
     *
     * <p>Lets the host charge fuel against the same budget the guest consumes so a Java-side
     * "toll" is enforced against real store fuel — the guest traps once the store is exhausted,
     * just as if the fuel had been burned by wasm instructions. Complements
     * {@link #fuelConsumed()}: what the host debits here shows up in the actual-consumed reading.
     *
     * <p>Providers whose engine is not fuel-metered (or that don't host a store per instance)
     * throw {@link UnsupportedFeatureException} by default. Providers that support fuel override.
     *
     * @param amount the units of fuel to deduct (must be non-negative)
     * @throws UnsupportedFeatureException if the provider does not support store-level fuel
     *     accounting
     * @throws IllegalArgumentException if {@code amount} is negative
     * @throws ai.tegmentum.webassembly4j.api.exception.ExecutionException if the deduction
     *     would exceed the currently-remaining fuel (no partial deduction) or the underlying
     *     fuel operation fails
     * @since 2.4.3
     */
    default void consumeFuel(long amount) {
        throw new UnsupportedFeatureException(
                "Provider does not support store-level fuel accounting");
    }

    /**
     * Report the amount of fuel consumed against the store since its fuel was last set.
     *
     * <p>Set-fuel points are: instantiation (from the caller's fuel cap) and, for providers with
     * per-call fuel resets, the start of each invocation. This method returns
     * {@code baseline - remaining} relative to the most recent set-fuel — the actual fuel burned
     * (wasm instructions plus any {@link #consumeFuel(long)} tolls) since then.
     *
     * <p>Providers whose engine is not fuel-metered return {@code -1} (unsupported sentinel).
     * Providers that support fuel override.
     *
     * @return the fuel consumed since the last {@code set_fuel}, or {@code -1} when the provider
     *     does not support store-level fuel accounting
     * @since 2.4.3
     */
    default long fuelConsumed() {
        return -1L;
    }
}
