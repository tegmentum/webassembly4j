package ai.tegmentum.webassembly4j.testing;

import ai.tegmentum.webassembly4j.api.Function;
import ai.tegmentum.webassembly4j.api.Instance;
import ai.tegmentum.webassembly4j.api.Memory;
import ai.tegmentum.webassembly4j.api.exception.TrapException;

import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Assertion helpers for WebAssembly testing.
 */
public final class WasmAssertions {

    private WasmAssertions() {}

    /**
     * Asserts that the instance exports a function with the given name.
     */
    public static Function assertExportsFunction(Instance instance, String name) {
        Objects.requireNonNull(instance, "instance");
        Objects.requireNonNull(name, "name");
        return instance.function(name).orElseGet(() -> {
            fail("Expected function export '" + name + "' not found");
            return null; // unreachable
        });
    }

    /**
     * Asserts that the instance exports a memory with the given name.
     */
    public static Memory assertExportsMemory(Instance instance, String name) {
        Objects.requireNonNull(instance, "instance");
        Objects.requireNonNull(name, "name");
        return instance.memory(name).orElseGet(() -> {
            fail("Expected memory export '" + name + "' not found");
            return null;
        });
    }

    /**
     * Asserts that invoking the function returns the expected result.
     */
    public static void assertInvokeEquals(Object expected, Function function, Object... args) {
        Objects.requireNonNull(function, "function");
        Object result = function.invoke(args);
        assertEquals(expected, result,
                "Function invocation returned unexpected result");
    }

    /**
     * Asserts that invoking the function causes a WASM trap.
     */
    public static TrapException assertTraps(Function function, Object... args) {
        Objects.requireNonNull(function, "function");
        return assertThrows(TrapException.class, () -> function.invoke(args));
    }

    /**
     * Asserts that invoking the function causes a trap with a message containing
     * the given substring.
     */
    public static void assertTrapsWithMessage(String messageSubstring,
                                               Function function, Object... args) {
        TrapException trap = assertTraps(function, args);
        assertNotNull(trap.getMessage(), "Trap message should not be null");
        assertTrue(trap.getMessage().contains(messageSubstring),
                "Expected trap message containing '" + messageSubstring
                + "' but got: " + trap.getMessage());
    }

    /**
     * Asserts that the memory contains the expected bytes at the given offset.
     */
    public static void assertMemoryContains(Memory memory, long offset, byte[] expected) {
        Objects.requireNonNull(memory, "memory");
        Objects.requireNonNull(expected, "expected");
        byte[] actual = memory.read(offset, expected.length);
        assertArrayEquals(expected, actual,
                "Memory contents at offset " + offset + " do not match");
    }

    /**
     * Asserts that the memory byte size is at least the given minimum.
     */
    public static void assertMemorySize(Memory memory, long minimumBytes) {
        Objects.requireNonNull(memory, "memory");
        assertTrue(memory.byteSize() >= minimumBytes,
                "Expected memory size >= " + minimumBytes
                + " but was " + memory.byteSize());
    }
}
