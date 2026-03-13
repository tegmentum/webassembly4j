/**
 * WebAssembly GC (Garbage Collection) proposal types.
 *
 * <p>This package defines the API for working with WasmGC-managed types:
 * structs, arrays, and i31ref values that live on the WebAssembly runtime's
 * managed heap.
 *
 * <h2>Overview</h2>
 *
 * <p>The WasmGC proposal adds garbage-collected reference types to WebAssembly,
 * enabling languages that target WasmGC (Kotlin/Wasm, Dart, OCaml, Java via
 * J2Wasm) to use structured heap objects without manual memory management.
 *
 * <p>Access GC support from an instance:
 *
 * <pre>{@code
 * Instance instance = module.instantiate();
 * GcExtension gc = instance.extension(GcExtension.class)
 *     .orElseThrow(() -> new UnsupportedOperationException("GC not supported"));
 * }</pre>
 *
 * <h2>Defining Types</h2>
 *
 * <pre>{@code
 * // Struct type with named fields
 * GcStructType pointType = GcStructType.builder("Point")
 *     .addField("x", GcFieldType.f64(), true)
 *     .addField("y", GcFieldType.f64(), true)
 *     .build();
 *
 * // Array type
 * GcArrayType intArray = GcArrayType.builder("IntArray")
 *     .elementType(GcFieldType.i32())
 *     .mutable(true)
 *     .build();
 * }</pre>
 *
 * <h2>Creating and Using Instances</h2>
 *
 * <pre>{@code
 * // Create a struct
 * GcStructInstance point = gc.createStruct(pointType,
 *     GcValue.f64(3.0), GcValue.f64(4.0));
 *
 * // Read fields
 * double x = point.getField(0).asF64(); // 3.0
 * double y = point.getField(1).asF64(); // 4.0
 *
 * // Write mutable fields
 * point.setField(0, GcValue.f64(5.0));
 *
 * // Create an array
 * GcArrayInstance arr = gc.createArray(intArray,
 *     GcValue.i32(10), GcValue.i32(20), GcValue.i32(30));
 * int first = arr.getElement(0).asI32(); // 10
 *
 * // Create an i31ref (unboxed 31-bit integer)
 * GcI31Instance i31 = gc.createI31(42);
 * int value = i31.value(); // 42
 * }</pre>
 *
 * <h2>Type Hierarchy</h2>
 *
 * <p>GC reference types follow the WebAssembly type hierarchy:
 *
 * <pre>
 *   anyref (top)
 *     +-- eqref
 *         +-- i31ref
 *         +-- structref
 *         +-- arrayref
 * </pre>
 *
 * <p>Use {@link GcExtension#refTest} and {@link GcExtension#refCast} for
 * runtime type checking and casting.
 *
 * <h2>Async Allocation</h2>
 *
 * <p>For runtimes that support async resource limiting, use the async
 * creation methods:
 *
 * <pre>{@code
 * CompletableFuture<GcStructInstance> future =
 *     gc.createStructAsync(pointType, GcValue.f64(1.0), GcValue.f64(2.0));
 * GcStructInstance point = future.join();
 * }</pre>
 *
 * @see GcExtension
 * @see GcStructType
 * @see GcArrayType
 * @see GcValue
 */
package ai.tegmentum.webassembly4j.api.gc;
