package ai.tegmentum.webassembly4j.api;

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
}
