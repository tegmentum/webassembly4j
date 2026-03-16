/*
 * Copyright 2025 Tegmentum AI. All rights reserved.
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
package ai.tegmentum.webassembly4j.component.builder.compile;

import java.nio.file.Path;
import java.util.Optional;

/**
 * Result of a WASM compilation step.
 */
public final class CompilationResult {

    private final boolean success;
    private final Path outputWasm;
    private final Path outputJs;
    private final Path outputWat;
    private final Path componentWasm;
    private final String errorMessage;

    private CompilationResult(Builder builder) {
        this.success = builder.success;
        this.outputWasm = builder.outputWasm;
        this.outputJs = builder.outputJs;
        this.outputWat = builder.outputWat;
        this.componentWasm = builder.componentWasm;
        this.errorMessage = builder.errorMessage;
    }

    public boolean isSuccess() {
        return success;
    }

    public Optional<Path> getOutputWasm() {
        return Optional.ofNullable(outputWasm);
    }

    public Optional<Path> getOutputJs() {
        return Optional.ofNullable(outputJs);
    }

    public Optional<Path> getOutputWat() {
        return Optional.ofNullable(outputWat);
    }

    public Optional<Path> getComponentWasm() {
        return Optional.ofNullable(componentWasm);
    }

    public Optional<String> getErrorMessage() {
        return Optional.ofNullable(errorMessage);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private boolean success;
        private Path outputWasm;
        private Path outputJs;
        private Path outputWat;
        private Path componentWasm;
        private String errorMessage;

        private Builder() {}

        public Builder success(boolean success) {
            this.success = success;
            return this;
        }

        public Builder outputWasm(Path outputWasm) {
            this.outputWasm = outputWasm;
            return this;
        }

        public Builder outputJs(Path outputJs) {
            this.outputJs = outputJs;
            return this;
        }

        public Builder outputWat(Path outputWat) {
            this.outputWat = outputWat;
            return this;
        }

        public Builder componentWasm(Path componentWasm) {
            this.componentWasm = componentWasm;
            return this;
        }

        public Builder errorMessage(String errorMessage) {
            this.errorMessage = errorMessage;
            return this;
        }

        public CompilationResult build() {
            return new CompilationResult(this);
        }
    }
}
