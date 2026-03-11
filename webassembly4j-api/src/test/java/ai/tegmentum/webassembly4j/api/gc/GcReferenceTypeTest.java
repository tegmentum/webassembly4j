package ai.tegmentum.webassembly4j.api.gc;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GcReferenceTypeTest {

    @Test
    void subtypingReflexive() {
        for (GcReferenceType t : GcReferenceType.values()) {
            assertTrue(t.isSubtypeOf(t), t + " should be subtype of itself");
        }
    }

    @Test
    void subtypingHierarchy() {
        assertTrue(GcReferenceType.EQ_REF.isSubtypeOf(GcReferenceType.ANY_REF));
        assertTrue(GcReferenceType.I31_REF.isSubtypeOf(GcReferenceType.EQ_REF));
        assertTrue(GcReferenceType.I31_REF.isSubtypeOf(GcReferenceType.ANY_REF));
        assertTrue(GcReferenceType.STRUCT_REF.isSubtypeOf(GcReferenceType.EQ_REF));
        assertTrue(GcReferenceType.STRUCT_REF.isSubtypeOf(GcReferenceType.ANY_REF));
        assertTrue(GcReferenceType.ARRAY_REF.isSubtypeOf(GcReferenceType.EQ_REF));
        assertTrue(GcReferenceType.ARRAY_REF.isSubtypeOf(GcReferenceType.ANY_REF));
    }

    @Test
    void subtypingNotReversible() {
        assertFalse(GcReferenceType.ANY_REF.isSubtypeOf(GcReferenceType.EQ_REF));
        assertFalse(GcReferenceType.EQ_REF.isSubtypeOf(GcReferenceType.STRUCT_REF));
    }

    @Test
    void subtypingSiblings() {
        assertFalse(GcReferenceType.STRUCT_REF.isSubtypeOf(GcReferenceType.ARRAY_REF));
        assertFalse(GcReferenceType.I31_REF.isSubtypeOf(GcReferenceType.STRUCT_REF));
    }

    @Test
    void equalitySupport() {
        assertFalse(GcReferenceType.ANY_REF.supportsEquality());
        assertTrue(GcReferenceType.EQ_REF.supportsEquality());
        assertTrue(GcReferenceType.I31_REF.supportsEquality());
        assertTrue(GcReferenceType.STRUCT_REF.supportsEquality());
        assertTrue(GcReferenceType.ARRAY_REF.supportsEquality());
    }
}
