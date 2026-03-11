package ai.tegmentum.webassembly4j.api.gc;

import java.util.List;

/**
 * Extension interface for WebAssembly GC (Garbage Collection) support.
 *
 * <p>Provides operations for creating and manipulating GC-managed struct and
 * array types, as well as i31ref values. Obtained from an {@link ai.tegmentum.webassembly4j.api.Instance}
 * via {@code instance.extension(GcExtension.class)}.
 *
 * <p>Not all runtimes support WasmGC. Providers that do not support GC will
 * return {@code Optional.empty()} from {@code extension()}.
 *
 * <p>Example:
 * <pre>
 * Instance instance = module.instantiate();
 * GcExtension gc = instance.extension(GcExtension.class)
 *     .orElseThrow(() -&gt; new UnsupportedOperationException("GC not supported"));
 *
 * GcStructType pointType = GcStructType.builder("Point")
 *     .addField("x", GcFieldType.f64(), true)
 *     .addField("y", GcFieldType.f64(), true)
 *     .build();
 *
 * GcStructInstance point = gc.createStruct(pointType,
 *     GcValue.f64(1.0), GcValue.f64(2.0));
 * double x = point.getField(0).asF64();
 * </pre>
 */
public interface GcExtension {

    // --- Struct operations ---

    /**
     * Creates a new struct instance with the given field values.
     *
     * @param type   the struct type
     * @param values initial field values, one per field in declaration order
     * @return the new struct instance
     * @throws GcException if the number of values doesn't match the field count,
     *                     or a value type is incompatible
     */
    GcStructInstance createStruct(GcStructType type, GcValue... values);

    /**
     * Creates a new struct instance with the given field values.
     *
     * @param type   the struct type
     * @param values initial field values, one per field in declaration order
     * @return the new struct instance
     * @throws GcException if the number of values doesn't match the field count,
     *                     or a value type is incompatible
     */
    GcStructInstance createStruct(GcStructType type, List<GcValue> values);

    // --- Array operations ---

    /**
     * Creates a new array instance with the given element values.
     *
     * @param type     the array type
     * @param elements initial element values
     * @return the new array instance
     * @throws GcException if an element value type is incompatible
     */
    GcArrayInstance createArray(GcArrayType type, GcValue... elements);

    /**
     * Creates a new array instance with the given element values.
     *
     * @param type     the array type
     * @param elements initial element values
     * @return the new array instance
     * @throws GcException if an element value type is incompatible
     */
    GcArrayInstance createArray(GcArrayType type, List<GcValue> elements);

    /**
     * Creates a new array instance filled with a default value.
     *
     * @param type   the array type
     * @param length the number of elements
     * @return the new array instance
     * @throws GcException if the length is negative
     */
    GcArrayInstance createArray(GcArrayType type, int length);

    // --- i31 operations ---

    /**
     * Creates an i31ref value from a signed integer.
     *
     * @param value the signed 31-bit value (must be in [-2^30, 2^30 - 1])
     * @return the i31 instance
     * @throws GcException if the value is out of the valid 31-bit range
     */
    GcI31Instance createI31(int value);

    // --- Type casting ---

    /**
     * Tests whether a GC object matches the given reference type ({@code ref.test}).
     *
     * @param object  the object to test
     * @param refType the reference type to test against
     * @return true if the object matches
     */
    boolean refTest(GcObject object, GcReferenceType refType);

    /**
     * Casts a GC object to the given reference type ({@code ref.cast}).
     *
     * @param object  the object to cast
     * @param refType the target reference type
     * @return the cast object
     * @throws GcException if the cast fails
     */
    GcObject refCast(GcObject object, GcReferenceType refType);

    // --- GC control ---

    /**
     * Triggers a garbage collection cycle, if the runtime supports explicit collection.
     *
     * @return statistics from the collection, or empty stats if not supported
     */
    GcStats collectGarbage();

    /**
     * Returns current GC statistics.
     */
    GcStats getStats();
}
