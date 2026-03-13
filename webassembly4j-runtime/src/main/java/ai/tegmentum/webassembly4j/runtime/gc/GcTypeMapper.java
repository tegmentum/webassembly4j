package ai.tegmentum.webassembly4j.runtime.gc;

import ai.tegmentum.webassembly4j.api.gc.GcFieldType;
import ai.tegmentum.webassembly4j.api.gc.GcReferenceType;
import ai.tegmentum.webassembly4j.api.gc.GcStructType;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Maps Java classes annotated with {@link GcMapped} to WebAssembly GC struct types.
 *
 * <p>Caches type mappings for reuse. Thread-safe.
 */
public final class GcTypeMapper {

    private final Map<Class<?>, GcStructType> cache = new ConcurrentHashMap<>();
    private final Map<Class<?>, List<FieldAccessor>> accessorCache = new ConcurrentHashMap<>();

    /**
     * Returns the GC struct type for the given Java class.
     *
     * @param type a class annotated with {@link GcMapped}
     * @return the corresponding GC struct type
     * @throws IllegalArgumentException if the class is not annotated or has unsupported fields
     */
    public GcStructType toStructType(Class<?> type) {
        return cache.computeIfAbsent(type, this::buildStructType);
    }

    /**
     * Returns the ordered field accessors for the given class.
     */
    List<FieldAccessor> fieldAccessors(Class<?> type) {
        return accessorCache.computeIfAbsent(type, this::buildFieldAccessors);
    }

    private GcStructType buildStructType(Class<?> type) {
        requireGcMapped(type);
        String name = structName(type);
        List<FieldAccessor> accessors = fieldAccessors(type);

        GcStructType.Builder builder = GcStructType.builder(name);
        for (FieldAccessor accessor : accessors) {
            builder.addField(accessor.name(), accessor.gcFieldType(), true);
        }
        return builder.build();
    }

    private List<FieldAccessor> buildFieldAccessors(Class<?> type) {
        requireGcMapped(type);
        List<FieldAccessor> accessors = new ArrayList<>();

        if (isRecord(type)) {
            // For records, use record components in declaration order
            try {
                Object[] components = (Object[]) type.getMethod("getRecordComponents").invoke(null);
                // getRecordComponents is on Class, not a static method
            } catch (Exception e) {
                // Fall through to field-based access
            }
            // Records: fields are declared in component order, all final
            for (Field field : type.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers())) {
                    continue;
                }
                field.setAccessible(true);
                GcFieldType gcType = javaTypeToGcFieldType(field.getType());
                accessors.add(new FieldAccessor(field.getName(), field, gcType, field.getType()));
            }
        } else {
            for (Field field : type.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers()) || Modifier.isTransient(field.getModifiers())) {
                    continue;
                }
                field.setAccessible(true);
                GcFieldType gcType = javaTypeToGcFieldType(field.getType());
                accessors.add(new FieldAccessor(field.getName(), field, gcType, field.getType()));
            }
        }

        if (accessors.isEmpty()) {
            throw new IllegalArgumentException(
                    "No mappable fields found on " + type.getName());
        }
        return accessors;
    }

    private GcFieldType javaTypeToGcFieldType(Class<?> type) {
        if (type == int.class || type == Integer.class) {
            return GcFieldType.i32();
        } else if (type == long.class || type == Long.class) {
            return GcFieldType.i64();
        } else if (type == float.class || type == Float.class) {
            return GcFieldType.f32();
        } else if (type == double.class || type == Double.class) {
            return GcFieldType.f64();
        } else if (type == boolean.class || type == Boolean.class) {
            return GcFieldType.i32();
        } else if (type.isAnnotationPresent(GcMapped.class)) {
            return GcFieldType.reference(GcReferenceType.STRUCT_REF, true);
        }
        throw new IllegalArgumentException(
                "Unsupported field type: " + type.getName()
                        + ". Supported: int, long, float, double, boolean, or @GcMapped types");
    }

    static String structName(Class<?> type) {
        GcMapped annotation = type.getAnnotation(GcMapped.class);
        if (annotation != null && !annotation.value().isEmpty()) {
            return annotation.value();
        }
        return type.getSimpleName();
    }

    static void requireGcMapped(Class<?> type) {
        if (!type.isAnnotationPresent(GcMapped.class)) {
            throw new IllegalArgumentException(
                    type.getName() + " is not annotated with @GcMapped");
        }
    }

    static boolean isGcMapped(Class<?> type) {
        return type.isAnnotationPresent(GcMapped.class);
    }

    private static boolean isRecord(Class<?> type) {
        // Java 8 compatible check — records have java.lang.Record as superclass
        Class<?> superclass = type.getSuperclass();
        return superclass != null && "java.lang.Record".equals(superclass.getName());
    }

    /**
     * Metadata for a single field in a GC-mapped class.
     */
    static final class FieldAccessor {
        private final String name;
        private final Field field;
        private final GcFieldType gcFieldType;
        private final Class<?> javaType;

        FieldAccessor(String name, Field field, GcFieldType gcFieldType, Class<?> javaType) {
            this.name = name;
            this.field = field;
            this.gcFieldType = gcFieldType;
            this.javaType = javaType;
        }

        String name() { return name; }
        Field field() { return field; }
        GcFieldType gcFieldType() { return gcFieldType; }
        Class<?> javaType() { return javaType; }

        Object get(Object instance) {
            try {
                return field.get(instance);
            } catch (IllegalAccessException e) {
                throw new RuntimeException("Failed to read field " + name, e);
            }
        }
    }
}
