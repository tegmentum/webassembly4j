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
package ai.tegmentum.webassembly4j.component.builder.cli;

import ai.tegmentum.webassembly4j.component.builder.ComponentBuildPipeline;
import ai.tegmentum.webassembly4j.component.builder.ComponentBuilderConfig;
import ai.tegmentum.webassembly4j.component.builder.ComponentBuilderException;
import ai.tegmentum.webassembly4j.component.builder.compile.CompilationResult;
import ai.tegmentum.webassembly4j.component.builder.scan.ScannedComponent;
import ai.tegmentum.webassembly4j.component.builder.wit.WitEmitter;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

/**
 * CLI entry point for webassembly4j-component-builder.
 */
@Command(
    name = "webassembly4j-component",
    mixinStandardHelpOptions = true,
    version = "webassembly4j-component-builder 1.0.0",
    description = "Java to WebAssembly Component toolchain",
    subcommands = {
        ComponentBuilderCli.GenerateWitCommand.class,
        ComponentBuilderCli.CompileCommand.class,
        ComponentBuilderCli.BuildCommand.class
    })
public final class ComponentBuilderCli implements Callable<Integer> {

    @Override
    public Integer call() {
        CommandLine.usage(this, System.out);
        return 0;
    }

    @Command(
        name = "generate-wit",
        description = "Generate WIT files from annotated Java classes")
    static final class GenerateWitCommand implements Callable<Integer> {

        @Option(
            names = {"--source-classes", "-s"},
            description = "Fully qualified class names to scan",
            paramLabel = "CLASS")
        private List<String> sourceClasses;

        @Option(
            names = {"--scan-package", "-p"},
            description = "Java package to scan for annotated classes",
            paramLabel = "PACKAGE")
        private String scanPackage;

        @Option(
            names = {"--classpath", "-cp"},
            description = "Classpath entries (directories or JARs)",
            paramLabel = "PATH")
        private List<File> classpathEntries;

        @Option(
            names = {"--output", "-o"},
            defaultValue = "wit",
            description = "Output directory for WIT files (default: ${DEFAULT-VALUE})",
            paramLabel = "DIR")
        private File outputDirectory;

        @Option(
            names = {"--dry-run"},
            description = "Show generated WIT without writing files")
        private boolean dryRun;

        @Option(
            names = {"--verbose", "-v"},
            description = "Enable verbose output")
        private boolean verbose;

        @Override
        public Integer call() {
            try {
                if ((sourceClasses == null || sourceClasses.isEmpty())
                        && (scanPackage == null || scanPackage.isEmpty())) {
                    System.err.println("Error: Either --source-classes or --scan-package is required");
                    return 1;
                }

                List<Path> cpEntries = resolveClasspath(classpathEntries);

                ComponentBuilderConfig config = ComponentBuilderConfig.builder()
                        .sourceClasses(sourceClasses != null ? sourceClasses : new ArrayList<>())
                        .scanPackage(scanPackage)
                        .classpathEntries(cpEntries)
                        .witOutputDirectory(outputDirectory.toPath())
                        .build();

                ComponentBuildPipeline pipeline = new ComponentBuildPipeline(config);

                if (dryRun) {
                    ScannedComponent component = pipeline.scan();
                    String wit = WitEmitter.emit(component);
                    System.out.println(wit);
                } else {
                    Path witFile = pipeline.generateWit();
                    System.out.println("Generated WIT file: " + witFile);
                }

                return 0;

            } catch (ComponentBuilderException e) {
                System.err.println("Error: " + e.getMessage());
                if (verbose && e.getCause() != null) {
                    e.getCause().printStackTrace(System.err);
                }
                return 1;
            } catch (Exception e) {
                System.err.println("Unexpected error: " + e.getMessage());
                if (verbose) {
                    e.printStackTrace(System.err);
                }
                return 2;
            }
        }
    }

    @Command(
        name = "compile",
        description = "Compile Java to WASM component via native-image + wasm-tools")
    static final class CompileCommand implements Callable<Integer> {

        @Option(
            names = {"--main-class", "-m"},
            description = "Main class for native-image compilation",
            required = true,
            paramLabel = "CLASS")
        private String mainClass;

        @Option(
            names = {"--classpath", "-cp"},
            description = "Classpath entries (directories or JARs)",
            paramLabel = "PATH")
        private List<File> classpathEntries;

        @Option(
            names = {"--wit-dir", "-w"},
            description = "Directory containing WIT files to embed",
            paramLabel = "DIR")
        private File witDirectory;

        @Option(
            names = {"--output", "-o"},
            defaultValue = "wasm",
            description = "Output directory for WASM artifacts (default: ${DEFAULT-VALUE})",
            paramLabel = "DIR")
        private File outputDirectory;

        @Option(
            names = {"--name", "-n"},
            defaultValue = "component",
            description = "Component name (default: ${DEFAULT-VALUE})",
            paramLabel = "NAME")
        private String componentName;

        @Option(
            names = {"--verbose", "-v"},
            description = "Enable verbose output")
        private boolean verbose;

        @Override
        public Integer call() {
            try {
                List<Path> cpEntries = resolveClasspath(classpathEntries);

                ComponentBuilderConfig.Builder builder = ComponentBuilderConfig.builder()
                        .mainClass(mainClass)
                        .classpathEntries(cpEntries)
                        .wasmOutputDirectory(outputDirectory.toPath())
                        .componentName(componentName)
                        .witOutputDirectory(
                                witDirectory != null ? witDirectory.toPath() : outputDirectory.toPath());

                ComponentBuildPipeline pipeline = new ComponentBuildPipeline(builder.build());
                CompilationResult result = pipeline.compileComponent();

                if (result.isSuccess()) {
                    result.getComponentWasm().ifPresent(
                            p -> System.out.println("Component WASM: " + p));
                    result.getOutputWasm().ifPresent(
                            p -> { if (verbose) System.out.println("Core WASM: " + p); });
                    return 0;
                } else {
                    System.err.println("Compilation failed: " +
                            result.getErrorMessage().orElse("Unknown error"));
                    return 1;
                }

            } catch (ComponentBuilderException e) {
                System.err.println("Error: " + e.getMessage());
                if (verbose && e.getCause() != null) {
                    e.getCause().printStackTrace(System.err);
                }
                return 1;
            } catch (Exception e) {
                System.err.println("Unexpected error: " + e.getMessage());
                if (verbose) {
                    e.printStackTrace(System.err);
                }
                return 2;
            }
        }
    }

    @Command(
        name = "build",
        description = "Generate WIT and compile to WASM component (end-to-end)")
    static final class BuildCommand implements Callable<Integer> {

        @Option(
            names = {"--source-classes", "-s"},
            description = "Fully qualified class names to scan",
            paramLabel = "CLASS",
            required = true)
        private List<String> sourceClasses;

        @Option(
            names = {"--main-class", "-m"},
            description = "Main class for native-image compilation",
            required = true,
            paramLabel = "CLASS")
        private String mainClass;

        @Option(
            names = {"--classpath", "-cp"},
            description = "Classpath entries (directories or JARs)",
            paramLabel = "PATH")
        private List<File> classpathEntries;

        @Option(
            names = {"--wit-output", "-w"},
            defaultValue = "generated-wit",
            description = "Output directory for WIT files (default: ${DEFAULT-VALUE})",
            paramLabel = "DIR")
        private File witOutputDirectory;

        @Option(
            names = {"--wasm-output", "-o"},
            defaultValue = "wasm",
            description = "Output directory for WASM artifacts (default: ${DEFAULT-VALUE})",
            paramLabel = "DIR")
        private File wasmOutputDirectory;

        @Option(
            names = {"--name", "-n"},
            defaultValue = "component",
            description = "Component name (default: ${DEFAULT-VALUE})",
            paramLabel = "NAME")
        private String componentName;

        @Option(
            names = {"--verbose", "-v"},
            description = "Enable verbose output")
        private boolean verbose;

        @Override
        public Integer call() {
            try {
                List<Path> cpEntries = resolveClasspath(classpathEntries);

                ComponentBuilderConfig config = ComponentBuilderConfig.builder()
                        .sourceClasses(sourceClasses)
                        .mainClass(mainClass)
                        .classpathEntries(cpEntries)
                        .witOutputDirectory(witOutputDirectory.toPath())
                        .wasmOutputDirectory(wasmOutputDirectory.toPath())
                        .componentName(componentName)
                        .build();

                ComponentBuildPipeline pipeline = new ComponentBuildPipeline(config);
                CompilationResult result = pipeline.build();

                if (result.isSuccess()) {
                    System.out.println("WIT generated to: " + witOutputDirectory);
                    result.getComponentWasm().ifPresent(
                            p -> System.out.println("Component WASM: " + p));
                    return 0;
                } else {
                    System.err.println("Build failed: " +
                            result.getErrorMessage().orElse("Unknown error"));
                    return 1;
                }

            } catch (ComponentBuilderException e) {
                System.err.println("Error: " + e.getMessage());
                if (verbose && e.getCause() != null) {
                    e.getCause().printStackTrace(System.err);
                }
                return 1;
            } catch (Exception e) {
                System.err.println("Unexpected error: " + e.getMessage());
                if (verbose) {
                    e.printStackTrace(System.err);
                }
                return 2;
            }
        }
    }

    private static List<Path> resolveClasspath(List<File> entries) {
        if (entries == null || entries.isEmpty()) {
            return new ArrayList<>();
        }
        return entries.stream()
                .map(File::toPath)
                .collect(Collectors.toList());
    }

    public static void main(String[] args) {
        int exitCode = new CommandLine(new ComponentBuilderCli()).execute(args);
        System.exit(exitCode);
    }
}
