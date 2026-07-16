package ai.tegmentum.webassembly4j.api;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class DefaultLinkingContext implements LinkingContext {

    private final WasiContext wasiContext;
    private final WasiNnConfig wasiNnConfig;
    private final Map<String, Object> imports;
    private final List<HostFunctionDefinition> hostFunctions;
    private final List<WitHostFunctionDefinition> witHostFunctions;

    private DefaultLinkingContext(WasiContext wasiContext, WasiNnConfig wasiNnConfig,
                                  Map<String, Object> imports,
                                  List<HostFunctionDefinition> hostFunctions,
                                  List<WitHostFunctionDefinition> witHostFunctions) {
        this.wasiContext = wasiContext;
        this.wasiNnConfig = wasiNnConfig;
        this.imports = Collections.unmodifiableMap(new LinkedHashMap<>(imports));
        this.hostFunctions = Collections.unmodifiableList(new ArrayList<>(hostFunctions));
        this.witHostFunctions = Collections.unmodifiableList(new ArrayList<>(witHostFunctions));
    }

    @Override
    public WasiContext wasiContext() {
        return wasiContext;
    }

    @Override
    public WasiNnConfig wasiNnConfig() {
        return wasiNnConfig;
    }

    @Override
    public List<HostFunctionDefinition> hostFunctions() {
        return hostFunctions;
    }

    @Override
    public List<WitHostFunctionDefinition> witHostFunctions() {
        return witHostFunctions;
    }

    public Map<String, Object> imports() {
        return imports;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {

        private WasiContext wasiContext;
        private WasiNnConfig wasiNnConfig;
        private final Map<String, Object> imports = new LinkedHashMap<>();
        private final List<HostFunctionDefinition> hostFunctions = new ArrayList<>();
        private final List<WitHostFunctionDefinition> witHostFunctions = new ArrayList<>();

        private Builder() {
        }

        public Builder wasiContext(WasiContext wasiContext) {
            this.wasiContext = wasiContext;
            return this;
        }

        /**
         * Enable WASI-NN on the resulting linking context with the given
         * configuration. Providers that support wasi:nn (currently
         * wasmtime4j-provider) translate this to their native enablement call
         * before component instantiation. Passing {@link WasiNnConfig#defaults()}
         * requests the provider's default backend set (ORT/ONNX under the
         * current wasmtime4j pin). Passing {@code null} clears the request.
         *
         * @param config the WASI-NN configuration; {@code null} disables
         */
        public Builder enableWasiNn(WasiNnConfig config) {
            this.wasiNnConfig = config;
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

        /**
         * Register a WIT-typed host function under {@code witPath} — the standard WIT
         * import path form ({@code "package:name/interface#function"} or {@code
         * "#function"} for a root import). See {@link WitHostFunctionDefinition}.
         */
        public Builder addWitHostFunction(String witPath, WitHostFunction function) {
            this.witHostFunctions.add(new WitHostFunctionDefinition(witPath, function));
            return this;
        }

        public Builder addWitHostFunction(WitHostFunctionDefinition definition) {
            this.witHostFunctions.add(definition);
            return this;
        }

        public DefaultLinkingContext build() {
            return new DefaultLinkingContext(
                    wasiContext, wasiNnConfig, imports, hostFunctions, witHostFunctions);
        }
    }
}
