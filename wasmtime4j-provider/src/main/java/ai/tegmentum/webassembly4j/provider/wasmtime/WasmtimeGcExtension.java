package ai.tegmentum.webassembly4j.provider.wasmtime;

import ai.tegmentum.wasmtime4j.WasmRuntime;
import ai.tegmentum.wasmtime4j.gc.ArrayInstance;
import ai.tegmentum.wasmtime4j.gc.ArrayType;
import ai.tegmentum.wasmtime4j.gc.FieldType;
import ai.tegmentum.wasmtime4j.gc.GcRuntime;
import ai.tegmentum.wasmtime4j.gc.I31Instance;
import ai.tegmentum.wasmtime4j.gc.StructInstance;
import ai.tegmentum.wasmtime4j.gc.StructType;
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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Wasmtime implementation of {@link GcExtension}.
 * Delegates to wasmtime4j's {@link GcRuntime} for all GC operations.
 */
final class WasmtimeGcExtension implements GcExtension {

    private final GcRuntime gcRuntime;

    WasmtimeGcExtension(WasmRuntime runtime) {
        try {
            this.gcRuntime = runtime.getGcRuntime();
        } catch (Exception e) {
            throw new GcException("Failed to create GC runtime", e);
        }
    }

    @Override
    public GcStructInstance createStruct(GcStructType type, GcValue... values) {
        return createStruct(type, Arrays.asList(values));
    }

    @Override
    public GcStructInstance createStruct(GcStructType type, List<GcValue> values) {
        try {
            StructType nativeType = convertStructType(type);
            List<ai.tegmentum.wasmtime4j.gc.GcValue> nativeValues = convertValues(values);
            StructInstance nativeInstance = gcRuntime.createStruct(nativeType, nativeValues);
            return new WasmtimeGcStructInstance(nativeInstance, type, gcRuntime);
        } catch (GcException e) {
            throw e;
        } catch (Exception e) {
            throw new GcException("Failed to create struct: " + e.getMessage(), e);
        }
    }

    @Override
    public GcArrayInstance createArray(GcArrayType type, GcValue... elements) {
        return createArray(type, Arrays.asList(elements));
    }

    @Override
    public GcArrayInstance createArray(GcArrayType type, List<GcValue> elements) {
        try {
            ArrayType nativeType = convertArrayType(type);
            List<ai.tegmentum.wasmtime4j.gc.GcValue> nativeElements = convertValues(elements);
            ArrayInstance nativeInstance = gcRuntime.createArray(nativeType, nativeElements);
            return new WasmtimeGcArrayInstance(nativeInstance, type, gcRuntime);
        } catch (GcException e) {
            throw e;
        } catch (Exception e) {
            throw new GcException("Failed to create array: " + e.getMessage(), e);
        }
    }

    @Override
    public GcArrayInstance createArray(GcArrayType type, int length) {
        try {
            ArrayType nativeType = convertArrayType(type);
            ArrayInstance nativeInstance = gcRuntime.createArray(nativeType, length);
            return new WasmtimeGcArrayInstance(nativeInstance, type, gcRuntime);
        } catch (GcException e) {
            throw e;
        } catch (Exception e) {
            throw new GcException("Failed to create array: " + e.getMessage(), e);
        }
    }

    @Override
    public GcI31Instance createI31(int value) {
        try {
            I31Instance nativeI31 = gcRuntime.createI31(value);
            return new WasmtimeGcI31Instance(nativeI31);
        } catch (Exception e) {
            throw new GcException("Failed to create i31: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean refTest(GcObject object, GcReferenceType refType) {
        try {
            ai.tegmentum.wasmtime4j.gc.GcObject nativeObj = unwrapGcObject(object);
            ai.tegmentum.wasmtime4j.gc.GcReferenceType nativeRefType = convertReferenceType(refType);
            return gcRuntime.refTest(nativeObj, nativeRefType);
        } catch (GcException e) {
            throw e;
        } catch (Exception e) {
            throw new GcException("ref.test failed: " + e.getMessage(), e);
        }
    }

    @Override
    public GcObject refCast(GcObject object, GcReferenceType refType) {
        try {
            ai.tegmentum.wasmtime4j.gc.GcObject nativeObj = unwrapGcObject(object);
            ai.tegmentum.wasmtime4j.gc.GcReferenceType nativeRefType = convertReferenceType(refType);
            ai.tegmentum.wasmtime4j.gc.GcObject castResult = gcRuntime.refCast(nativeObj, nativeRefType);
            return wrapGcObject(castResult);
        } catch (GcException e) {
            throw e;
        } catch (Exception e) {
            throw new GcException("ref.cast failed: " + e.getMessage(), e);
        }
    }

    @Override
    public GcStats collectGarbage() {
        try {
            ai.tegmentum.wasmtime4j.gc.GcStats nativeStats = gcRuntime.collectGarbage();
            return convertStats(nativeStats);
        } catch (Exception e) {
            return GcStats.builder().build();
        }
    }

    @Override
    public GcStats getStats() {
        try {
            ai.tegmentum.wasmtime4j.gc.GcStats nativeStats = gcRuntime.getGcStats();
            return convertStats(nativeStats);
        } catch (Exception e) {
            return GcStats.builder().build();
        }
    }

    // --- Type conversion helpers ---

    private StructType convertStructType(GcStructType type) {
        StructType.Builder builder = StructType.builder(type.name());
        for (GcStructType.Field field : type.fields()) {
            FieldType nativeFieldType = convertFieldType(field.type());
            builder.addField(field.name(), nativeFieldType, field.isMutable());
        }
        builder.withFinality(convertFinality(type.finality()));
        return builder.build();
    }

    private ArrayType convertArrayType(GcArrayType type) {
        ArrayType.Builder builder = ArrayType.builder(type.name())
                .elementType(convertFieldType(type.elementType()))
                .mutable(type.isMutable());
        builder.withFinality(convertFinality(type.finality()));
        return builder.build();
    }

    private FieldType convertFieldType(GcFieldType fieldType) {
        switch (fieldType.kind()) {
            case I32: return FieldType.i32();
            case I64: return FieldType.i64();
            case F32: return FieldType.f32();
            case F64: return FieldType.f64();
            case V128: return FieldType.v128();
            case PACKED_I8: return FieldType.packedI8();
            case PACKED_I16: return FieldType.packedI16();
            case REFERENCE:
                ai.tegmentum.wasmtime4j.gc.GcReferenceType nativeRefType =
                        convertReferenceType(fieldType.referenceType());
                return FieldType.reference(nativeRefType, fieldType.isNullable());
            default:
                throw new GcException("Unknown field type kind: " + fieldType.kind());
        }
    }

    private ai.tegmentum.wasmtime4j.gc.GcReferenceType convertReferenceType(GcReferenceType refType) {
        switch (refType) {
            case ANY_REF: return ai.tegmentum.wasmtime4j.gc.GcReferenceType.ANY_REF;
            case EQ_REF: return ai.tegmentum.wasmtime4j.gc.GcReferenceType.EQ_REF;
            case I31_REF: return ai.tegmentum.wasmtime4j.gc.GcReferenceType.I31_REF;
            case STRUCT_REF: return ai.tegmentum.wasmtime4j.gc.GcReferenceType.STRUCT_REF;
            case ARRAY_REF: return ai.tegmentum.wasmtime4j.gc.GcReferenceType.ARRAY_REF;
            default: throw new GcException("Unknown reference type: " + refType);
        }
    }

    private ai.tegmentum.wasmtime4j.gc.Finality convertFinality(
            ai.tegmentum.webassembly4j.api.gc.Finality finality) {
        return finality == ai.tegmentum.webassembly4j.api.gc.Finality.NON_FINAL
                ? ai.tegmentum.wasmtime4j.gc.Finality.NON_FINAL
                : ai.tegmentum.wasmtime4j.gc.Finality.FINAL;
    }

    // --- Value conversion helpers ---

    private List<ai.tegmentum.wasmtime4j.gc.GcValue> convertValues(List<GcValue> values) {
        List<ai.tegmentum.wasmtime4j.gc.GcValue> result = new ArrayList<>(values.size());
        for (GcValue v : values) {
            result.add(convertValue(v));
        }
        return result;
    }

    private ai.tegmentum.wasmtime4j.gc.GcValue convertValue(GcValue value) {
        switch (value.type()) {
            case I32: return ai.tegmentum.wasmtime4j.gc.GcValue.i32(value.asI32());
            case I64: return ai.tegmentum.wasmtime4j.gc.GcValue.i64(value.asI64());
            case F32: return ai.tegmentum.wasmtime4j.gc.GcValue.f32(value.asF32());
            case F64: return ai.tegmentum.wasmtime4j.gc.GcValue.f64(value.asF64());
            case NULL: return ai.tegmentum.wasmtime4j.gc.GcValue.nullValue();
            case REFERENCE:
                ai.tegmentum.wasmtime4j.gc.GcObject nativeObj = unwrapGcObject(value.asReference());
                return ai.tegmentum.wasmtime4j.gc.GcValue.reference(nativeObj);
            default:
                throw new GcException("Unknown value type: " + value.type());
        }
    }

    GcValue convertFromNativeValue(ai.tegmentum.wasmtime4j.gc.GcValue nativeValue) {
        switch (nativeValue.getType()) {
            case I32: return GcValue.i32(nativeValue.asI32());
            case I64: return GcValue.i64(nativeValue.asI64());
            case F32: return GcValue.f32(nativeValue.asF32());
            case F64: return GcValue.f64(nativeValue.asF64());
            case NULL: return GcValue.nullValue();
            case REFERENCE:
                ai.tegmentum.wasmtime4j.gc.GcObject ref = nativeValue.asReference();
                return GcValue.reference(wrapGcObject(ref));
            default:
                throw new GcException("Unknown native value type: " + nativeValue.getType());
        }
    }

    // --- Object wrapping/unwrapping ---

    private ai.tegmentum.wasmtime4j.gc.GcObject unwrapGcObject(GcObject object) {
        if (object instanceof WasmtimeGcStructInstance) {
            return ((WasmtimeGcStructInstance) object).nativeInstance();
        }
        if (object instanceof WasmtimeGcArrayInstance) {
            return ((WasmtimeGcArrayInstance) object).nativeInstance();
        }
        if (object instanceof WasmtimeGcI31Instance) {
            return ((WasmtimeGcI31Instance) object).nativeInstance();
        }
        throw new GcException("Cannot unwrap GC object of type: " + object.getClass().getName());
    }

    private GcObject wrapGcObject(ai.tegmentum.wasmtime4j.gc.GcObject nativeObj) {
        if (nativeObj instanceof StructInstance) {
            return new WasmtimeGcStructInstance((StructInstance) nativeObj, null, gcRuntime);
        }
        if (nativeObj instanceof ArrayInstance) {
            return new WasmtimeGcArrayInstance((ArrayInstance) nativeObj, null, gcRuntime);
        }
        if (nativeObj instanceof I31Instance) {
            return new WasmtimeGcI31Instance((I31Instance) nativeObj);
        }
        throw new GcException("Unknown native GC object type: " + nativeObj.getClass().getName());
    }

    private GcStats convertStats(ai.tegmentum.wasmtime4j.gc.GcStats nativeStats) {
        return GcStats.builder()
                .totalAllocated(nativeStats.getTotalAllocated())
                .totalCollected(nativeStats.getTotalCollected())
                .liveObjects(nativeStats.getLiveObjects())
                .totalCollections(nativeStats.getTotalCollections())
                .heapSizeBytes(nativeStats.getCurrentHeapSize())
                .build();
    }

    // --- Inner adapter classes ---

    /**
     * Wraps a wasmtime4j StructInstance as a GcStructInstance.
     */
    static final class WasmtimeGcStructInstance implements GcStructInstance {
        private final StructInstance nativeInstance;
        private final GcStructType apiType;
        private final GcRuntime gcRuntime;

        WasmtimeGcStructInstance(StructInstance nativeInstance, GcStructType apiType, GcRuntime gcRuntime) {
            this.nativeInstance = nativeInstance;
            this.apiType = apiType;
            this.gcRuntime = gcRuntime;
        }

        StructInstance nativeInstance() { return nativeInstance; }

        @Override
        public GcStructType type() { return apiType; }

        @Override
        public int fieldCount() { return nativeInstance.getFieldCount(); }

        @Override
        public GcValue getField(int index) {
            try {
                ai.tegmentum.wasmtime4j.gc.GcValue nativeValue = gcRuntime.getStructField(nativeInstance, index);
                return convertFromNative(nativeValue);
            } catch (Exception e) {
                throw new GcException("Failed to get struct field " + index + ": " + e.getMessage(), e);
            }
        }

        @Override
        public void setField(int index, GcValue value) {
            try {
                gcRuntime.setStructField(nativeInstance, index, convertToNative(value));
            } catch (Exception e) {
                throw new GcException("Failed to set struct field " + index + ": " + e.getMessage(), e);
            }
        }

        @Override
        public boolean isNull() { return false; }

        @Override
        public boolean refEquals(GcObject other) {
            if (!(other instanceof WasmtimeGcStructInstance)) return false;
            return gcRuntime.refEquals(nativeInstance, ((WasmtimeGcStructInstance) other).nativeInstance);
        }
    }

    /**
     * Wraps a wasmtime4j ArrayInstance as a GcArrayInstance.
     */
    static final class WasmtimeGcArrayInstance implements GcArrayInstance {
        private final ArrayInstance nativeInstance;
        private final GcArrayType apiType;
        private final GcRuntime gcRuntime;

        WasmtimeGcArrayInstance(ArrayInstance nativeInstance, GcArrayType apiType, GcRuntime gcRuntime) {
            this.nativeInstance = nativeInstance;
            this.apiType = apiType;
            this.gcRuntime = gcRuntime;
        }

        ArrayInstance nativeInstance() { return nativeInstance; }

        @Override
        public GcArrayType type() { return apiType; }

        @Override
        public int length() { return nativeInstance.getLength(); }

        @Override
        public GcValue getElement(int index) {
            try {
                ai.tegmentum.wasmtime4j.gc.GcValue nativeValue = gcRuntime.getArrayElement(nativeInstance, index);
                return convertFromNative(nativeValue);
            } catch (Exception e) {
                throw new GcException("Failed to get array element " + index + ": " + e.getMessage(), e);
            }
        }

        @Override
        public void setElement(int index, GcValue value) {
            try {
                gcRuntime.setArrayElement(nativeInstance, index, convertToNative(value));
            } catch (Exception e) {
                throw new GcException("Failed to set array element " + index + ": " + e.getMessage(), e);
            }
        }

        @Override
        public boolean isNull() { return false; }

        @Override
        public boolean refEquals(GcObject other) {
            if (!(other instanceof WasmtimeGcArrayInstance)) return false;
            return gcRuntime.refEquals(nativeInstance, ((WasmtimeGcArrayInstance) other).nativeInstance);
        }
    }

    /**
     * Wraps a wasmtime4j I31Instance as a GcI31Instance.
     */
    static final class WasmtimeGcI31Instance implements GcI31Instance {
        private final I31Instance nativeInstance;

        WasmtimeGcI31Instance(I31Instance nativeInstance) {
            this.nativeInstance = nativeInstance;
        }

        I31Instance nativeInstance() { return nativeInstance; }

        @Override
        public int value() { return nativeInstance.getSignedValue(); }

        @Override
        public int unsignedValue() { return nativeInstance.getUnsignedValue(); }

        @Override
        public boolean refEquals(GcObject other) {
            if (!(other instanceof WasmtimeGcI31Instance)) return false;
            return nativeInstance.getValue() == ((WasmtimeGcI31Instance) other).nativeInstance.getValue();
        }
    }

    // --- Static value conversion used by inner classes ---

    private static GcValue convertFromNative(ai.tegmentum.wasmtime4j.gc.GcValue nativeValue) {
        switch (nativeValue.getType()) {
            case I32: return GcValue.i32(nativeValue.asI32());
            case I64: return GcValue.i64(nativeValue.asI64());
            case F32: return GcValue.f32(nativeValue.asF32());
            case F64: return GcValue.f64(nativeValue.asF64());
            case NULL: return GcValue.nullValue();
            default:
                throw new GcException("Unsupported native value type: " + nativeValue.getType());
        }
    }

    private static ai.tegmentum.wasmtime4j.gc.GcValue convertToNative(GcValue value) {
        switch (value.type()) {
            case I32: return ai.tegmentum.wasmtime4j.gc.GcValue.i32(value.asI32());
            case I64: return ai.tegmentum.wasmtime4j.gc.GcValue.i64(value.asI64());
            case F32: return ai.tegmentum.wasmtime4j.gc.GcValue.f32(value.asF32());
            case F64: return ai.tegmentum.wasmtime4j.gc.GcValue.f64(value.asF64());
            case NULL: return ai.tegmentum.wasmtime4j.gc.GcValue.nullValue();
            default:
                throw new GcException("Unsupported value type: " + value.type());
        }
    }
}
