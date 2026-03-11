package ai.tegmentum.webassembly4j.api.gc;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GcFieldTypeTest {

    @Test
    void primitiveTypes() {
        assertEquals(GcFieldType.Kind.I32, GcFieldType.i32().kind());
        assertEquals(GcFieldType.Kind.I64, GcFieldType.i64().kind());
        assertEquals(GcFieldType.Kind.F32, GcFieldType.f32().kind());
        assertEquals(GcFieldType.Kind.F64, GcFieldType.f64().kind());
        assertEquals(GcFieldType.Kind.V128, GcFieldType.v128().kind());
    }

    @Test
    void packedTypes() {
        assertTrue(GcFieldType.packedI8().isPacked());
        assertTrue(GcFieldType.packedI16().isPacked());
        assertFalse(GcFieldType.i32().isPacked());
    }

    @Test
    void referenceTypes() {
        GcFieldType anyRef = GcFieldType.anyRef();
        assertTrue(anyRef.isReference());
        assertTrue(anyRef.isNullable());
        assertEquals(GcReferenceType.ANY_REF, anyRef.referenceType());

        GcFieldType nonNull = GcFieldType.reference(GcReferenceType.STRUCT_REF, false);
        assertTrue(nonNull.isReference());
        assertFalse(nonNull.isNullable());
    }

    @Test
    void sizesCorrect() {
        assertEquals(1, GcFieldType.packedI8().sizeBytes());
        assertEquals(2, GcFieldType.packedI16().sizeBytes());
        assertEquals(4, GcFieldType.i32().sizeBytes());
        assertEquals(4, GcFieldType.f32().sizeBytes());
        assertEquals(8, GcFieldType.i64().sizeBytes());
        assertEquals(8, GcFieldType.f64().sizeBytes());
        assertEquals(16, GcFieldType.v128().sizeBytes());
        assertEquals(4, GcFieldType.anyRef().sizeBytes());
    }

    @Test
    void equality() {
        assertEquals(GcFieldType.i32(), GcFieldType.i32());
        assertNotEquals(GcFieldType.i32(), GcFieldType.i64());
        assertEquals(GcFieldType.anyRef(), GcFieldType.reference(GcReferenceType.ANY_REF, true));
        assertNotEquals(
                GcFieldType.reference(GcReferenceType.STRUCT_REF, true),
                GcFieldType.reference(GcReferenceType.STRUCT_REF, false));
    }

    @Test
    void toStringReadable() {
        assertEquals("i32", GcFieldType.i32().toString());
        assertEquals("(ref null ANY_REF)", GcFieldType.anyRef().toString());
        assertEquals("(ref STRUCT_REF)", GcFieldType.reference(GcReferenceType.STRUCT_REF, false).toString());
    }

    @Test
    void referenceTypeRequiresNonNull() {
        assertThrows(NullPointerException.class, () -> GcFieldType.reference(null, true));
    }
}
