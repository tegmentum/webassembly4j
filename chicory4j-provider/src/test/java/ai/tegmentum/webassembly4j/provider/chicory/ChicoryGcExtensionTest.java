package ai.tegmentum.webassembly4j.provider.chicory;

import ai.tegmentum.webassembly4j.api.gc.GcArrayInstance;
import ai.tegmentum.webassembly4j.api.gc.GcArrayType;
import ai.tegmentum.webassembly4j.api.gc.GcFieldType;
import ai.tegmentum.webassembly4j.api.gc.GcI31Instance;
import ai.tegmentum.webassembly4j.api.gc.GcReferenceType;
import ai.tegmentum.webassembly4j.api.gc.GcStats;
import ai.tegmentum.webassembly4j.api.gc.GcStructInstance;
import ai.tegmentum.webassembly4j.api.gc.GcStructType;
import ai.tegmentum.webassembly4j.api.gc.GcValue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChicoryGcExtensionTest {

    private ChicoryGcExtension gc;

    @BeforeEach
    void setUp() {
        gc = new ChicoryGcExtension();
    }

    @Test
    void createStructAndReadFields() {
        GcStructType type = GcStructType.builder("Point")
                .addField("x", GcFieldType.f64(), true)
                .addField("y", GcFieldType.f64(), true)
                .build();

        GcStructInstance struct = gc.createStruct(type, GcValue.f64(3.0), GcValue.f64(4.0));
        assertNotNull(struct);
        assertEquals(2, struct.fieldCount());
        assertEquals(3.0, struct.getField(0).asF64(), 0.001);
        assertEquals(4.0, struct.getField(1).asF64(), 0.001);
    }

    @Test
    void createStructAndWriteField() {
        GcStructType type = GcStructType.builder("Point")
                .addField("x", GcFieldType.i32(), true)
                .build();

        GcStructInstance struct = gc.createStruct(type, GcValue.i32(10));
        struct.setField(0, GcValue.i32(20));
        assertEquals(20, struct.getField(0).asI32());
    }

    @Test
    void createArrayAndReadElements() {
        GcArrayType type = GcArrayType.builder("IntArray")
                .elementType(GcFieldType.i32())
                .mutable(true)
                .build();
        GcArrayInstance array = gc.createArray(type, GcValue.i32(1), GcValue.i32(2), GcValue.i32(3));
        assertEquals(3, array.length());
        assertEquals(1, array.getElement(0).asI32());
        assertEquals(2, array.getElement(1).asI32());
        assertEquals(3, array.getElement(2).asI32());
    }

    @Test
    void createArrayWithLength() {
        GcArrayType type = GcArrayType.builder("IntArray")
                .elementType(GcFieldType.i32())
                .mutable(true)
                .build();
        GcArrayInstance array = gc.createArray(type, 5);
        assertEquals(5, array.length());
        assertEquals(0, array.getElement(0).asI32());
    }

    @Test
    void createI31() {
        GcI31Instance i31 = gc.createI31(42);
        assertNotNull(i31);
        assertEquals(42, i31.value());
        assertEquals(42, i31.unsignedValue());
        assertFalse(i31.isNull());
        assertEquals(GcReferenceType.I31_REF, i31.referenceType());
    }

    @Test
    void structReferenceType() {
        GcStructType type = GcStructType.builder("Test")
                .addField("f", GcFieldType.i32(), true)
                .build();
        GcStructInstance struct = gc.createStruct(type, GcValue.i32(0));
        assertEquals(GcReferenceType.STRUCT_REF, struct.referenceType());
        assertFalse(struct.isNull());
    }

    @Test
    void arrayReferenceType() {
        GcArrayType type = GcArrayType.builder("IntArray")
                .elementType(GcFieldType.i32())
                .mutable(true)
                .build();
        GcArrayInstance array = gc.createArray(type, 1);
        assertEquals(GcReferenceType.ARRAY_REF, array.referenceType());
    }

    @Test
    void structRefEquals() {
        GcStructType type = GcStructType.builder("Test")
                .addField("f", GcFieldType.i32(), true)
                .build();
        GcStructInstance a = gc.createStruct(type, GcValue.i32(1));
        GcStructInstance b = gc.createStruct(type, GcValue.i32(1));
        assertTrue(a.refEquals(a));
        assertFalse(a.refEquals(b));
    }

    @Test
    void collectGarbageReturnsStats() {
        GcStats stats = gc.getStats();
        assertNotNull(stats);
        gc.collectGarbage();
    }

    @Test
    void f32FieldRoundTrips() {
        GcStructType type = GcStructType.builder("FloatBox")
                .addField("val", GcFieldType.f32(), true)
                .build();
        GcStructInstance struct = gc.createStruct(type, GcValue.f32(3.14f));
        assertEquals(3.14f, struct.getField(0).asF32(), 0.001f);
    }

    @Test
    void i64FieldRoundTrips() {
        GcStructType type = GcStructType.builder("LongBox")
                .addField("val", GcFieldType.i64(), true)
                .build();
        GcStructInstance struct = gc.createStruct(type, GcValue.i64(Long.MAX_VALUE));
        assertEquals(Long.MAX_VALUE, struct.getField(0).asI64());
    }
}
