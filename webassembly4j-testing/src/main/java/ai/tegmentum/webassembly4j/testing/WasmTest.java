package ai.tegmentum.webassembly4j.testing;

import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a test method to be executed once per available WebAssembly engine.
 * Each invocation receives an {@link ai.tegmentum.webassembly4j.api.Engine}
 * parameter that is automatically managed (created before, closed after each test).
 *
 * <pre>{@code
 * @WasmTest
 * void addFunction(Engine engine) {
 *     Module module = engine.loadModule(bytes);
 *     Instance instance = module.instantiate();
 *     Function add = instance.function("add").orElseThrow();
 *     assertEquals(7, add.invoke(3, 4));
 * }
 * }</pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@TestTemplate
@ExtendWith(WasmTestExtension.class)
public @interface WasmTest {
}
