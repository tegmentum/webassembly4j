package ai.tegmentum.webassembly4j.runtime.gc;

import ai.tegmentum.webassembly4j.api.gc.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GcMarshallerTest {

    @GcMapped
    static class Point {
        double x;
        double y;

        Point() {}

        Point(double x, double y) {
            this.x = x;
            this.y = y;
        }
    }

    @GcMapped
    static class AllTypes {
        int i;
        long l;
        float f;
        double d;
        boolean b;

        AllTypes() {}
    }

    @GcMapped
    static class WithNested {
        int id;
        Point position;

        WithNested() {}
    }

    private StubGcExtension gc;
    private GcMarshaller marshaller;

    @BeforeEach
    void setUp() {
        gc = new StubGcExtension();
        marshaller = GcMarshaller.forExtension(gc);
    }

    @Test
    void marshalSimpleClass() {
        Point p = new Point(3.0, 4.0);
        GcStructInstance struct = marshaller.marshal(p);

        assertEquals(3.0, struct.getField(0).asF64());
        assertEquals(4.0, struct.getField(1).asF64());
    }

    @Test
    void unmarshalSimpleClass() {
        StubStructInstance stub = new StubStructInstance(
                null, GcValue.f64(1.5), GcValue.f64(2.5));

        Point p = marshaller.unmarshal(stub, Point.class);
        assertEquals(1.5, p.x);
        assertEquals(2.5, p.y);
    }

    @Test
    void roundTripSimpleClass() {
        Point original = new Point(7.0, 8.0);
        GcStructInstance struct = marshaller.marshal(original);
        Point result = marshaller.unmarshal((StubStructInstance) struct, Point.class);
        assertEquals(original.x, result.x);
        assertEquals(original.y, result.y);
    }

    @Test
    void marshalAllPrimitiveTypes() {
        AllTypes obj = new AllTypes();
        obj.i = 42;
        obj.l = 123456789L;
        obj.f = 1.5f;
        obj.d = 2.718;
        obj.b = true;

        GcStructInstance struct = marshaller.marshal(obj);
        assertEquals(42, struct.getField(0).asI32());
        assertEquals(123456789L, struct.getField(1).asI64());
        assertEquals(1.5f, struct.getField(2).asF32());
        assertEquals(2.718, struct.getField(3).asF64());
        assertEquals(1, struct.getField(4).asI32()); // boolean as i32
    }

    @Test
    void unmarshalAllPrimitiveTypes() {
        StubStructInstance stub = new StubStructInstance(null,
                GcValue.i32(99),
                GcValue.i64(987654321L),
                GcValue.f32(3.14f),
                GcValue.f64(1.618),
                GcValue.i32(0));

        AllTypes obj = marshaller.unmarshal(stub, AllTypes.class);
        assertEquals(99, obj.i);
        assertEquals(987654321L, obj.l);
        assertEquals(3.14f, obj.f);
        assertEquals(1.618, obj.d);
        assertFalse(obj.b);
    }

    @Test
    void marshalNestedGcMappedType() {
        WithNested obj = new WithNested();
        obj.id = 1;
        obj.position = new Point(5.0, 6.0);

        GcStructInstance struct = marshaller.marshal(obj);
        assertEquals(1, struct.getField(0).asI32());
        assertTrue(struct.getField(1).isReference());

        GcStructInstance nested = (GcStructInstance) struct.getField(1).asReference();
        assertEquals(5.0, nested.getField(0).asF64());
        assertEquals(6.0, nested.getField(1).asF64());
    }

    @Test
    void unmarshalNestedGcMappedType() {
        StubStructInstance innerStub = new StubStructInstance(null,
                GcValue.f64(10.0), GcValue.f64(20.0));

        StubStructInstance outerStub = new StubStructInstance(null,
                GcValue.i32(42), GcValue.reference(innerStub));

        WithNested obj = marshaller.unmarshal(outerStub, WithNested.class);
        assertEquals(42, obj.id);
        assertNotNull(obj.position);
        assertEquals(10.0, obj.position.x);
        assertEquals(20.0, obj.position.y);
    }

    @Test
    void marshalNullNestedField() {
        WithNested obj = new WithNested();
        obj.id = 1;
        obj.position = null;

        GcStructInstance struct = marshaller.marshal(obj);
        assertTrue(struct.getField(1).isNull());
    }

    @Test
    void unmarshalNullNestedField() {
        StubStructInstance stub = new StubStructInstance(null,
                GcValue.i32(1), GcValue.nullValue());

        WithNested obj = marshaller.unmarshal(stub, WithNested.class);
        assertEquals(1, obj.id);
        assertNull(obj.position);
    }

    @Test
    void rejectsNonGcMappedClass() {
        assertThrows(IllegalArgumentException.class,
                () -> marshaller.marshal("not a gc mapped object"));
    }

    @Test
    void typeMapperIsAccessible() {
        assertNotNull(marshaller.typeMapper());
    }

    // --- Stubs ---

    /**
     * Stub GcExtension that creates StubStructInstances.
     */
    private static class StubGcExtension implements GcExtension {
        @Override
        public GcStructInstance createStruct(GcStructType type, GcValue... values) {
            return new StubStructInstance(type, values);
        }

        @Override
        public GcStructInstance createStruct(GcStructType type, List<GcValue> values) {
            return new StubStructInstance(type, values.toArray(new GcValue[0]));
        }

        @Override
        public GcArrayInstance createArray(GcArrayType type, GcValue... elements) {
            throw new UnsupportedOperationException();
        }

        @Override
        public GcArrayInstance createArray(GcArrayType type, List<GcValue> elements) {
            throw new UnsupportedOperationException();
        }

        @Override
        public GcArrayInstance createArray(GcArrayType type, int length) {
            throw new UnsupportedOperationException();
        }

        @Override
        public GcI31Instance createI31(int value) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean refTest(GcObject object, GcReferenceType refType) {
            return false;
        }

        @Override
        public GcObject refCast(GcObject object, GcReferenceType refType) {
            throw new UnsupportedOperationException();
        }

        @Override
        public GcStats collectGarbage() {
            return GcStats.builder().build();
        }

        @Override
        public GcStats getStats() {
            return GcStats.builder().build();
        }
    }

    /**
     * Stub GcStructInstance backed by a simple GcValue array.
     */
    static class StubStructInstance implements GcStructInstance {
        private final GcStructType type;
        private final List<GcValue> fields;

        StubStructInstance(GcStructType type, GcValue... values) {
            this.type = type;
            this.fields = new ArrayList<>();
            for (GcValue v : values) {
                this.fields.add(v);
            }
        }

        @Override
        public GcStructType type() { return type; }

        @Override
        public int fieldCount() { return fields.size(); }

        @Override
        public GcValue getField(int index) { return fields.get(index); }

        @Override
        public void setField(int index, GcValue value) { fields.set(index, value); }

        @Override
        public boolean isNull() { return false; }

        @Override
        public boolean refEquals(GcObject other) { return this == other; }
    }
}
