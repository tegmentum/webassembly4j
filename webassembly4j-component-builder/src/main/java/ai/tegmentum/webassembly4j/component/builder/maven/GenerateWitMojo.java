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
import java.util.stream.Collectors;

/**
 * Generates WIT files from annotated Java classes.
 */
@Mojo(
    name = "generate-wit",
    defaultPhase = LifecyclePhase.GENERATE_SOURCES,
    requiresDependencyResolution = ResolutionScope.COMPILE,
    threadSafe = true)
public class GenerateWitMojo extends AbstractMojo {

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    /** Fully qualified class names to scan for component annotations. */
    @Parameter(property = "webassembly4j.component.sourceClasses")
    private List<String> sourceClasses;

    /** Package to scan for annotated classes. */
    @Parameter(property = "webassembly4j.component.scanPackage")
    private String scanPackage;

    /** Output directory for generated WIT files. */
    @Parameter(
        property = "webassembly4j.component.witOutputDirectory",
        defaultValue = "${project.build.directory}/generated-wit")
    private File witOutputDirectory;

    /** Skip execution. */
    @Parameter(property = "webassembly4j.component.skip", defaultValue = "false")
    private boolean skip;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        if (skip) {
            getLog().info("Skipping webassembly4j-component generate-wit");
            return;
        }

        if ((sourceClasses == null || sourceClasses.isEmpty()) && scanPackage == null) {
            throw new MojoExecutionException(
                    "Either sourceClasses or scanPackage must be specified");
        }

        try {
            List<Path> classpathEntries = buildClasspath();

            ComponentBuilderConfig config = ComponentBuilderConfig.builder()
                    .sourceClasses(sourceClasses != null ? sourceClasses : new ArrayList<>())
                    .scanPackage(scanPackage)
                    .classpathEntries(classpathEntries)
                    .witOutputDirectory(witOutputDirectory.toPath())
                    .build();

            ComponentBuildPipeline pipeline = new ComponentBuildPipeline(config);
            Path witFile = pipeline.generateWit();

            getLog().info("Generated WIT file: " + witFile);

        } catch (ComponentBuilderException e) {
            throw new MojoExecutionException("WIT generation failed: " + e.getMessage(), e);
        }
    }

    @SuppressWarnings("unchecked")
    private List<Path> buildClasspath() {
        List<Path> entries = new ArrayList<>();

        // Add compile output directory
        String outputDir = project.getBuild().getOutputDirectory();
        if (outputDir != null) {
            entries.add(new File(outputDir).toPath());
        }

        // Add compile classpath elements
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
