package ai.tegmentum.webassembly4j.api.component.async;

import ai.tegmentum.webassembly4j.api.ComponentInstance;
import ai.tegmentum.webassembly4j.api.exception.UnsupportedFeatureException;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;

class ComponentInstanceAsyncTest {

    @Test
    void defaultRunConcurrentThrows() {
        ComponentInstance instance = new MinimalComponentInstance();
        assertThrows(UnsupportedFeatureException.class, () ->
                instance.runConcurrent(scope -> scope.callConcurrent("fn")));
    }

    private static class MinimalComponentInstance implements ComponentInstance {
        @Override
        public Object invoke(String functionName, Object... args) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean hasFunction(String name) {
            return false;
        }

        @Override
        public List<String> exportedFunctions() {
            return Collections.emptyList();
        }

        @Override
        public List<String> exportedInterfaces() {
            return Collections.emptyList();
        }

        @Override
        public boolean exportsInterface(String name) {
            return false;
        }

        @Override
        public java.util.Optional<ai.tegmentum.webassembly4j.api.Function> function(String name) {
            return java.util.Optional.empty();
        }

        @Override
        public java.util.Optional<ai.tegmentum.webassembly4j.api.Memory> memory(String name) {
            return java.util.Optional.empty();
        }

        @Override
        public java.util.Optional<ai.tegmentum.webassembly4j.api.Table> table(String name) {
            return java.util.Optional.empty();
        }

        @Override
        public java.util.Optional<ai.tegmentum.webassembly4j.api.Global> global(String name) {
            return java.util.Optional.empty();
        }

        @Override
        public <T> java.util.Optional<T> unwrap(Class<T> nativeType) {
            return java.util.Optional.empty();
        }
    }
}
