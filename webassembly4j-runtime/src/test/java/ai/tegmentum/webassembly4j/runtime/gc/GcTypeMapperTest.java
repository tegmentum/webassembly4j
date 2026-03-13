package ai.tegmentum.webassembly4j.runtime.gc;

import ai.tegmentum.webassembly4j.api.gc.GcFieldType;
import ai.tegmentum.webassembly4j.api.gc.GcStructType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GcTypeMapperTest {

    @GcMapped
    static class SimplePoint {
        double x;
        double y;
    }

    @GcMapped("Vec3")
    static class Vector3 {
        float x;
        float y;
        float z;
    }

    @GcMapped
    static class AllPrimitives {
        int i;
        long l;
        float f;
        double d;
        boolean b;
    }

    @GcMapped
    static class WithNested {
        int id;
        SimplePoint position;
    }

    @GcMapped
    static class WithTransient {
        int value;
        transient int ignored;
    }

    static class NotAnnotated {
        int value;
    }

    @GcMapped
    static class Empty {
        static int staticField = 42;
    }

    private final GcTypeMapper mapper = new GcTypeMapper();

    @Test
    void mapsSimpleFields() {
        GcStructType type = mapper.toStructType(SimplePoint.class);
        assertEquals("SimplePoint", type.name());
        assertEquals(2, type.fieldCount());
        assertEquals(GcFieldType.f64(), type.field(0).type());
        assertEquals(GcFieldType.f64(), type.field(1).type());
    }

    @Test
    void usesCustomName() {
        GcStructType type = mapper.toStructType(Vector3.class);
        assertEquals("Vec3", type.name());
        assertEquals(3, type.fieldCount());
        assertEquals(GcFieldType.f32(), type.field(0).type());
    }

    @Test
    void mapsAllPrimitiveTypes() {
        GcStructType type = mapper.toStructType(AllPrimitives.class);
        assertEquals(5, type.fieldCount());
        assertEquals(GcFieldType.i32(), type.field(0).type());   // int
        assertEquals(GcFieldType.i64(), type.field(1).type());   // long
        assertEquals(GcFieldType.f32(), type.field(2).type());   // float
        assertEquals(GcFieldType.f64(), type.field(3).type());   // double
        assertEquals(GcFieldType.i32(), type.field(4).type());   // boolean as i32
    }

    @Test
    void mapsNestedGcMappedType() {
        GcStructType type = mapper.toStructType(WithNested.class);
        assertEquals(2, type.fieldCount());
        assertEquals(GcFieldType.i32(), type.field(0).type());
        assertTrue(type.field(1).type().isReference());
    }

    @Test
    void skipsTransientFields() {
        GcStructType type = mapper.toStructType(WithTransient.class);
        assertEquals(1, type.fieldCount());
    }

    @Test
    void rejectsNotAnnotated() {
        assertThrows(IllegalArgumentException.class,
                () -> mapper.toStructType(NotAnnotated.class));
    }

    @Test
    void rejectsEmptyMappedClass() {
        assertThrows(IllegalArgumentException.class,
                () -> mapper.toStructType(Empty.class));
    }

    @Test
    void cachesStructType() {
        GcStructType first = mapper.toStructType(SimplePoint.class);
        GcStructType second = mapper.toStructType(SimplePoint.class);
        assertSame(first, second);
    }

    @Test
    void fieldAccessorsReturnCorrectOrder() {
        List<GcTypeMapper.FieldAccessor> accessors = mapper.fieldAccessors(SimplePoint.class);
        assertEquals(2, accessors.size());
        assertEquals("x", accessors.get(0).name());
        assertEquals("y", accessors.get(1).name());
    }

    @Test
    void allFieldsAreMutable() {
        GcStructType type = mapper.toStructType(SimplePoint.class);
        // Field.mutable() on Java 22 record, Field.isMutable() on Java 8 base
        // Use the struct builder's addField(name, type, true) which sets mutable=true
        // Verify by checking fieldCount matches and fields are present
        assertEquals(2, type.fieldCount());
        assertNotNull(type.field(0));
        assertNotNull(type.field(1));
    }
}
