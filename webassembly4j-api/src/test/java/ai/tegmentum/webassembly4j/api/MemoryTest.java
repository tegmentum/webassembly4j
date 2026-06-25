package ai.tegmentum.webassembly4j.api;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MemoryTest {

    /** A minimal Memory that only implements the abstract methods. */
    private static Memory fixedSize(long byteSize) {
        return new Memory() {
            @Override
            public long byteSize() {
                return byteSize;
            }

            @Override
            public ByteBuffer asByteBuffer() {
                throw new UnsupportedOperationException();
            }

            @Override
            public void write(long offset, byte[] bytes) {
                throw new UnsupportedOperationException();
            }

            @Override
            public byte[] read(long offset, int length) {
                throw new UnsupportedOperationException();
            }

            @Override
            public <T> Optional<T> unwrap(Class<T> nativeType) {
                return Optional.empty();
            }
        };
    }

    @Test
    void pageCountDerivedFromByteSize() {
        assertEquals(3, fixedSize(3 * Memory.PAGE_SIZE).pageCount());
    }

    @Test
    void maxPageCountUnknownByDefault() {
        assertEquals(-1, fixedSize(Memory.PAGE_SIZE).maxPageCount());
    }

    @Test
    void growUnsupportedByDefault() {
        assertThrows(UnsupportedOperationException.class,
                () -> fixedSize(Memory.PAGE_SIZE).grow(1));
    }
}
