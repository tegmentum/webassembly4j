/*
 * Copyright 2024 Tegmentum AI. All rights reserved.
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

package ai.tegmentum.webassembly4j.bindgen;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Configuration for Java binding generation.
 *
 * <p>This immutable class holds all configuration options for the binding generator, including
 * source files, output settings, and code generation preferences.
 *
 * <p>Use the {@link Builder} to construct instances:
 *
 * <pre>{@code
 * BindgenConfig config = BindgenConfig.builder()
 *     .packageName("com.example.generated")
 *     .outputDirectory(Path.of("target/generated-sources"))
 *     .codeStyle(CodeStyle.MODERN)
 *     .witSources(List.of(Path.of("src/main/wit")))
 *     .build();
 * }</pre>
 */
public final class BindgenConfig {

  private final CodeStyle codeStyle;
  private final String packageName;
  private final Path outputDirectory;
  private final List<Path> witSources;
  private final List<Path> wasmSources;
  private final boolean generateJavadoc;
  private final boolean generateBuilders;
  private final boolean generateImplementations;
  private final boolean generateServiceLoader;

  private BindgenConfig(final Builder builder) {
    this.codeStyle = builder.codeStyle;
    this.packageName = builder.packageName;
    this.outputDirectory = builder.outputDirectory;
    this.witSources = Collections.unmodifiableList(new ArrayList<>(builder.witSources));
    this.wasmSources = Collections.unmodifiableList(new ArrayList<>(builder.wasmSources));
    this.generateJavadoc = builder.generateJavadoc;
    this.generateBuilders = builder.generateBuilders;
    this.generateImplementations = builder.generateImplementations;
    this.generateServiceLoader = builder.generateServiceLoader;
  }

  /**
   * Creates a new builder for BindgenConfig.
   *
   * @return a new builder instance
   */
  public static Builder builder() {
    return new Builder();
  }

  /**
   * Returns the code generation style.
   *
   * @return the code style (MODERN or LEGACY)
   */
  public CodeStyle getCodeStyle() {
    return codeStyle;
  }

  /**
   * Returns the target Java package name for generated code.
   *
   * @return the package name
   */
  public String getPackageName() {
    return packageName;
  }

  /**
   * Returns the output directory for generated source files.
   *
   * @return the output directory path
   */
  public Path getOutputDirectory() {
    return outputDirectory;
  }

  /**
   * Returns the list of WIT source files or directories.
   *
   * @return an unmodifiable list of WIT source paths
   */
  public List<Path> getWitSources() {
    return witSources;
  }

  /**
   * Returns the list of WASM source files for introspection.
   *
   * @return an unmodifiable list of WASM source paths
   */
  public List<Path> getWasmSources() {
    return wasmSources;
  }

  /**
   * Returns whether Javadoc comments should be generated.
   *
   * @return true if Javadoc should be generated
   */
  public boolean isGenerateJavadoc() {
    return generateJavadoc;
  }

  /**
   * Returns whether builder classes should be generated for complex types.
   *
   * @return true if builders should be generated
   */
  public boolean isGenerateBuilders() {
    return generateBuilders;
  }

  /**
   * Returns whether implementation classes should be generated for interfaces.
   *
   * @return true if implementations should be generated
   */
  public boolean isGenerateImplementations() {
    return generateImplementations;
  }

  /**
   * Returns whether ServiceLoader registration files should be generated.
   *
   * @return true if ServiceLoader files should be generated
   */
  public boolean isGenerateServiceLoader() {
    return generateServiceLoader;
  }

  /**
   * Checks if there are WIT sources configured.
   *
   * @return true if WIT sources exist
   */
  public boolean hasWitSources() {
    return !witSources.isEmpty();
  }

  /**
   * Checks if there are WASM sources configured.
   *
   * @return true if WASM sources exist
   */
  public boolean hasWasmSources() {
    return !wasmSources.isEmpty();
  }

  /**
   * Validates this configuration and throws an exception if invalid.
   *
   * @throws BindgenException if the configuration is invalid
   */
  public void validate() throws BindgenException {
    if (packageName == null || packageName.isEmpty()) {
      throw BindgenException.configurationError("packageName is required");
    }
    if (outputDirectory == null) {
      throw BindgenException.configurationError("outputDirectory is required");
    }
    if (witSources.isEmpty() && wasmSources.isEmpty()) {
      throw BindgenException.configurationError(
          "At least one WIT or WASM source must be specified");
    }
  }

  @Override
  public boolean equals(final Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null || getClass() != obj.getClass()) {
      return false;
    }
    BindgenConfig that = (BindgenConfig) obj;
    return generateJavadoc == that.generateJavadoc
        && generateBuilders == that.generateBuilders
        && generateImplementations == that.generateImplementations
        && generateServiceLoader == that.generateServiceLoader
        && codeStyle == that.codeStyle
        && Objects.equals(packageName, that.packageName)
        && Objects.equals(outputDirectory, that.outputDirectory)
        && Objects.equals(witSources, that.witSources)
        && Objects.equals(wasmSources, that.wasmSources);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        codeStyle,
        packageName,
        outputDirectory,
        witSources,
        wasmSources,
        generateJavadoc,
        generateBuilders,
        generateImplementations,
        generateServiceLoader);
  }

  @Override
  public String toString() {
    return "BindgenConfig{"
        + "codeStyle="
        + codeStyle
        + ", packageName='"
        + packageName
        + '\''
        + ", outputDirectory="
        + outputDirectory
        + ", witSources="
        + witSources.size()
        + " files"
        + ", wasmSources="
        + wasmSources.size()
        + " files"
        + ", generateJavadoc="
        + generateJavadoc
        + ", generateBuilders="
        + generateBuilders
        + ", generateImplementations="
        + generateImplementations
        + ", generateServiceLoader="
        + generateServiceLoader
        + '}';
  }

  /** Builder for creating BindgenConfig instances. */
  public static final class Builder {

    private CodeStyle codeStyle = CodeStyle.MODERN;
    private String packageName;
    private Path outputDirectory;
    private List<Path> witSources = new ArrayList<>();
    private List<Path> wasmSources = new ArrayList<>();
    private boolean generateJavadoc = true;
    private boolean generateBuilders = true;
    private boolean generateImplementations = true;
    private boolean generateServiceLoader = true;

    private Builder() {}

    /**
     * Sets the code generation style.
     *
     * @param style the code style (MODERN or LEGACY)
     * @return this builder
     */
    public Builder codeStyle(final CodeStyle style) {
      this.codeStyle = Objects.requireNonNull(style, "codeStyle");
      return this;
    }

    /**
     * Sets the target Java package name.
     *
     * @param name the package name
     * @return this builder
     */
    public Builder packageName(final String name) {
      this.packageName = Objects.requireNonNull(name, "packageName");
      return this;
    }

    /**
     * Sets the output directory for generated sources.
     *
     * @param directory the output directory path
     * @return this builder
     */
    public Builder outputDirectory(final Path directory) {
      this.outputDirectory = Objects.requireNonNull(directory, "outputDirectory");
      return this;
    }

    /**
     * Sets the WIT source files or directories.
     *
     * @param sources list of WIT source paths
     * @return this builder
     */
    public Builder witSources(final List<Path> sources) {
      this.witSources = new ArrayList<>(Objects.requireNonNull(sources, "witSources"));
      return this;
    }

    /**
     * Adds a WIT source file or directory.
     *
     * @param source a WIT source path
     * @return this builder
     */
    public Builder addWitSource(final Path source) {
      this.witSources.add(Objects.requireNonNull(source, "source"));
      return this;
    }

    /**
     * Sets the WASM source files for introspection.
     *
     * @param sources list of WASM source paths
     * @return this builder
     */
    public Builder wasmSources(final List<Path> sources) {
      this.wasmSources = new ArrayList<>(Objects.requireNonNull(sources, "wasmSources"));
      return this;
    }

    /**
     * Adds a WASM source file.
     *
     * @param source a WASM source path
     * @return this builder
     */
    public Builder addWasmSource(final Path source) {
      this.wasmSources.add(Objects.requireNonNull(source, "source"));
      return this;
    }

    /**
     * Sets whether to generate Javadoc comments.
     *
     * @param generate true to generate Javadoc
     * @return this builder
     */
    public Builder generateJavadoc(final boolean generate) {
      this.generateJavadoc = generate;
      return this;
    }

    /**
     * Sets whether to generate builder classes.
     *
     * @param generate true to generate builders
     * @return this builder
     */
    public Builder generateBuilders(final boolean generate) {
      this.generateBuilders = generate;
      return this;
    }

    /**
     * Sets whether to generate implementation classes for interfaces.
     *
     * @param generate true to generate implementations
     * @return this builder
     */
    public Builder generateImplementations(final boolean generate) {
      this.generateImplementations = generate;
      return this;
    }

    /**
     * Sets whether to generate ServiceLoader registration files.
     *
     * @param generate true to generate ServiceLoader files
     * @return this builder
     */
    public Builder generateServiceLoader(final boolean generate) {
      this.generateServiceLoader = generate;
      return this;
    }

    /**
     * Builds the BindgenConfig instance.
     *
     * @return a new BindgenConfig
     */
    public BindgenConfig build() {
      return new BindgenConfig(this);
    }
  }
}
