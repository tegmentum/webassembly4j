package ai.tegmentum.webassembly4j.runtime.marshal;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BumpAllocatorTest {

    @Test
    void allocateSimple() {
        BumpAllocator alloc = new BumpAllocator(0, 1024);
        int ptr = alloc.allocate(16, 1);
        assertEquals(0, ptr);
        assertEquals(16, alloc.currentOffset());
    }

    @Test
    void allocateWithAlignment() {
        BumpAllocator alloc = new BumpAllocator(1, 1024);
        int ptr = alloc.allocate(4, 4);
        // 1 aligned to 4 = 4
        assertEquals(4, ptr);
        assertEquals(8, alloc.currentOffset());
    }

    @Test
    void allocateMultiple() {
        BumpAllocator alloc = new BumpAllocator(0, 1024);
        int ptr1 = alloc.allocate(4, 4);
        assertEquals(0, ptr1);
        int ptr2 = alloc.allocate(8, 8);
        // 4 aligned to 8 = 8
        assertEquals(8, ptr2);
        assertEquals(16, alloc.currentOffset());
    }

    @Test
    void allocateOutOfMemory() {
        BumpAllocator alloc = new BumpAllocator(0, 16);
        alloc.allocate(12, 4);
        assertThrows(IllegalStateException.class,
                () -> alloc.allocate(8, 4));
    }

    @Test
    void reset() {
        BumpAllocator alloc = new BumpAllocator(0, 1024);
        alloc.allocate(100, 1);
        assertEquals(100, alloc.currentOffset());
        alloc.reset(0);
        assertEquals(0, alloc.currentOffset());
    }

    @Test
    void freeIsNoOp() {
        BumpAllocator alloc = new BumpAllocator(0, 1024);
        int ptr = alloc.allocate(16, 4);
        int before = alloc.currentOffset();
        alloc.free(ptr, 16, 4);
        assertEquals(before, alloc.currentOffset());
    }

    @Test
    void allocateExactFit() {
        BumpAllocator alloc = new BumpAllocator(0, 16);
        int ptr = alloc.allocate(16, 1);
        assertEquals(0, ptr);
        assertEquals(16, alloc.currentOffset());
    }
}
