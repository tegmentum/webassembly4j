package ai.tegmentum.webassembly4j.api.gc;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GcValueTest {

    @Test
    void i32Value() {
        GcValue v = GcValue.i32(42);
        assertEquals(GcValue.Type.I32, v.type());
        assertEquals(42, v.asI32());
    }

    @Test
    void i64Value() {
        GcValue v = GcValue.i64(Long.MAX_VALUE);
        assertEquals(GcValue.Type.I64, v.type());
        assertEquals(Long.MAX_VALUE, v.asI64());
    }

    @Test
    void f32Value() {
        GcValue v = GcValue.f32(3.14f);
        assertEquals(GcValue.Type.F32, v.type());
        assertEquals(3.14f, v.asF32(), 0.001f);
    }

    @Test
    void f64Value() {
        GcValue v = GcValue.f64(2.718);
        assertEquals(GcValue.Type.F64, v.type());
        assertEquals(2.718, v.asF64(), 0.001);
    }

    @Test
    void nullValue() {
        GcValue v = GcValue.nullValue();
        assertEquals(GcValue.Type.NULL, v.type());
        assertTrue(v.isNull());
        assertTrue(v.isReference());
    }

    @Test
    void wrongTypeCastThrows() {
        GcValue v = GcValue.i32(1);
        assertThrows(GcException.class, v::asI64);
        assertThrows(GcException.class, v::asF32);
        assertThrows(GcException.class, v::asF64);
        assertThrows(GcException.class, v::asReference);
    }

    @Test
    void referenceValueRequiresNonNull() {
        assertThrows(NullPointerException.class, () -> GcValue.reference(null));
    }

    @Test
    void toStringReadable() {
        assertEquals("i32(42)", GcValue.i32(42).toString());
        assertEquals("null", GcValue.nullValue().toString());
    }
}
