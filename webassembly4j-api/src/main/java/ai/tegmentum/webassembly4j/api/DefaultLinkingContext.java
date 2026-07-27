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
    private final List<ExternImportDefinition> externImports;
    private final List<CallerAwareHostFunctionDefinition> callerAwareHostFunctions;

    private DefaultLinkingContext(WasiContext wasiContext, WasiNnConfig wasiNnConfig,
                                  Map<String, Object> imports,
                                  List<HostFunctionDefinition> hostFunctions,
                                  List<WitHostFunctionDefinition> witHostFunctions,
                                  List<ExternImportDefinition> externImports,
                                  List<CallerAwareHostFunctionDefinition> callerAwareHostFunctions) {
        this.wasiContext = wasiContext;
        this.wasiNnConfig = wasiNnConfig;
        this.imports = Collections.unmodifiableMap(new LinkedHashMap<>(imports));
        this.hostFunctions = Collections.unmodifiableList(new ArrayList<>(hostFunctions));
        this.witHostFunctions = Collections.unmodifiableList(new ArrayList<>(witHostFunctions));
        this.externImports = Collections.unmodifiableList(new ArrayList<>(externImports));
        this.callerAwareHostFunctions = Collections.unmodifiableList(
                new ArrayList<>(callerAwareHostFunctions));
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

    @Override
    public List<ExternImportDefinition> externImports() {
        return externImports;
    }

    @Override
    public List<CallerAwareHostFunctionDefinition> callerAwareHostFunctions() {
        return callerAwareHostFunctions;
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
        private final List<ExternImportDefinition> externImports = new ArrayList<>();
        private final List<CallerAwareHostFunctionDefinition> callerAwareHostFunctions =
                new ArrayList<>();

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

        /**
         * Puts a value into an untyped import map.
         *
         * @deprecated The stored map is not exposed through the
         *     {@link LinkingContext} interface, so no in-tree provider consumes
         *     it — calls have no observable effect on instantiation. Use the
         *     typed methods
         *     {@link #addMemoryImport(String, String, Memory)},
         *     {@link #addTableImport(String, String, Table)},
         *     {@link #addGlobalImport(String, String, Global)},
         *     {@link #addFunctionImport(String, String, Function)}, or
         *     {@link #addExternImport(ExternImportDefinition)} instead. The
         *     method is retained for source-compatibility with existing
         *     callers; its behaviour (a no-op with respect to instantiation)
         *     is unchanged.
         */
        @Deprecated
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

        /**
         * Wire an already-constructed {@link ExternImportDefinition}. Useful
         * when a caller has built the variant elsewhere or is threading a
         * heterogeneous list through their code.
         *
         * @since 2.5.2
         */
        public Builder addExternImport(ExternImportDefinition definition) {
            this.externImports.add(definition);
            return this;
        }

        /**
         * Wire an imported linear memory under {@code moduleName::name}. The
         * importing module must declare
         * {@code (import "<moduleName>" "<name>" (memory ...))}.
         *
         * @since 2.5.2
         */
        public Builder addMemoryImport(String moduleName, String name, Memory memory) {
            this.externImports.add(new MemoryImport(moduleName, name, memory));
            return this;
        }

        /**
         * Wire an imported table under {@code moduleName::name}. The importing
         * module must declare
         * {@code (import "<moduleName>" "<name>" (table ...))}.
         *
         * @since 2.5.2
         */
        public Builder addTableImport(String moduleName, String name, Table table) {
            this.externImports.add(new TableImport(moduleName, name, table));
            return this;
        }

        /**
         * Wire an imported global under {@code moduleName::name}. The importing
         * module must declare
         * {@code (import "<moduleName>" "<name>" (global ...))}.
         *
         * @since 2.5.2
         */
        public Builder addGlobalImport(String moduleName, String name, Global global) {
            this.externImports.add(new GlobalImport(moduleName, name, global));
            return this;
        }

        /**
         * Wire an imported function under {@code moduleName::name}. The
         * importing module must declare
         * {@code (import "<moduleName>" "<name>" (func ...))}.
         *
         * <p>Note: the wasmtime4j-provider does not yet implement this variant
         * — see {@link FunctionImport}. For today's function-import needs use
         * {@link #addHostFunction(String, String, ValueType[], ValueType[], HostFunction)}
         * instead.
         *
         * @since 2.5.2
         */
        public Builder addFunctionImport(String moduleName, String name, Function function) {
            this.externImports.add(new FunctionImport(moduleName, name, function));
            return this;
        }

        /**
         * Register a caller-aware host function under {@code moduleName::functionName}.
         * The provider passes a {@link Caller} handle scoped to the callback
         * frame — see {@link CallerAwareHostFunction}.
         *
         * <p>Providers that do not support the caller-aware pattern raise
         * {@link ai.tegmentum.webassembly4j.api.exception.UnsupportedFeatureException}
         * at {@code instantiate} time when the resulting list is non-empty.
         *
         * @since 2.5.2
         */
        public Builder addCallerAwareHostFunction(String moduleName, String functionName,
                                                   ValueType[] parameterTypes,
                                                   ValueType[] resultTypes,
                                                   CallerAwareHostFunction<?> function) {
            this.callerAwareHostFunctions.add(new CallerAwareHostFunctionDefinition(
                    moduleName, functionName, parameterTypes, resultTypes, function));
            return this;
        }

        /**
         * Register a pre-built {@link CallerAwareHostFunctionDefinition}.
         *
         * @since 2.5.2
         */
        public Builder addCallerAwareHostFunction(CallerAwareHostFunctionDefinition definition) {
            this.callerAwareHostFunctions.add(definition);
            return this;
        }

        public DefaultLinkingContext build() {
            return new DefaultLinkingContext(
                    wasiContext, wasiNnConfig, imports, hostFunctions, witHostFunctions,
                    externImports, callerAwareHostFunctions);
        }
    }
}
