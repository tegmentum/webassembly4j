package ai.tegmentum.webassembly4j.testing;

import ai.tegmentum.webassembly4j.api.Function;
import ai.tegmentum.webassembly4j.api.Global;
import ai.tegmentum.webassembly4j.api.Instance;
import ai.tegmentum.webassembly4j.api.Memory;
import ai.tegmentum.webassembly4j.api.Table;
import ai.tegmentum.webassembly4j.api.ValueType;
import ai.tegmentum.webassembly4j.api.exception.TrapException;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WasmAssertionsTest {

    @Test
    void assertExportsFunctionSuccess() {
        Instance instance = stubInstance();
        Function fn = WasmAssertions.assertExportsFunction(instance, "add");
        assertNotNull(fn);
    }

    @Test
    void assertExportsFunctionMissing() {
        Instance instance = stubInstance();
        assertThrows(AssertionError.class, () ->
                WasmAssertions.assertExportsFunction(instance, "nonexistent"));
    }

    @Test
    void assertExportsMemorySuccess() {
        Instance instance = stubInstance();
        Memory mem = WasmAssertions.assertExportsMemory(instance, "memory");
        assertNotNull(mem);
    }

    @Test
    void assertExportsMemoryMissing() {
        Instance instance = stubInstance();
        assertThrows(AssertionError.class, () ->
                WasmAssertions.assertExportsMemory(instance, "nonexistent"));
    }

    @Test
    void assertInvokeEqualsSuccess() {
        Function fn = stubAddFunction();
        WasmAssertions.assertInvokeEquals(7, fn, 3, 4);
    }

    @Test
    void assertInvokeEqualsFailure() {
        Function fn = stubAddFunction();
        assertThrows(AssertionError.class, () ->
                WasmAssertions.assertInvokeEquals(99, fn, 3, 4));
    }

    @Test
    void assertTrapsSuccess() {
        Function fn = trappingFunction();
        TrapException trap = WasmAssertions.assertTraps(fn);
        assertNotNull(trap);
    }

    @Test
    void assertTrapsFailure() {
        Function fn = stubAddFunction();
        assertThrows(AssertionError.class, () ->
                WasmAssertions.assertTraps(fn, 1, 2));
    }

    @Test
    void assertTrapsWithMessageSuccess() {
        Function fn = trappingFunction();
        WasmAssertions.assertTrapsWithMessage("unreachable", fn);
    }

    @Test
    void assertTrapsWithMessageWrongMessage() {
        Function fn = trappingFunction();
        assertThrows(AssertionError.class, () ->
                WasmAssertions.assertTrapsWithMessage("division by zero", fn));
    }

    @Test
    void assertMemoryContainsSuccess() {
        Memory mem = stubMemory();
        WasmAssertions.assertMemoryContains(mem, 0, new byte[]{1, 2, 3});
    }

    @Test
    void assertMemoryContainsFailure() {
        Memory mem = stubMemory();
        assertThrows(AssertionError.class, () ->
                WasmAssertions.assertMemoryContains(mem, 0, new byte[]{9, 9, 9}));
    }

    @Test
    void assertMemorySizeSuccess() {
        Memory mem = stubMemory();
        WasmAssertions.assertMemorySize(mem, 100);
    }

    @Test
    void assertMemorySizeTooSmall() {
        Memory mem = stubMemory();
        assertThrows(AssertionError.class, () ->
                WasmAssertions.assertMemorySize(mem, 99999));
    }

    // ─── Stubs ───

    private static Instance stubInstance() {
        return new Instance() {
            @Override
            public Optional<Function> function(String name) {
                if ("add".equals(name)) return Optional.of(stubAddFunction());
                return Optional.empty();
            }
            @Override
            public Optional<Memory> memory(String name) {
                if ("memory".equals(name)) return Optional.of(stubMemory());
                return Optional.empty();
            }
            @Override
            public Optional<Table> table(String name) { return Optional.empty(); }
            @Override
            public Optional<Global> global(String name) { return Optional.empty(); }
            @Override
            public <T> Optional<T> unwrap(Class<T> t) { return Optional.empty(); }
        };
    }

    private static Function stubAddFunction() {
        return new Function() {
            @Override public ValueType[] parameterTypes() {
                return new ValueType[]{ValueType.I32, ValueType.I32};
            }
            @Override public ValueType[] resultTypes() {
                return new ValueType[]{ValueType.I32};
            }
            @Override public Object invoke(Object... args) {
                return ((Number) args[0]).intValue() + ((Number) args[1]).intValue();
            }
        };
    }

    private static Function trappingFunction() {
        return new Function() {
            @Override public ValueType[] parameterTypes() { return new ValueType[0]; }
            @Override public ValueType[] resultTypes() { return new ValueType[0]; }
            @Override public Object invoke(Object... args) {
                throw new TrapException("unreachable instruction executed");
            }
        };
    }

    private static Memory stubMemory() {
        final byte[] data = new byte[65536];
        data[0] = 1; data[1] = 2; data[2] = 3;
        return new Memory() {
            @Override public long byteSize() { return data.length; }
            @Override public ByteBuffer asByteBuffer() { return ByteBuffer.wrap(data); }
            @Override public void write(long offset, byte[] bytes) {
                System.arraycopy(bytes, 0, data, (int) offset, bytes.length);
            }
            @Override public byte[] read(long offset, int length) {
                byte[] result = new byte[length];
                System.arraycopy(data, (int) offset, result, 0, length);
                return result;
            }
            @Override public <T> Optional<T> unwrap(Class<T> t) { return Optional.empty(); }
        };
    }
}
