package ai.tegmentum.webassembly4j.api;

import java.util.Objects;

/**
 * Describes an import required by a WebAssembly module.
 */
public final class ImportDescriptor {

    private final String moduleName;
    private final String name;
    private final ExternType type;
    private final ValueType[] parameterTypes;
    private final ValueType[] resultTypes;

    private ImportDescriptor(String moduleName, String name, ExternType type,
                             ValueType[] parameterTypes, ValueType[] resultTypes) {
        this.moduleName = Objects.requireNonNull(moduleName, "moduleName");
        this.name = Objects.requireNonNull(name, "name");
        this.type = Objects.requireNonNull(type, "type");
        this.parameterTypes = parameterTypes != null ? parameterTypes.clone() : new ValueType[0];
        this.resultTypes = resultTypes != null ? resultTypes.clone() : new ValueType[0];
    }

    /**
     * Creates a function import descriptor.
     */
    public static ImportDescriptor function(String moduleName, String name,
                                            ValueType[] parameterTypes,
                                            ValueType[] resultTypes) {
        return new ImportDescriptor(moduleName, name, ExternType.FUNCTION,
                parameterTypes, resultTypes);
    }

    /**
     * Creates a memory import descriptor.
     */
    public static ImportDescriptor memory(String moduleName, String name) {
        return new ImportDescriptor(moduleName, name, ExternType.MEMORY, null, null);
    }

    /**
     * Creates a table import descriptor.
     */
    public static ImportDescriptor table(String moduleName, String name) {
        return new ImportDescriptor(moduleName, name, ExternType.TABLE, null, null);
    }

    /**
     * Creates a global import descriptor.
     */
    public static ImportDescriptor global(String moduleName, String name, ValueType valueType) {
        return new ImportDescriptor(moduleName, name, ExternType.GLOBAL,
                new ValueType[]{valueType}, null);
    }

    public String moduleName() {
        return moduleName;
    }

    public String name() {
        return name;
    }

    public ExternType type() {
        return type;
    }

    /**
     * Returns the parameter types for function imports, or the value type for globals.
     * Empty array for other import types.
     */
    public ValueType[] parameterTypes() {
        return parameterTypes.clone();
    }

    /**
     * Returns the result types for function imports.
     * Empty array for non-function imports.
     */
    public ValueType[] resultTypes() {
        return resultTypes.clone();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof ImportDescriptor)) return false;
        ImportDescriptor that = (ImportDescriptor) obj;
        return moduleName.equals(that.moduleName) && name.equals(that.name) && type == that.type;
    }

    @Override
    public int hashCode() {
        return Objects.hash(moduleName, name, type);
    }

    @Override
    public String toString() {
        return "ImportDescriptor{module='" + moduleName + "', name='" + name
                + "', type=" + type + '}';
    }
}
