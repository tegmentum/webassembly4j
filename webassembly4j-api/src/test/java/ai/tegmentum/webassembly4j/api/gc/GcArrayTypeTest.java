package ai.tegmentum.webassembly4j.api.gc;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GcArrayTypeTest {

    @Test
    void basicBuilder() {
        GcArrayType type = GcArrayType.builder("IntArray")
                .elementType(GcFieldType.i32())
                .mutable(true)
                .build();

        assertEquals("IntArray", type.name());
        assertEquals(GcFieldType.i32(), type.elementType());
        assertTrue(type.isMutable());
    }

    @Test
    void immutableByDefault() {
        GcArrayType type = GcArrayType.builder("ReadOnly")
                .elementType(GcFieldType.f64())
                .build();
        assertFalse(type.isMutable());
    }

    @Test
    void requiresElementType() {
        assertThrows(IllegalStateException.class, () ->
                GcArrayType.builder("Bad").build());
    }

    @Test
    void cannotExtendFinalType() {
        GcArrayType finalType = GcArrayType.builder("Final")
                .elementType(GcFieldType.i32())
                .build();

        assertThrows(IllegalStateException.class, () ->
                GcArrayType.builder("Sub")
                        .elementType(GcFieldType.i32())
                        .extend(finalType)
                        .build());
    }

    @Test
    void subtypingWithInheritance() {
        GcArrayType base = GcArrayType.builder("Base")
                .elementType(GcFieldType.i32())
                .finality(Finality.NON_FINAL)
                .build();

        GcArrayType derived = GcArrayType.builder("Derived")
                .elementType(GcFieldType.i32())
                .extend(base)
                .build();

        assertTrue(derived.isSubtypeOf(base));
        assertFalse(base.isSubtypeOf(derived));
    }

    @Test
    void toStringReadable() {
        GcArrayType type = GcArrayType.builder("Arr")
                .elementType(GcFieldType.i32())
                .mutable(true)
                .build();
        assertEquals("array Arr (i32, mut)", type.toString());
    }
}
