package ai.tegmentum.webassembly4j.api;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class DefaultLinkingContext implements LinkingContext {

    private final WasiContext wasiContext;
    private final Map<String, Object> imports;
    private final List<HostFunctionDefinition> hostFunctions;

    private DefaultLinkingContext(WasiContext wasiContext, Map<String, Object> imports,
                                  List<HostFunctionDefinition> hostFunctions) {
        this.wasiContext = wasiContext;
        this.imports = Collections.unmodifiableMap(new LinkedHashMap<>(imports));
        this.hostFunctions = Collections.unmodifiableList(new ArrayList<>(hostFunctions));
    }

    @Override
    public WasiContext wasiContext() {
        return wasiContext;
    }

    @Override
    public List<HostFunctionDefinition> hostFunctions() {
        return hostFunctions;
    }

    public Map<String, Object> imports() {
        return imports;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {

        private WasiContext wasiContext;
        private final Map<String, Object> imports = new LinkedHashMap<>();
        private final List<HostFunctionDefinition> hostFunctions = new ArrayList<>();

        private Builder() {
        }

        public Builder wasiContext(WasiContext wasiContext) {
            this.wasiContext = wasiContext;
            return this;
        }

        public Builder addImport(String name, Object value) {
            this.imports.put(name, value);
            return this;
        }

        public Builder addHostFunction(String moduleName, String functionName,
                                       ValueType[] parameterTypes, ValueType[] resultTypes,
                                       HostFunction function) {
            this.hostFunctions.add(new HostFunctionDefinition(
                    moduleName, functionName, parameterTypes, resultTypes, function));
            return this;
        }

        public Builder addHostFunction(HostFunctionDefinition definition) {
            this.hostFunctions.add(definition);
            return this;
        }

        public DefaultLinkingContext build() {
            return new DefaultLinkingContext(wasiContext, imports, hostFunctions);
        }
    }
}
