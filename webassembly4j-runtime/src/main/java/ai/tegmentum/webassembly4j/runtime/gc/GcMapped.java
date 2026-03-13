package ai.tegmentum.webassembly4j.runtime.gc;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a Java class or record as mappable to a WebAssembly GC struct type.
 *
 * <p>Fields are mapped in declaration order to GC struct fields. Supported
 * field types are:
 * <ul>
 *   <li>{@code int} / {@code Integer} → i32</li>
 *   <li>{@code long} / {@code Long} → i64</li>
 *   <li>{@code float} / {@code Float} → f32</li>
 *   <li>{@code double} / {@code Double} → f64</li>
 *   <li>{@code boolean} / {@code Boolean} → i32 (0/1)</li>
 *   <li>Another {@code @GcMapped} type → struct reference</li>
 * </ul>
 *
 * <p>Example:
 * <pre>{@code
 * @GcMapped
 * public record Point(double x, double y) {}
 *
 * GcMarshaller marshaller = GcMarshaller.forExtension(gc);
 * GcStructInstance struct = marshaller.marshal(new Point(1.0, 2.0));
 * Point point = marshaller.unmarshal(struct, Point.class);
 * }</pre>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface GcMapped {

    /**
     * Optional WasmGC struct type name. If empty, the simple class name is used.
     */
    String value() default "";
}
