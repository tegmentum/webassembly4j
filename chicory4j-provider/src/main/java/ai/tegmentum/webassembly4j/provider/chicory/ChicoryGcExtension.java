package ai.tegmentum.webassembly4j.provider.chicory;

import ai.tegmentum.webassembly4j.api.gc.GcArrayInstance;
import ai.tegmentum.webassembly4j.api.gc.GcArrayType;
import ai.tegmentum.webassembly4j.api.gc.GcException;
import ai.tegmentum.webassembly4j.api.gc.GcExtension;
import ai.tegmentum.webassembly4j.api.gc.GcFieldType;
import ai.tegmentum.webassembly4j.api.gc.GcI31Instance;
import ai.tegmentum.webassembly4j.api.gc.GcObject;
import ai.tegmentum.webassembly4j.api.gc.GcReferenceType;
import ai.tegmentum.webassembly4j.api.gc.GcStats;
import ai.tegmentum.webassembly4j.api.gc.GcStructInstance;
import ai.tegmentum.webassembly4j.api.gc.GcStructType;
import ai.tegmentum.webassembly4j.api.gc.GcValue;
import com.dylibso.chicory.runtime.WasmArray;
import com.dylibso.chicory.runtime.WasmI31Ref;
import com.dylibso.chicory.runtime.WasmStruct;
import com.dylibso.chicory.wasm.types.Value;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Chicory implementation of the GC extension.
 *
 * <p>Chicory GC objects are plain Java objects allocated on the JVM heap.
 * Values are encoded as {@code long} using Chicory's {@link Value} helpers.
 * A synthetic type index starting from 0x10000 is used for programmatically
 * created types to avoid collision with module type sections.
 */
final class ChicoryGcExtension implements GcExtension {

    private static final int SYNTHETIC_TYPE_BASE = 0x10000;

    private final AtomicLong allocationCount = new AtomicLong();
    private final AtomicLong nextTypeIndex = new AtomicLong(SYNTHETIC_TYPE_BASE);

    @Override
    public GcStructInstance createStruct(GcStructType type, GcValue... values) {
        if (values.length != type.fieldCount()) {
            throw new GcException("Expected " + type.fieldCount()
                    + " field values but got " + values.length);
        }
        long[] fields = new long[values.length];
        for (int i = 0; i < values.length; i++) {
            fields[i] = encodeValue(values[i], type.field(i).type());
        }
        int typeIdx = (int) nextTypeIndex.getAndIncrement();
        WasmStruct nativeStruct = new WasmStruct(typeIdx, fields);
        allocationCount.incrementAndGet();
        return new ChicoryGcStructInstance(nativeStruct, type);
    }

    @Override
    public GcStructInstance createStruct(GcStructType type, List<GcValue> values) {
        return createStruct(type, values.toArray(new GcValue[0]));
    }

    @Override
    public GcArrayInstance createArray(GcArrayType type, GcValue... elements) {
        long[] encoded = new long[elements.length];
        GcFieldType elemType = type.elementType();
        for (int i = 0; i < elements.length; i++) {
            encoded[i] = encodeValue(elements[i], elemType);
        }
        int typeIdx = (int) nextTypeIndex.getAndIncrement();
        WasmArray nativeArray = new WasmArray(typeIdx, encoded);
        allocationCount.incrementAndGet();
        return new ChicoryGcArrayInstance(nativeArray, type);
    }

    @Override
    public GcArrayInstance createArray(GcArrayType type, List<GcValue> elements) {
        return createArray(type, elements.toArray(new GcValue[0]));
    }

    @Override
    public GcArrayInstance createArray(GcArrayType type, int length) {
        if (length < 0) {
            throw new GcException("Array length must not be negative: " + length);
        }
        long[] encoded = new long[length];
        int typeIdx = (int) nextTypeIndex.getAndIncrement();
        WasmArray nativeArray = new WasmArray(typeIdx, encoded);
        allocationCount.incrementAndGet();
        return new ChicoryGcArrayInstance(nativeArray, type);
    }

    @Override
    public GcI31Instance createI31(int value) {
        WasmI31Ref nativeRef = new WasmI31Ref(value);
        allocationCount.incrementAndGet();
        return new ChicoryGcI31Instance(nativeRef);
    }

    @Override
    public boolean refTest(GcObject object, GcReferenceType refType) {
        return true;
    }

    @Override
    public GcObject refCast(GcObject object, GcReferenceType refType) {
        return object;
    }

    @Override
    public GcStats collectGarbage() {
        System.gc();
        return getStats();
    }

    @Override
    public GcStats getStats() {
        return GcStats.builder()
                .totalAllocated(allocationCount.get())
                .liveObjects(0)
                .totalCollections(0)
                .totalCollected(0)
                .build();
    }

    @Override
    public boolean release(GcObject object) {
        return false;
    }

    // --- Value encoding/decoding ---

    private static long encodeValue(GcValue value, GcFieldType fieldType) {
        switch (fieldType.kind()) {
            case I32:
            case PACKED_I8:
            case PACKED_I16:
                return value.asI32();
            case I64:
                return value.asI64();
            case F32:
                return Value.floatToLong(value.asF32());
            case F64:
                return Value.doubleToLong(value.asF64());
            default:
                return 0; // reference types
        }
    }

    private static GcValue decodeValue(long raw, GcFieldType fieldType) {
        switch (fieldType.kind()) {
            case I32:
            case PACKED_I8:
            case PACKED_I16:
                return GcValue.i32((int) raw);
            case I64:
                return GcValue.i64(raw);
            case F32:
                return GcValue.f32(Value.longToFloat(raw));
            case F64:
                return GcValue.f64(Value.longToDouble(raw));
            default:
                return GcValue.nullValue();
        }
    }

    // --- Inner adapter classes ---

    private static final class ChicoryGcStructInstance implements GcStructInstance {

        private final WasmStruct nativeStruct;
        private final GcStructType structType;

        ChicoryGcStructInstance(WasmStruct nativeStruct, GcStructType structType) {
            this.nativeStruct = nativeStruct;
            this.structType = structType;
        }

        @Override
        public GcStructType type() {
            return structType;
        }

        @Override
        public int fieldCount() {
            return nativeStruct.fieldCount();
        }

        @Override
        public GcValue getField(int index) {
            if (index < 0 || index >= fieldCount()) {
                throw new GcException("Field index out of bounds: " + index);
            }
            long raw = nativeStruct.field(index);
            GcFieldType fieldType = structType.field(index).type();
            return decodeValue(raw, fieldType);
        }

        @Override
        public void setField(int index, GcValue value) {
            if (index < 0 || index >= fieldCount()) {
                throw new GcException("Field index out of bounds: " + index);
            }
            GcFieldType fieldType = structType.field(index).type();
            long encoded = encodeValue(value, fieldType);
            nativeStruct.setField(index, encoded);
        }

        @Override
        public GcReferenceType referenceType() {
            return GcReferenceType.STRUCT_REF;
        }

        @Override
        public boolean isNull() {
            return false;
        }

        @Override
        public boolean refEquals(GcObject other) {
            if (!(other instanceof ChicoryGcStructInstance)) {
                return false;
            }
            return this.nativeStruct == ((ChicoryGcStructInstance) other).nativeStruct;
        }
    }

    private static final class ChicoryGcArrayInstance implements GcArrayInstance {

        private final WasmArray nativeArray;
        private final GcArrayType arrayType;

        ChicoryGcArrayInstance(WasmArray nativeArray, GcArrayType arrayType) {
            this.nativeArray = nativeArray;
            this.arrayType = arrayType;
        }

        @Override
        public GcArrayType type() {
            return arrayType;
        }

        @Override
        public int length() {
            return nativeArray.length();
        }

        @Override
        public GcValue getElement(int index) {
            if (index < 0 || index >= length()) {
                throw new GcException("Array index out of bounds: " + index);
            }
            long raw = nativeArray.get(index);
            return decodeValue(raw, arrayType.elementType());
        }

        @Override
        public void setElement(int index, GcValue value) {
            if (index < 0 || index >= length()) {
                throw new GcException("Array index out of bounds: " + index);
            }
            long encoded = encodeValue(value, arrayType.elementType());
            nativeArray.set(index, encoded);
        }

        @Override
        public GcReferenceType referenceType() {
            return GcReferenceType.ARRAY_REF;
        }

        @Override
        public boolean isNull() {
            return false;
        }

        @Override
        public boolean refEquals(GcObject other) {
            if (!(other instanceof ChicoryGcArrayInstance)) {
                return false;
            }
            return this.nativeArray == ((ChicoryGcArrayInstance) other).nativeArray;
        }
    }

    private static final class ChicoryGcI31Instance implements GcI31Instance {

        private final WasmI31Ref nativeRef;

        ChicoryGcI31Instance(WasmI31Ref nativeRef) {
            this.nativeRef = nativeRef;
        }

        @Override
        public int value() {
            int raw = nativeRef.value();
            // Sign-extend from 31 bits: if bit 30 is set, the value is negative
            if ((raw & 0x40000000) != 0) {
                return raw | 0x80000000;
            }
            return raw;
        }

        @Override
        public int unsignedValue() {
            return nativeRef.value() & 0x7FFFFFFF;
        }

        @Override
        public GcReferenceType referenceType() {
            return GcReferenceType.I31_REF;
        }

        @Override
        public boolean isNull() {
            return false;
        }

        @Override
        public boolean refEquals(GcObject other) {
            if (!(other instanceof ChicoryGcI31Instance)) {
                return false;
            }
            return this.nativeRef.value() == ((ChicoryGcI31Instance) other).nativeRef.value();
        }
    }
}
