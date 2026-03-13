/**
 * WasmGC Object Bridge -- automatic marshalling between Java objects and
 * WebAssembly GC struct instances.
 *
 * <p>This package provides a high-level bridge for passing structured data
 * between Java and WebAssembly using the WasmGC (Garbage Collection) proposal
 * instead of linear memory. Objects live on the Wasm runtime's managed heap
 * and are garbage-collected automatically -- no manual allocation or freeing
 * is needed.
 *
 * <h2>Quick Start</h2>
 *
 * <p>Annotate a Java class or record with {@link GcMapped}:
 *
 * <pre>{@code
 * @GcMapped
 * public class Point {
 *     double x;
 *     double y;
 *
 *     public Point() {}
 *     public Point(double x, double y) {
 *         this.x = x;
 *         this.y = y;
 *     }
 * }
 * }</pre>
 *
 * <p>Create a {@link GcMarshaller} from a {@link ai.tegmentum.webassembly4j.api.gc.GcExtension}
 * and marshal objects in both directions:
 *
 * <pre>{@code
 * GcExtension gc = instance.extension(GcExtension.class)
 *     .orElseThrow(() -> new UnsupportedOperationException("GC not supported"));
 * GcMarshaller marshaller = GcMarshaller.forExtension(gc);
 *
 * // Java -> GC struct
 * Point p = new Point(3.0, 4.0);
 * GcStructInstance struct = marshaller.marshal(p);
 *
 * // GC struct -> Java
 * Point result = marshaller.unmarshal(struct, Point.class);
 * assert result.x == 3.0;
 * assert result.y == 4.0;
 * }</pre>
 *
 * <h2>Type Mapping</h2>
 *
 * <p>The following Java types are supported as fields in {@code @GcMapped} classes:
 *
 * <table>
 *   <caption>Java to WasmGC type mapping</caption>
 *   <tr><th>Java Type</th><th>GC Field Type</th><th>Notes</th></tr>
 *   <tr><td>{@code int} / {@code Integer}</td><td>i32</td><td></td></tr>
 *   <tr><td>{@code long} / {@code Long}</td><td>i64</td><td></td></tr>
 *   <tr><td>{@code float} / {@code Float}</td><td>f32</td><td></td></tr>
 *   <tr><td>{@code double} / {@code Double}</td><td>f64</td><td></td></tr>
 *   <tr><td>{@code boolean} / {@code Boolean}</td><td>i32</td><td>0 = false, 1 = true</td></tr>
 *   <tr><td>{@code @GcMapped} class</td><td>structref (nullable)</td><td>Recursively marshalled</td></tr>
 * </table>
 *
 * <p>{@code static} and {@code transient} fields are excluded. Fields are
 * mapped in declaration order.
 *
 * <h2>Records</h2>
 *
 * <p>Java records work naturally:
 *
 * <pre>{@code
 * @GcMapped
 * public record Vec3(float x, float y, float z) {}
 *
 * Vec3 v = new Vec3(1.0f, 2.0f, 3.0f);
 * GcStructInstance struct = marshaller.marshal(v);
 * Vec3 roundTripped = marshaller.unmarshal(struct, Vec3.class);
 * }</pre>
 *
 * <p>Records are unmarshalled via their canonical constructor. Regular classes
 * require a no-arg constructor; fields are set reflectively.
 *
 * <h2>Nested Structs</h2>
 *
 * <p>Fields whose type is also {@code @GcMapped} are marshalled as nested
 * GC struct references:
 *
 * <pre>{@code
 * @GcMapped
 * public record Line(Point start, Point end) {}
 *
 * Line line = new Line(new Point(0, 0), new Point(1, 1));
 * GcStructInstance struct = marshaller.marshal(line);
 *
 * // The struct has two reference fields pointing to nested Point structs
 * GcStructInstance startStruct = (GcStructInstance) struct.getField(0).asReference();
 * }</pre>
 *
 * <p>Null nested references are marshalled as {@code GcValue.nullValue()} and
 * unmarshalled back to {@code null}.
 *
 * <h2>Interface Proxy Binding</h2>
 *
 * <p>{@link GcProxyFactory} creates Java interface proxies that automatically
 * marshal {@code @GcMapped} parameters and return values through GC structs.
 * This mirrors {@link ai.tegmentum.webassembly4j.runtime.proxy.ProxyFactory}
 * but uses the GC heap instead of linear memory:
 *
 * <pre>{@code
 * @GcMapped
 * record Point(double x, double y) {}
 *
 * interface Geometry extends AutoCloseable {
 *     @WasmExport("rotate_point")
 *     Point rotate(Point p, double angle);
 *
 *     @WasmExport("distance")
 *     double distance(Point a, Point b);
 * }
 *
 * GcExtension gc = instance.extension(GcExtension.class).orElseThrow();
 * Geometry geom = GcProxyFactory.create(
 *     Geometry.class, engine, module, instance, gc);
 *
 * Point rotated = geom.rotate(new Point(1, 0), Math.PI / 2);
 * double dist = geom.distance(new Point(0, 0), new Point(3, 4));
 * }</pre>
 *
 * <p>Primitive parameters pass through directly to the Wasm function.
 * {@code @GcMapped} parameters are marshalled to {@code GcStructInstance}
 * before the call, and {@code @GcMapped} return types are unmarshalled
 * after.
 *
 * <h2>When to Use GC vs Linear Memory</h2>
 *
 * <ul>
 *   <li><b>Use GC marshalling</b> when working with languages that compile to
 *       WasmGC (Kotlin/Wasm, Dart, OCaml, Java via J2Wasm), or when you want
 *       automatic lifetime management for structured data.</li>
 *   <li><b>Use linear memory marshalling</b> (via {@link ai.tegmentum.webassembly4j.runtime.proxy.ProxyFactory})
 *       when working with languages that compile to traditional Wasm with
 *       linear memory (C, C++, Rust, Go).</li>
 * </ul>
 *
 * <h2>Limitations</h2>
 *
 * <ul>
 *   <li>Not all runtimes support WasmGC. Check for
 *       {@code instance.extension(GcExtension.class)} availability.</li>
 *   <li>Java's GC and the Wasm runtime's GC are separate collectors.
 *       Java references to GC objects pin them on the Wasm heap, but
 *       cross-heap reference cycles are not automatically collected.</li>
 *   <li>String fields are not supported (no native string type in WasmGC).
 *       Use linear memory marshalling for string-heavy interfaces.</li>
 *   <li>Array fields (e.g., {@code int[]}) are not yet supported.
 *       Use {@link ai.tegmentum.webassembly4j.api.gc.GcExtension#createArray}
 *       directly for array data.</li>
 * </ul>
 *
 * @see GcMapped
 * @see GcMarshaller
 * @see GcTypeMapper
 * @see GcProxyFactory
 * @see ai.tegmentum.webassembly4j.api.gc.GcExtension
 */
package ai.tegmentum.webassembly4j.runtime.gc;
