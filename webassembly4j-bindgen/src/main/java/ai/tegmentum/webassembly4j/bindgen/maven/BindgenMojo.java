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

package ai.tegmentum.webassembly4j.bindgen.maven;

import ai.tegmentum.webassembly4j.bindgen.BindgenConfig;
import ai.tegmentum.webassembly4j.bindgen.BindgenException;
import ai.tegmentum.webassembly4j.bindgen.CodeGenerator;
import ai.tegmentum.webassembly4j.bindgen.CodeStyle;
import ai.tegmentum.webassembly4j.bindgen.GeneratedSource;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;

/**
 * Maven Mojo for generating Java bindings from WIT files and WASM modules.
 *
 * <p>Usage example:
 *
 * <pre>{@code
 * <plugin>
 *   <groupId>ai.tegmentum</groupId>
 *   <artifactId>webassembly4j-bindgen</artifactId>
 *   <version>${webassembly4j.version}</version>
 *   <executions>
 *     <execution>
 *       <goals>
 *         <goal>generate</goal>
 *       </goals>
 *       <configuration>
 *         <witDirectory>${project.basedir}/src/main/wit</witDirectory>
 *         <packageName>com.example.generated</packageName>
 *         <codeStyle>MODERN</codeStyle>
 *       </configuration>
 *     </execution>
 *   </executions>
 * </plugin>
 * }</pre>
 */
@Mojo(
    name = "generate",
    defaultPhase = LifecyclePhase.GENERATE_SOURCES,
    requiresDependencyResolution = ResolutionScope.COMPILE,
    threadSafe = true)
public class BindgenMojo extends AbstractMojo {

  /** The Maven project. */
  @Parameter(defaultValue = "${project}", readonly = true, required = true)
  private MavenProject project;

  /** Directory containing WIT files. */
  @Parameter(
      property = "webassembly4j.bindgen.witDirectory",
      defaultValue = "${project.basedir}/src/main/wit")
  private File witDirectory;

  /** Directory containing WASM modules for introspection. */
  @Parameter(
      property = "webassembly4j.bindgen.wasmDirectory",
      defaultValue = "${project.basedir}/src/main/wasm")
  private File wasmDirectory;

  /** Target package name for generated code. */
  @Parameter(property = "webassembly4j.bindgen.packageName", required = true)
  private String packageName;

  /** Output directory for generated sources. */
  @Parameter(
      property = "webassembly4j.bindgen.outputDirectory",
      defaultValue = "${project.build.directory}/generated-sources/webassembly4j-bindgen")
  private File outputDirectory;

  /** Code generation style: MODERN (Java 17+) or LEGACY (Java 8+). */
  @Parameter(property = "webassembly4j.bindgen.codeStyle", defaultValue = "MODERN")
  private String codeStyle;

  /** Whether to generate Javadoc comments. */
  @Parameter(property = "webassembly4j.bindgen.generateJavadoc", defaultValue = "true")
  private boolean generateJavadoc;

  /** Whether to generate builder classes for records. */
  @Parameter(property = "webassembly4j.bindgen.generateBuilders", defaultValue = "true")
  private boolean generateBuilders;

  /** WIT file include patterns (e.g., "*.wit"). */
  @Parameter private List<String> witIncludes;

  /** WIT file exclude patterns. */
  @Parameter private List<String> witExcludes;

  /** Skip execution of this plugin. */
  @Parameter(property = "webassembly4j.bindgen.skip", defaultValue = "false")
  private boolean skip;

  @Override
  public void execute() throws MojoExecutionException, MojoFailureException {
    if (skip) {
      getLog().info("Skipping webassembly4j-bindgen execution");
      return;
    }

    getLog().info("Generating Java bindings from WIT/WASM sources...");

    try {
      // Collect sources
      List<Path> witSources = collectWitSources();
      List<Path> wasmSources = collectWasmSources();

      if (witSources.isEmpty() && wasmSources.isEmpty()) {
        getLog().info("No WIT or WASM sources found, skipping generation");
        return;
      }

      getLog()
          .info(
              "Found "
                  + witSources.size()
                  + " WIT sources and "
                  + wasmSources.size()
                  + " WASM sources");

      // Build configuration
      BindgenConfig config =
          BindgenConfig.builder()
              .codeStyle(parseCodeStyle())
              .packageName(packageName)
              .outputDirectory(outputDirectory.toPath())
              .witSources(witSources)
              .wasmSources(wasmSources)
              .generateJavadoc(generateJavadoc)
              .generateBuilders(generateBuilders)
              .build();

      // Generate code
      CodeGenerator generator = new CodeGenerator(config);
      List<GeneratedSource> sources = generator.generate();

      // Write sources
      for (GeneratedSource source : sources) {
        source.writeTo(outputDirectory.toPath());
        getLog().debug("Generated: " + source.getQualifiedName());
      }

      // Add generated sources to compile path
      project.addCompileSourceRoot(outputDirectory.getAbsolutePath());

      getLog()
          .info(
              "Generated "
                  + sources.size()
                  + " Java source files to "
                  + outputDirectory.getAbsolutePath());

    } catch (BindgenException e) {
      throw new MojoExecutionException("Bindgen failed: " + e.getMessage(), e);
    }
  }

  private CodeStyle parseCodeStyle() throws MojoExecutionException {
    try {
      return CodeStyle.valueOf(codeStyle.toUpperCase());
    } catch (IllegalArgumentException e) {
      throw new MojoExecutionException(
          "Invalid codeStyle: " + codeStyle + ". Must be MODERN or LEGACY");
    }
  }

  private List<Path> collectWitSources() {
    if (witDirectory == null || !witDirectory.exists()) {
      return new ArrayList<>();
    }

    try (Stream<Path> stream = Files.walk(witDirectory.toPath())) {
      return stream
          .filter(Files::isRegularFile)
          .filter(p -> p.toString().endsWith(".wit"))
          .filter(this::matchesWitPatterns)
          .collect(Collectors.toList());
    } catch (IOException e) {
      getLog().warn("Failed to scan WIT directory: " + e.getMessage());
      return new ArrayList<>();
    }
  }

  private List<Path> collectWasmSources() {
    if (wasmDirectory == null || !wasmDirectory.exists()) {
      return new ArrayList<>();
    }

    try (Stream<Path> stream = Files.walk(wasmDirectory.toPath())) {
      return stream
          .filter(Files::isRegularFile)
          .filter(p -> p.toString().endsWith(".wasm"))
          .collect(Collectors.toList());
    } catch (IOException e) {
      getLog().warn("Failed to scan WASM directory: " + e.getMessage());
      return new ArrayList<>();
    }
  }

  private boolean matchesWitPatterns(final Path path) {
    String fileName = path.getFileName().toString();

    // Check excludes first
    if (witExcludes != null) {
      for (String pattern : witExcludes) {
        if (matchesPattern(fileName, pattern)) {
          return false;
        }
      }
    }

    // If no includes specified, include all
    if (witIncludes == null || witIncludes.isEmpty()) {
      return true;
    }

    // Check includes
    for (String pattern : witIncludes) {
      if (matchesPattern(fileName, pattern)) {
        return true;
      }
    }

    return false;
  }

  private boolean matchesPattern(final String fileName, final String pattern) {
    // Simple glob pattern matching
    String regex = pattern.replace(".", "\\.").replace("*", ".*").replace("?", ".");
    return fileName.matches(regex);
  }
}
