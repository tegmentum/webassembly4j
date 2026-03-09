package ai.tegmentum.webassembly4j.api;

import java.util.Objects;

/**
 * Describes an export from a WebAssembly module.
 */
public final class ExportDescriptor {

    private final String name;
    private final ExternType type;
    private final ValueType[] parameterTypes;
    private final ValueType[] resultTypes;

    private ExportDescriptor(String name, ExternType type,
                             ValueType[] parameterTypes, ValueType[] resultTypes) {
        this.name = Objects.requireNonNull(name, "name");
        this.type = Objects.requireNonNull(type, "type");
        this.parameterTypes = parameterTypes != null ? parameterTypes.clone() : new ValueType[0];
        this.resultTypes = resultTypes != null ? resultTypes.clone() : new ValueType[0];
    }

    /**
     * Creates a function export descriptor.
     */
    public static ExportDescriptor function(String name, ValueType[] parameterTypes,
                                            ValueType[] resultTypes) {
        return new ExportDescriptor(name, ExternType.FUNCTION, parameterTypes, resultTypes);
    }

    /**
     * Creates a memory export descriptor.
     */
    public static ExportDescriptor memory(String name) {
        return new ExportDescriptor(name, ExternType.MEMORY, null, null);
    }

    /**
     * Creates a table export descriptor.
     */
    public static ExportDescriptor table(String name) {
        return new ExportDescriptor(name, ExternType.TABLE, null, null);
    }

    /**
     * Creates a global export descriptor.
     */
    public static ExportDescriptor global(String name, ValueType valueType) {
        return new ExportDescriptor(name, ExternType.GLOBAL,
                new ValueType[]{valueType}, null);
    }

    public String name() {
        return name;
    }

    public ExternType type() {
        return type;
    }

    /**
     * Returns the parameter types for function exports, or the value type for globals.
     * Empty array for other export types.
     */
    public ValueType[] parameterTypes() {
        return parameterTypes.clone();
    }

    /**
     * Returns the result types for function exports.
     * Empty array for non-function exports.
     */
    public ValueType[] resultTypes() {
        return resultTypes.clone();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof ExportDescriptor)) return false;
        ExportDescriptor that = (ExportDescriptor) obj;
        return name.equals(that.name) && type == that.type;
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, type);
    }

    @Override
    public String toString() {
        return "ExportDescriptor{name='" + name + "', type=" + type + '}';
    }
}
