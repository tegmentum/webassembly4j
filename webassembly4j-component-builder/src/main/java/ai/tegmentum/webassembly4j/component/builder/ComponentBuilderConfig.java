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
package ai.tegmentum.webassembly4j.component.builder;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Configuration for the component builder pipeline.
 */
public final class ComponentBuilderConfig {

    private final List<String> sourceClasses;
    private final String scanPackage;
    private final List<Path> classpathEntries;
    private final Path witOutputDirectory;
    private final Path wasmOutputDirectory;
    private final String componentName;
    private final String mainClass;
    private final String gluePackageName;

    private ComponentBuilderConfig(Builder builder) {
        this.sourceClasses = Collections.unmodifiableList(new ArrayList<>(builder.sourceClasses));
        this.scanPackage = builder.scanPackage;
        this.classpathEntries = Collections.unmodifiableList(new ArrayList<>(builder.classpathEntries));
        this.witOutputDirectory = Objects.requireNonNull(builder.witOutputDirectory, "witOutputDirectory");
        this.wasmOutputDirectory = builder.wasmOutputDirectory;
        this.componentName = builder.componentName;
        this.mainClass = builder.mainClass;
        this.gluePackageName = builder.gluePackageName;
    }

    public List<String> getSourceClasses() {
        return sourceClasses;
    }

    public String getScanPackage() {
        return scanPackage;
    }

    public List<Path> getClasspathEntries() {
        return classpathEntries;
    }

    public Path getWitOutputDirectory() {
        return witOutputDirectory;
    }

    public Path getWasmOutputDirectory() {
        return wasmOutputDirectory;
    }

    public String getComponentName() {
        return componentName;
    }

    public String getMainClass() {
        return mainClass;
    }

    public String getGluePackageName() {
        return gluePackageName;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private List<String> sourceClasses = new ArrayList<>();
        private String scanPackage;
        private List<Path> classpathEntries = new ArrayList<>();
        private Path witOutputDirectory;
        private Path wasmOutputDirectory;
        private String componentName;
        private String mainClass;
        private String gluePackageName;

        private Builder() {}

        public Builder sourceClasses(List<String> sourceClasses) {
            this.sourceClasses = sourceClasses != null ? sourceClasses : new ArrayList<>();
            return this;
        }

        public Builder scanPackage(String scanPackage) {
            this.scanPackage = scanPackage;
            return this;
        }

        public Builder classpathEntries(List<Path> classpathEntries) {
            this.classpathEntries = classpathEntries != null ? classpathEntries : new ArrayList<>();
            return this;
        }

        public Builder witOutputDirectory(Path witOutputDirectory) {
            this.witOutputDirectory = witOutputDirectory;
            return this;
        }

        public Builder wasmOutputDirectory(Path wasmOutputDirectory) {
            this.wasmOutputDirectory = wasmOutputDirectory;
            return this;
        }

        public Builder componentName(String componentName) {
            this.componentName = componentName;
            return this;
        }

        public Builder mainClass(String mainClass) {
            this.mainClass = mainClass;
            return this;
        }

        public Builder gluePackageName(String gluePackageName) {
            this.gluePackageName = gluePackageName;
            return this;
        }

        public ComponentBuilderConfig build() {
            return new ComponentBuilderConfig(this);
        }
    }
}
