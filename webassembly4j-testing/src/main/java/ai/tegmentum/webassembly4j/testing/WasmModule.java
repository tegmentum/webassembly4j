package ai.tegmentum.webassembly4j.testing;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Specifies a WASM module to load as a test fixture. Can be placed on a test
 * method or test class. The path is resolved from the classpath.
 *
 * <pre>{@code
 * @WasmTest
 * @WasmModule("calculator.wasm")
 * void testAdd(Engine engine, Module module) {
 *     Instance instance = module.instantiate();
 *     // ...
 * }
 * }</pre>
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface WasmModule {

    /**
     * Classpath resource path to the WASM module file.
     */
    String value();
}
