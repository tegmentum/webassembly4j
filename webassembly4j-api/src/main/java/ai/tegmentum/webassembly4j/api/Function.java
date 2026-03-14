package ai.tegmentum.webassembly4j.api;

public interface Function {

    ValueType[] parameterTypes();

    ValueType[] resultTypes();

    Object invoke(Object... args);

    /**
     * Returns the number of parameters this function accepts.
     * Default implementation delegates to {@code parameterTypes().length}.
     */
    default int parameterCount() {
        return parameterTypes().length;
    }

    /**
     * Returns the number of results this function returns.
     * Default implementation delegates to {@code resultTypes().length}.
     */
    default int resultCount() {
        return resultTypes().length;
    }

    /**
     * Returns a typed wrapper for this function that avoids boxing overhead.
     * The type must be one of the functional interfaces defined in
     * {@link TypedFunction}.
     *
     * <p>Example:
     * <pre>{@code
     * TypedFunction.I32_I32_I32 add = fn.typed(TypedFunction.I32_I32_I32.class);
     * int sum = add.call(3, 4);
     * }</pre>
     *
     * @param type the desired typed function interface
     * @param <T>  the typed function type
     * @return a typed wrapper
     */
    default <T> T typed(Class<T> type) {
        return TypedFunction.wrap(this, type);
    }

    /**
     * Invokes this function multiple times with different argument sets in a single batch.
     *
     * <p>Amortizes native crossing overhead by performing all invocations in one round-trip
     * where the provider supports it. Fails fast on the first error.
     *
     * @param argSets the argument arrays for each invocation
     * @return an array of results, one per invocation (null entries for void functions)
     * @throws IllegalArgumentException if argSets is null
     */
    default Object[] invokeMultiple(final Object[]... argSets) {
        if (argSets == null) {
            throw new IllegalArgumentException("Argument sets array cannot be null");
        }
        final Object[] results = new Object[argSets.length];
        for (int i = 0; i < argSets.length; i++) {
            results[i] = invoke(argSets[i]);
        }
        return results;
    }
}
