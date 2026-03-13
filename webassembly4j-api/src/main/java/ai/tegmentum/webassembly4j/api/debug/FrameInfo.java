package ai.tegmentum.webassembly4j.api.debug;

import java.util.Optional;

/**
 * Information about a single frame in a WebAssembly call stack.
 *
 * <p>Provides function name, index, and offset information useful for
 * debugging and diagnostics.
 */
public final class FrameInfo {

    private final int funcIndex;
    private final String funcName;
    private final String moduleName;
    private final Integer moduleOffset;
    private final Integer funcOffset;

    private FrameInfo(Builder builder) {
        this.funcIndex = builder.funcIndex;
        this.funcName = builder.funcName;
        this.moduleName = builder.moduleName;
        this.moduleOffset = builder.moduleOffset;
        this.funcOffset = builder.funcOffset;
    }

    /**
     * Returns the function index within the module.
     */
    public int funcIndex() {
        return funcIndex;
    }

    /**
     * Returns the function name from the WebAssembly name section or debug info.
     */
    public Optional<String> funcName() {
        return Optional.ofNullable(funcName);
    }

    /**
     * Returns the module's debug name from the WebAssembly name section.
     * Particularly useful for component model frames where multiple modules
     * are embedded within a single component.
     */
    public Optional<String> moduleName() {
        return Optional.ofNullable(moduleName);
    }

    /**
     * Returns the byte offset within the module where this frame's instruction
     * is located.
     */
    public Optional<Integer> moduleOffset() {
        return Optional.ofNullable(moduleOffset);
    }

    /**
     * Returns the byte offset within the function where this frame's instruction
     * is located.
     */
    public Optional<Integer> funcOffset() {
        return Optional.ofNullable(funcOffset);
    }

    public static Builder builder(int funcIndex) {
        return new Builder(funcIndex);
    }

    public static final class Builder {
        private final int funcIndex;
        private String funcName;
        private String moduleName;
        private Integer moduleOffset;
        private Integer funcOffset;

        private Builder(int funcIndex) {
            this.funcIndex = funcIndex;
        }

        public Builder funcName(String funcName) { this.funcName = funcName; return this; }
        public Builder moduleName(String moduleName) { this.moduleName = moduleName; return this; }
        public Builder moduleOffset(int moduleOffset) { this.moduleOffset = moduleOffset; return this; }
        public Builder funcOffset(int funcOffset) { this.funcOffset = funcOffset; return this; }

        public FrameInfo build() {
            return new FrameInfo(this);
        }
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        if (moduleName != null) {
            sb.append(moduleName).append('!');
        }
        sb.append(funcName != null ? funcName : "<func " + funcIndex + ">");
        if (moduleOffset != null) {
            sb.append(" @").append(String.format("0x%x", moduleOffset));
        }
        return sb.toString();
    }
}
