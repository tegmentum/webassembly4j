package ai.tegmentum.webassembly4j.api;

/**
 * Host function that receives a {@link Caller} handle giving scoped access
 * to the calling instance's exports, engine, and store for the duration of
 * the callback. Providers that support this pattern route the caller from
 * their native runtime; providers that do not throw
 * {@link UnsupportedOperationException} at instantiation time when a
 * {@link LinkingContext} carries any
 * {@link CallerAwareHostFunctionDefinition} — see
 * {@link LinkingContext#callerAwareHostFunctions()}.
 *
 * @param <T> the type of caller-associated data (typically {@code Void}
 *            when no store data is used)
 * @since 2.5.2
 */
@FunctionalInterface
public interface CallerAwareHostFunction<T> {

    /**
     * Execute the host function with scoped access to the calling instance.
     *
     * @param caller scoped access to the calling instance; only valid for
     *               the duration of this call
     * @param args   arguments coerced from wasm values by the provider
     * @return result values coerced back into wasm values by the provider;
     *         must match the declared result types
     */
    Object[] execute(Caller<T> caller, Object... args);
}
