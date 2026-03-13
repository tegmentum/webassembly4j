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
}
