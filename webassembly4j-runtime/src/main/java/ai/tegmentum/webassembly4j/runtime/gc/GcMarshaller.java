package ai.tegmentum.webassembly4j.runtime.gc;

import ai.tegmentum.webassembly4j.api.gc.GcExtension;
import ai.tegmentum.webassembly4j.api.gc.GcStructInstance;
import ai.tegmentum.webassembly4j.api.gc.GcStructType;
import ai.tegmentum.webassembly4j.api.gc.GcValue;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Objects;

/**
 * Marshals Java objects to and from WebAssembly GC struct instances.
 *
 * <p>Works with classes annotated with {@link GcMapped}. The marshaller
 * reflects the class fields, maps them to GC struct fields, and handles
 * bidirectional conversion.
 *
 * <p>Example:
 * <pre>{@code
 * GcExtension gc = instance.extension(GcExtension.class).orElseThrow();
 * GcMarshaller marshaller = GcMarshaller.forExtension(gc);
 *
 * // Java → GC struct
 * Point p = new Point(1.0, 2.0);
 * GcStructInstance struct = marshaller.marshal(p);
 *
 * // GC struct → Java
 * Point result = marshaller.unmarshal(struct, Point.class);
 * }</pre>
 */
public final class GcMarshaller {

    private final GcExtension gc;
    private final GcTypeMapper typeMapper;

    private GcMarshaller(GcExtension gc, GcTypeMapper typeMapper) {
        this.gc = Objects.requireNonNull(gc, "gc");
        this.typeMapper = Objects.requireNonNull(typeMapper, "typeMapper");
    }

    /**
     * Creates a marshaller using the given GC extension and a new type mapper.
     */
    public static GcMarshaller forExtension(GcExtension gc) {
        return new GcMarshaller(gc, new GcTypeMapper());
    }

    /**
     * Creates a marshaller using the given GC extension and type mapper.
     */
    public static GcMarshaller forExtension(GcExtension gc, GcTypeMapper typeMapper) {
        return new GcMarshaller(gc, typeMapper);
    }

    /**
     * Returns the type mapper used by this marshaller.
     */
    public GcTypeMapper typeMapper() {
        return typeMapper;
    }

    /**
     * Marshals a Java object to a GC struct instance.
     *
     * @param object an instance of a {@link GcMapped}-annotated class
     * @return a new GC struct instance with the object's field values
     * @throws IllegalArgumentException if the object's class is not GC-mappable
     */
    public GcStructInstance marshal(Object object) {
        Objects.requireNonNull(object, "object");
        Class<?> type = object.getClass();
        GcStructType structType = typeMapper.toStructType(type);
        List<GcTypeMapper.FieldAccessor> accessors = typeMapper.fieldAccessors(type);

        GcValue[] values = new GcValue[accessors.size()];
        for (int i = 0; i < accessors.size(); i++) {
            values[i] = toGcValue(accessors.get(i), accessors.get(i).get(object));
        }
        return gc.createStruct(structType, values);
    }

    /**
     * Unmarshals a GC struct instance to a Java object.
     *
     * @param struct the GC struct instance
     * @param type   the target Java class (must be {@link GcMapped}-annotated)
     * @param <T>    the target type
     * @return a new Java object with fields populated from the struct
     * @throws IllegalArgumentException if the class is not GC-mappable
     */
    public <T> T unmarshal(GcStructInstance struct, Class<T> type) {
        Objects.requireNonNull(struct, "struct");
        Objects.requireNonNull(type, "type");
        List<GcTypeMapper.FieldAccessor> accessors = typeMapper.fieldAccessors(type);

        if (isRecord(type)) {
            return unmarshalRecord(struct, type, accessors);
        }
        return unmarshalClass(struct, type, accessors);
    }

    private GcValue toGcValue(GcTypeMapper.FieldAccessor accessor, Object value) {
        Class<?> javaType = accessor.javaType();

        if (value == null) {
            return GcValue.nullValue();
        }
        if (javaType == int.class || javaType == Integer.class) {
            return GcValue.i32((Integer) value);
        } else if (javaType == long.class || javaType == Long.class) {
            return GcValue.i64((Long) value);
        } else if (javaType == float.class || javaType == Float.class) {
            return GcValue.f32((Float) value);
        } else if (javaType == double.class || javaType == Double.class) {
            return GcValue.f64((Double) value);
        } else if (javaType == boolean.class || javaType == Boolean.class) {
            return GcValue.i32(((Boolean) value) ? 1 : 0);
        } else if (GcTypeMapper.isGcMapped(javaType)) {
            GcStructInstance nested = marshal(value);
            return GcValue.reference(nested);
        }
        throw new IllegalArgumentException("Cannot convert field " + accessor.name()
                + " of type " + javaType.getName());
    }

    private Object fromGcValue(GcValue value, Class<?> javaType) {
        if (value.isNull()) {
            return null;
        }
        if (javaType == int.class || javaType == Integer.class) {
            return value.asI32();
        } else if (javaType == long.class || javaType == Long.class) {
            return value.asI64();
        } else if (javaType == float.class || javaType == Float.class) {
            return value.asF32();
        } else if (javaType == double.class || javaType == Double.class) {
            return value.asF64();
        } else if (javaType == boolean.class || javaType == Boolean.class) {
            return value.asI32() != 0;
        } else if (GcTypeMapper.isGcMapped(javaType)) {
            GcStructInstance nested = (GcStructInstance) value.asReference();
            return unmarshal(nested, javaType);
        }
        throw new IllegalArgumentException("Cannot convert GC value to " + javaType.getName());
    }

    @SuppressWarnings("unchecked")
    private <T> T unmarshalRecord(GcStructInstance struct, Class<T> type,
                                   List<GcTypeMapper.FieldAccessor> accessors) {
        // Records: find canonical constructor matching field types in order
        Class<?>[] paramTypes = new Class<?>[accessors.size()];
        Object[] args = new Object[accessors.size()];
        for (int i = 0; i < accessors.size(); i++) {
            paramTypes[i] = accessors.get(i).javaType();
            args[i] = fromGcValue(struct.getField(i), paramTypes[i]);
        }
        try {
            Constructor<T> ctor = type.getDeclaredConstructor(paramTypes);
            ctor.setAccessible(true);
            return ctor.newInstance(args);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Failed to construct record " + type.getName(), e);
        }
    }

    @SuppressWarnings("unchecked")
    private <T> T unmarshalClass(GcStructInstance struct, Class<T> type,
                                  List<GcTypeMapper.FieldAccessor> accessors) {
        try {
            Constructor<T> ctor = type.getDeclaredConstructor();
            ctor.setAccessible(true);
            T instance = ctor.newInstance();
            for (int i = 0; i < accessors.size(); i++) {
                GcTypeMapper.FieldAccessor accessor = accessors.get(i);
                Object value = fromGcValue(struct.getField(i), accessor.javaType());
                Field field = accessor.field();
                field.setAccessible(true);
                field.set(instance, value);
            }
            return instance;
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Failed to construct " + type.getName(), e);
        }
    }

    private static boolean isRecord(Class<?> type) {
        Class<?> superclass = type.getSuperclass();
        return superclass != null && "java.lang.Record".equals(superclass.getName());
    }
}
