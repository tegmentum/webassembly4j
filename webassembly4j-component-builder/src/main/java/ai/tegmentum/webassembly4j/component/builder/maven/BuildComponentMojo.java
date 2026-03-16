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
 * Convenience goal: generate-wit + compile-component in one step.
 *
 * <p>Scans annotated classes, emits WIT, compiles via native-image,
 * and wraps the result as a WASM component.
 */
@Mojo(
    name = "build",
    defaultPhase = LifecyclePhase.PACKAGE,
    requiresDependencyResolution = ResolutionScope.COMPILE,
    threadSafe = true)
public class BuildComponentMojo extends AbstractMojo {

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    /** Fully qualified class names to scan for component annotations. */
    @Parameter(property = "webassembly4j.component.sourceClasses", required = true)
    private List<String> sourceClasses;

    /** Main class for native-image compilation. */
    @Parameter(property = "webassembly4j.component.mainClass", required = true)
    private String mainClass;

    /** Output directory for generated WIT files. */
    @Parameter(
        property = "webassembly4j.component.witOutputDirectory",
        defaultValue = "${project.build.directory}/generated-wit")
    private File witOutputDirectory;

    /** Output directory for WASM artifacts. */
    @Parameter(
        property = "webassembly4j.component.wasmOutputDirectory",
        defaultValue = "${project.build.directory}/wasm")
    private File wasmOutputDirectory;

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
            getLog().info("Skipping webassembly4j-component build");
            return;
        }

        try {
            List<Path> classpathEntries = buildClasspath();

            ComponentBuilderConfig config = ComponentBuilderConfig.builder()
                    .sourceClasses(sourceClasses)
                    .mainClass(mainClass)
                    .classpathEntries(classpathEntries)
                    .witOutputDirectory(witOutputDirectory.toPath())
                    .wasmOutputDirectory(wasmOutputDirectory.toPath())
                    .componentName(componentName)
                    .build();

            ComponentBuildPipeline pipeline = new ComponentBuildPipeline(config);
            CompilationResult result = pipeline.build();

            if (result.isSuccess()) {
                getLog().info("WIT generated to: " + witOutputDirectory);
                result.getComponentWasm().ifPresent(
                        p -> getLog().info("Component WASM: " + p));
            } else {
                String error = result.getErrorMessage().orElse("Unknown error");
                throw new MojoFailureException("Build failed: " + error);
            }

        } catch (ComponentBuilderException e) {
            throw new MojoExecutionException(
                    "Component build failed: " + e.getMessage(), e);
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
