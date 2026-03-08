package ai.tegmentum.webassembly4j.api;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class DefaultLinkingContext implements LinkingContext {

    private final WasiContext wasiContext;
    private final Map<String, Object> imports;

    private DefaultLinkingContext(WasiContext wasiContext, Map<String, Object> imports) {
        this.wasiContext = wasiContext;
        this.imports = Collections.unmodifiableMap(new LinkedHashMap<>(imports));
    }

    public WasiContext wasiContext() {
        return wasiContext;
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

        public DefaultLinkingContext build() {
            return new DefaultLinkingContext(wasiContext, imports);
        }
    }
}
