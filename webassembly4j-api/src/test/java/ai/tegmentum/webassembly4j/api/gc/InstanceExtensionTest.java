package ai.tegmentum.webassembly4j.api.gc;

import ai.tegmentum.webassembly4j.api.Instance;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class InstanceExtensionTest {

    @Test
    void defaultExtensionReturnsEmpty() {
        Instance instance = new MinimalInstance();
        Optional<GcExtension> gc = instance.extension(GcExtension.class);
        assertFalse(gc.isPresent());
    }

    /**
     * Minimal Instance implementation to test the default extension() method.
     */
    private static class MinimalInstance implements Instance {
        @Override
        public Optional<ai.tegmentum.webassembly4j.api.Function> function(String name) {
            return Optional.empty();
        }

        @Override
        public Optional<ai.tegmentum.webassembly4j.api.Memory> memory(String name) {
            return Optional.empty();
        }

        @Override
        public Optional<ai.tegmentum.webassembly4j.api.Table> table(String name) {
            return Optional.empty();
        }

        @Override
        public Optional<ai.tegmentum.webassembly4j.api.Global> global(String name) {
            return Optional.empty();
        }

        @Override
        public <T> Optional<T> unwrap(Class<T> nativeType) {
            return Optional.empty();
        }
    }
}
