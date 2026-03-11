package ai.tegmentum.webassembly4j.api;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class ComponentInstanceTest {

    @Test
    void componentInstanceExtendsInstance() {
        ComponentInstance instance = createTestInstance();
        // ComponentInstance is-a Instance
        Instance asInstance = instance;
        assertNotNull(asInstance);
    }

    @Test
    void coreLookupMethodsReturnEmpty() {
        ComponentInstance instance = createTestInstance();
        assertFalse(instance.function("test").isPresent());
        assertFalse(instance.memory("memory").isPresent());
        assertFalse(instance.table("table").isPresent());
        assertFalse(instance.global("global").isPresent());
    }

    @Test
    void invokeReturnsResult() {
        ComponentInstance instance = createTestInstance();
        Object result = instance.invoke("add", 1, 2);
        assertEquals(3, result);
    }

    @Test
    void hasFunctionReturnsTrueForKnown() {
        ComponentInstance instance = createTestInstance();
        assertTrue(instance.hasFunction("add"));
        assertFalse(instance.hasFunction("missing"));
    }

    @Test
    void exportedFunctionsReturnsList() {
        ComponentInstance instance = createTestInstance();
        List<String> functions = instance.exportedFunctions();
        assertEquals(Collections.singletonList("add"), functions);
    }

    @Test
    void exportedInterfacesReturnsList() {
        ComponentInstance instance = createTestInstance();
        List<String> interfaces = instance.exportedInterfaces();
        assertEquals(Collections.singletonList("my:pkg/math"), interfaces);
    }

    @Test
    void exportsInterfaceChecksCorrectly() {
        ComponentInstance instance = createTestInstance();
        assertTrue(instance.exportsInterface("my:pkg/math"));
        assertFalse(instance.exportsInterface("nonexistent"));
    }

    private static ComponentInstance createTestInstance() {
        return new ComponentInstance() {
            @Override
            public Object invoke(String functionName, Object... args) {
                if ("add".equals(functionName)) {
                    return ((Integer) args[0]) + ((Integer) args[1]);
                }
                return null;
            }

            @Override
            public boolean hasFunction(String name) {
                return "add".equals(name);
            }

            @Override
            public List<String> exportedFunctions() {
                return Collections.singletonList("add");
            }

            @Override
            public List<String> exportedInterfaces() {
                return Collections.singletonList("my:pkg/math");
            }

            @Override
            public boolean exportsInterface(String name) {
                return "my:pkg/math".equals(name);
            }

            @Override
            public Optional<Function> function(String name) {
                return Optional.empty();
            }

            @Override
            public Optional<Memory> memory(String name) {
                return Optional.empty();
            }

            @Override
            public Optional<Table> table(String name) {
                return Optional.empty();
            }

            @Override
            public Optional<Global> global(String name) {
                return Optional.empty();
            }

            @Override
            public <T> Optional<T> unwrap(Class<T> nativeType) {
                return Optional.empty();
            }
        };
    }
}
