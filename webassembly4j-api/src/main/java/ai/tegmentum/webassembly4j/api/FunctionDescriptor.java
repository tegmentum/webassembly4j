package ai.tegmentum.webassembly4j.api;

import java.util.Arrays;
import java.util.Objects;

/**
 * Describes a function defined in a WebAssembly module, including its name,
 * index, parameter and result types, and whether it is imported, exported,
 * or internal.
 */
public final class FunctionDescriptor {

    private final int index;
    private final String name;
    private final ValueType[] parameterTypes;
    private final ValueType[] resultTypes;
    private final boolean imported;
    private final boolean exported;
    private final String moduleName;

    private FunctionDescriptor(int index, String name, ValueType[] parameterTypes,
                               ValueType[] resultTypes, boolean imported,
                               boolean exported, String moduleName) {
        if (index < 0) {
            throw new IllegalArgumentException("index must be non-negative");
        }
        this.index = index;
        this.name = name;
        this.parameterTypes = parameterTypes != null ? parameterTypes.clone() : new ValueType[0];
        this.resultTypes = resultTypes != null ? resultTypes.clone() : new ValueType[0];
        this.imported = imported;
        this.exported = exported;
        this.moduleName = moduleName;
    }

    /**
     * Creates a descriptor for an exported function.
     */
    public static FunctionDescriptor exported(String name, int index,
                                              ValueType[] params, ValueType[] results) {
        Objects.requireNonNull(name, "name");
        return new FunctionDescriptor(index, name, params, results, false, true, null);
    }

    /**
     * Creates a descriptor for an imported function.
     */
    public static FunctionDescriptor imported(String moduleName, String name, int index,
                                              ValueType[] params, ValueType[] results) {
        Objects.requireNonNull(moduleName, "moduleName");
        Objects.requireNonNull(name, "name");
        return new FunctionDescriptor(index, name, params, results, true, false, moduleName);
    }

    /**
     * Creates a descriptor for an internal (neither imported nor exported) function.
     */
    public static FunctionDescriptor internal(String name, int index,
                                              ValueType[] params, ValueType[] results) {
        return new FunctionDescriptor(index, name, params, results, false, false, null);
    }

    /**
     * Returns the function index within the module.
     */
    public int index() {
        return index;
    }

    /**
     * Returns the function name, or null for unnamed functions.
     */
    public String name() {
        return name;
    }

    /**
     * Returns a defensive copy of the parameter types.
     */
    public ValueType[] parameterTypes() {
        return parameterTypes.clone();
    }

    /**
     * Returns a defensive copy of the result types.
     */
    public ValueType[] resultTypes() {
        return resultTypes.clone();
    }

    /**
     * Returns true if this function is exported from the module.
     */
    public boolean isExported() {
        return exported;
    }

    /**
     * Returns true if this function is imported into the module.
     */
    public boolean isImported() {
        return imported;
    }

    /**
     * Returns true if this function is internal (neither imported nor exported).
     */
    public boolean isInternal() {
        return !imported && !exported;
    }

    /**
     * Returns the module name for imported functions, or null for non-imports.
     */
    public String moduleName() {
        return moduleName;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof FunctionDescriptor)) return false;
        FunctionDescriptor that = (FunctionDescriptor) obj;
        return index == that.index
                && imported == that.imported
                && exported == that.exported
                && Objects.equals(name, that.name)
                && Arrays.equals(parameterTypes, that.parameterTypes)
                && Arrays.equals(resultTypes, that.resultTypes)
                && Objects.equals(moduleName, that.moduleName);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(index, name, imported, exported, moduleName);
        result = 31 * result + Arrays.hashCode(parameterTypes);
        result = 31 * result + Arrays.hashCode(resultTypes);
        return result;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("FunctionDescriptor{");
        sb.append("index=").append(index);
        if (name != null) {
            sb.append(", name='").append(name).append('\'');
        }
        if (imported) {
            sb.append(", imported from '").append(moduleName).append('\'');
        } else if (exported) {
            sb.append(", exported");
        } else {
            sb.append(", internal");
        }
        sb.append('}');
        return sb.toString();
    }
}
