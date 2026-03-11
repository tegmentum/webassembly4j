package ai.tegmentum.webassembly4j.api.gc;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GcStructTypeTest {

    @Test
    void basicBuilder() {
        GcStructType type = GcStructType.builder("Point")
                .addField("x", GcFieldType.f64(), true)
                .addField("y", GcFieldType.f64(), true)
                .build();

        assertEquals("Point", type.name());
        assertEquals(2, type.fieldCount());
        assertEquals("x", type.field(0).name());
        assertEquals(GcFieldType.f64(), type.field(0).type());
        assertTrue(type.field(0).isMutable());
    }

    @Test
    void unnamedFields() {
        GcStructType type = GcStructType.builder("Pair")
                .addField(GcFieldType.i32(), false)
                .addField(GcFieldType.i64(), false)
                .build();

        assertNull(type.field(0).name());
        assertEquals(2, type.fieldCount());
    }

    @Test
    void defaultFinality() {
        GcStructType type = GcStructType.builder("T")
                .addField("f", GcFieldType.i32(), false)
                .build();
        assertEquals(Finality.FINAL, type.finality());
    }

    @Test
    void nonFinalAllowsExtension() {
        GcStructType base = GcStructType.builder("Base")
                .addField("id", GcFieldType.i32(), false)
                .finality(Finality.NON_FINAL)
                .build();

        GcStructType derived = GcStructType.builder("Derived")
                .addField("id", GcFieldType.i32(), false)
                .addField("extra", GcFieldType.i64(), true)
                .extend(base)
                .build();

        assertEquals(base, derived.supertype());
        assertTrue(derived.isSubtypeOf(base));
        assertFalse(base.isSubtypeOf(derived));
    }

    @Test
    void cannotExtendFinalType() {
        GcStructType finalType = GcStructType.builder("Final")
                .addField("f", GcFieldType.i32(), false)
                .build();

        assertThrows(IllegalStateException.class, () ->
                GcStructType.builder("Sub")
                        .addField("f", GcFieldType.i32(), false)
                        .extend(finalType)
                        .build());
    }

    @Test
    void emptyStructNotAllowed() {
        assertThrows(IllegalStateException.class, () ->
                GcStructType.builder("Empty").build());
    }

    @Test
    void fieldsListIsImmutable() {
        GcStructType type = GcStructType.builder("T")
                .addField("f", GcFieldType.i32(), false)
                .build();
        assertThrows(UnsupportedOperationException.class, () ->
                type.fields().add(new GcStructType.Field("x", GcFieldType.i32(), true)));
    }
}
