/*
 * Copyright 2026 Tegmentum AI
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package ai.tegmentum.webassembly4j.api;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Provider-neutral configuration for enabling WASI-NN on a component
 * {@link LinkingContext}. Passed to
 * {@link DefaultLinkingContext.Builder#enableWasiNn(WasiNnConfig)}; providers that
 * support wasi:nn translate this to their native config and register the interface
 * on their linker. Providers without wasi:nn support surface
 * {@link ai.tegmentum.webassembly4j.api.exception.UnsupportedFeatureException} when
 * a config is present.
 *
 * <p>An empty config (the {@link #defaults()} instance) requests the provider's
 * default backend set — typically ORT/ONNX under the current wasmtime4j pin. That
 * mirrors the zero-arg {@code enableWasiNn()} on wasmtime4j's native linker.
 *
 * <p><b>Extensibility.</b> Named-model registry entries are additive; a caller
 * that supplies models today gets no behaviour change from a provider that has not
 * yet wired the registry through (wasmtime4j {@code 46.0.1-1.4.0} silently ignores
 * them). Future additions (preferred backend enumeration, per-graph execution
 * targets) land as additive setters.
 *
 * <p><b>Thread safety.</b> Instances are immutable after {@link Builder#build()}
 * and safe to share across threads.
 */
public final class WasiNnConfig {

    private static final WasiNnConfig DEFAULTS = new WasiNnConfig(Collections.emptyMap());

    private final Map<String, byte[]> namedModels;

    private WasiNnConfig(Map<String, byte[]> namedModels) {
        this.namedModels = Collections.unmodifiableMap(new LinkedHashMap<>(namedModels));
    }

    /**
     * Returns a shared, empty configuration equivalent to the zero-arg
     * {@code enableWasiNn()} on the underlying provider linker.
     */
    public static WasiNnConfig defaults() {
        return DEFAULTS;
    }

    /** Starts a builder for a customised configuration. */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Returns the (read-only) named-model registry entries supplied to this
     * config. Providers translate these into their native wasi:nn registry when
     * the plumbing is present; entries are silently ignored otherwise.
     */
    public Map<String, byte[]> namedModels() {
        return namedModels;
    }

    /** Builder for {@link WasiNnConfig}. */
    public static final class Builder {
        private final Map<String, byte[]> namedModels = new LinkedHashMap<>();

        private Builder() {
        }

        /**
         * Register a model by name so guests can look it up via
         * {@code wasi:nn/graph.load-by-name}. Model bytes are copied at build
         * time; the caller's array may be mutated afterwards without affecting
         * linker behaviour.
         */
        public Builder registerModel(String name, byte[] modelBytes) {
            if (name == null || name.isEmpty()) {
                throw new IllegalArgumentException("model name cannot be null or empty");
            }
            if (modelBytes == null) {
                throw new IllegalArgumentException("modelBytes cannot be null");
            }
            namedModels.put(name, modelBytes.clone());
            return this;
        }

        public WasiNnConfig build() {
            return new WasiNnConfig(namedModels);
        }
    }
}
