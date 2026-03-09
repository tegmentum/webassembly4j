package ai.tegmentum.webassembly4j.runtime.marshal;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class CanonicalABITest {

    @Test
    void alignToAlreadyAligned() {
        assertEquals(0, CanonicalABI.alignTo(0, 4));
        assertEquals(4, CanonicalABI.alignTo(4, 4));
        assertEquals(8, CanonicalABI.alignTo(8, 4));
    }

    @Test
    void alignToNeedsAlignment() {
        assertEquals(4, CanonicalABI.alignTo(1, 4));
        assertEquals(4, CanonicalABI.alignTo(2, 4));
        assertEquals(4, CanonicalABI.alignTo(3, 4));
        assertEquals(8, CanonicalABI.alignTo(5, 4));
    }

    @Test
    void alignToSingleByteAlignment() {
        assertEquals(0, CanonicalABI.alignTo(0, 1));
        assertEquals(5, CanonicalABI.alignTo(5, 1));
    }

    @Test
    void alignToEightByteAlignment() {
        assertEquals(8, CanonicalABI.alignTo(1, 8));
        assertEquals(8, CanonicalABI.alignTo(7, 8));
        assertEquals(8, CanonicalABI.alignTo(8, 8));
        assertEquals(16, CanonicalABI.alignTo(9, 8));
    }

    @Test
    void fieldOffsetsSimpleRecord() {
        // Record with two i32 fields: no padding needed
        int[] offsets = CanonicalABI.fieldOffsets(TypeLayout.S32, TypeLayout.S32);
        assertArrayEquals(new int[]{0, 4}, offsets);
    }

    @Test
    void fieldOffsetsWithPadding() {
        // Record: bool (1,1), i32 (4,4) — bool at 0, i32 at 4 (padded)
        int[] offsets = CanonicalABI.fieldOffsets(TypeLayout.BOOL, TypeLayout.S32);
        assertArrayEquals(new int[]{0, 4}, offsets);
    }

    @Test
    void fieldOffsetsStringAndI32() {
        // Record: string (8,4), i32 (4,4)
        int[] offsets = CanonicalABI.fieldOffsets(TypeLayout.STRING, TypeLayout.S32);
        assertArrayEquals(new int[]{0, 8}, offsets);
    }

    @Test
    void fieldOffsetsI64AndBool() {
        // Record: i64 (8,8), bool (1,1) — i64 at 0, bool at 8
        int[] offsets = CanonicalABI.fieldOffsets(TypeLayout.S64, TypeLayout.BOOL);
        assertArrayEquals(new int[]{0, 8}, offsets);
    }

    @Test
    void recordSizeWithTrailingPadding() {
        // Record: i64 (8,8), bool (1,1) — ends at 9, padded to 16 (align 8)
        int size = CanonicalABI.recordSize(TypeLayout.S64, TypeLayout.BOOL);
        assertEquals(16, size);
    }

    @Test
    void recordSizeNoTrailingPadding() {
        // Record: i32 (4,4), i32 (4,4) — ends at 8, align 4, so 8
        int size = CanonicalABI.recordSize(TypeLayout.S32, TypeLayout.S32);
        assertEquals(8, size);
    }

    @Test
    void recordSizeSingleField() {
        assertEquals(4, CanonicalABI.recordSize(TypeLayout.S32));
        assertEquals(1, CanonicalABI.recordSize(TypeLayout.BOOL));
        assertEquals(8, CanonicalABI.recordSize(TypeLayout.S64));
    }

    @Test
    void recordSizeEmpty() {
        assertEquals(0, CanonicalABI.recordSize());
    }

    @Test
    void recordSizeStringRecord() {
        // Record: string (8,4) — ends at 8, align 4, size 8
        int size = CanonicalABI.recordSize(TypeLayout.STRING);
        assertEquals(8, size);
    }

    @Test
    void recordAlignmentMaxOfFields() {
        assertEquals(4, CanonicalABI.recordAlignment(TypeLayout.BOOL, TypeLayout.S32));
        assertEquals(8, CanonicalABI.recordAlignment(TypeLayout.S32, TypeLayout.S64));
        assertEquals(1, CanonicalABI.recordAlignment(TypeLayout.BOOL, TypeLayout.S8));
    }

    @Test
    void complexRecordLayout() {
        // Record: bool (1,1), i64 (8,8), string (8,4), i32 (4,4)
        // bool at 0, i64 at 8 (padded), string at 16, i32 at 24
        int[] offsets = CanonicalABI.fieldOffsets(
                TypeLayout.BOOL, TypeLayout.S64, TypeLayout.STRING, TypeLayout.S32);
        assertArrayEquals(new int[]{0, 8, 16, 24}, offsets);

        int size = CanonicalABI.recordSize(
                TypeLayout.BOOL, TypeLayout.S64, TypeLayout.STRING, TypeLayout.S32);
        // Ends at 28, record alignment is 8, padded to 32
        assertEquals(32, size);
    }
}
