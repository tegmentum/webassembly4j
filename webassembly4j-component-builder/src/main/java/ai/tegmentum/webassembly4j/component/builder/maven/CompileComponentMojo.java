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
package ai.tegmentum.webassembly4j.component.builder.maven;

import ai.tegmentum.webassembly4j.component.builder.ComponentBuildPipeline;
import ai.tegmentum.webassembly4j.component.builder.ComponentBuilderConfig;
import ai.tegmentum.webassembly4j.component.builder.ComponentBuilderException;
import ai.tegmentum.webassembly4j.component.builder.compile.CompilationResult;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Compiles Java to WASM and wraps as a component.
 *
 * <p>Runs native-image + wasm-tools to produce a component WASM binary.
 * Requires GraalVM 25 EA+ and wasm-tools on PATH.
 */
@Mojo(
    name = "compile-component",
    defaultPhase = LifecyclePhase.PACKAGE,
    requiresDependencyResolution = ResolutionScope.COMPILE,
    threadSafe = true)
public class CompileComponentMojo extends AbstractMojo {

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    /** Main class for native-image compilation. */
    @Parameter(property = "webassembly4j.component.mainClass", required = true)
    private String mainClass;

    /** Output directory for WASM artifacts. */
    @Parameter(
        property = "webassembly4j.component.wasmOutputDirectory",
        defaultValue = "${project.build.directory}/wasm")
    private File wasmOutputDirectory;

    /** Directory containing WIT files (for embedding). */
    @Parameter(
        property = "webassembly4j.component.witOutputDirectory",
        defaultValue = "${project.build.directory}/generated-wit")
    private File witOutputDirectory;

    /** Component name (used for output file naming). */
    @Parameter(
        property = "webassembly4j.component.componentName",
        defaultValue = "${project.artifactId}")
    private String componentName;

    /** Skip execution. */
    @Parameter(property = "webassembly4j.component.skip", defaultValue = "false")
    private boolean skip;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        if (skip) {
            getLog().info("Skipping webassembly4j-component compile-component");
            return;
        }

        try {
            List<Path> classpathEntries = buildClasspath();

            ComponentBuilderConfig config = ComponentBuilderConfig.builder()
                    .mainClass(mainClass)
                    .classpathEntries(classpathEntries)
                    .wasmOutputDirectory(wasmOutputDirectory.toPath())
                    .witOutputDirectory(witOutputDirectory.toPath())
                    .componentName(componentName)
                    .build();

            ComponentBuildPipeline pipeline = new ComponentBuildPipeline(config);
            CompilationResult result = pipeline.compileComponent();

            if (result.isSuccess()) {
                result.getComponentWasm().ifPresent(
                        p -> getLog().info("Component WASM: " + p));
                result.getOutputWasm().ifPresent(
                        p -> getLog().info("Core WASM: " + p));
            } else {
                String error = result.getErrorMessage().orElse("Unknown error");
                throw new MojoFailureException("Compilation failed: " + error);
            }

        } catch (ComponentBuilderException e) {
            throw new MojoExecutionException(
                    "Component compilation failed: " + e.getMessage(), e);
        }
    }

    @SuppressWarnings("unchecked")
    private List<Path> buildClasspath() {
        List<Path> entries = new ArrayList<>();
        String outputDir = project.getBuild().getOutputDirectory();
        if (outputDir != null) {
            entries.add(new File(outputDir).toPath());
        }
        try {
            List<String> elements = project.getCompileClasspathElements();
            if (elements != null) {
                for (String element : elements) {
                    entries.add(new File(element).toPath());
                }
            }
        } catch (Exception e) {
            getLog().warn("Failed to resolve compile classpath: " + e.getMessage());
        }
        return entries;
    }
}
