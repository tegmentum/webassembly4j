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

import ai.tegmentum.webassembly4j.component.builder.compile.CompilationResult;
import ai.tegmentum.webassembly4j.component.builder.compile.GlueCodeGenerator;
import ai.tegmentum.webassembly4j.component.builder.compile.NativeImageCompiler;
import ai.tegmentum.webassembly4j.component.builder.compile.WasmToolsLinker;
import ai.tegmentum.webassembly4j.component.builder.scan.ComponentValidator;
import ai.tegmentum.webassembly4j.component.builder.scan.JavaInterfaceScanner;
import ai.tegmentum.webassembly4j.component.builder.scan.PackageScanner;
import ai.tegmentum.webassembly4j.component.builder.scan.ScannedComponent;
import ai.tegmentum.webassembly4j.component.builder.wit.WitEmitter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Orchestrates the component build pipeline: scan → validate → emit WIT → compile → wrap.
 */
public final class ComponentBuildPipeline {

    private final ComponentBuilderConfig config;

    public ComponentBuildPipeline(ComponentBuilderConfig config) {
        this.config = config;
    }

    /**
     * Runs the WIT generation phase: scans annotated classes, validates, and emits WIT files.
     *
     * @return the path to the generated WIT file
     */
    public Path generateWit() {
        ScannedComponent component = scan();
        ComponentValidator.validateOrThrow(component);

        try {
            return WitEmitter.emitToFile(component, config.getWitOutputDirectory());
        } catch (IOException e) {
            throw new ComponentBuilderException("Failed to write WIT file", e);
        }
    }

    /**
     * Scans and returns the component model without writing files.
     *
     * <p>If {@code scanPackage} is configured, discovers annotated classes in that package.
     * Otherwise, uses the explicitly provided {@code sourceClasses}.
     *
     * @return the scanned component
     */
    public ScannedComponent scan() {
        JavaInterfaceScanner scanner = new JavaInterfaceScanner(config.getClasspathEntries());
        List<String> classNames = resolveClassNames(scanner);
        return scanner.scan(classNames);
    }

    /**
     * Generates glue code for GraalVM Web Image from the scanned component model.
     *
     * @param outputDir the directory to write generated bridge sources
     * @return the scanned component used for generation
     */
    public ScannedComponent generateGlueCode(Path outputDir) {
        ScannedComponent component = scan();
        ComponentValidator.validateOrThrow(component);

        String gluePackage = config.getGluePackageName();
        if (gluePackage == null || gluePackage.isEmpty()) {
            gluePackage = "generated.bridge";
        }

        GlueCodeGenerator generator = new GlueCodeGenerator(gluePackage);
        generator.generate(component, outputDir);
        return component;
    }

    /**
     * Runs the compilation phase: native-image → wasm-tools component new.
     *
     * <p>Requires GraalVM 25 EA+ with native-image and wasm-tools on PATH.
     *
     * @return the compilation result
     */
    public CompilationResult compileComponent() {
        String mainClass = config.getMainClass();
        if (mainClass == null || mainClass.isEmpty()) {
            throw new ComponentBuilderException("mainClass is required for compilation");
        }

        Path wasmOutputDir = config.getWasmOutputDirectory();
        if (wasmOutputDir == null) {
            throw new ComponentBuilderException("wasmOutputDirectory is required for compilation");
        }

        String componentName = config.getComponentName();
        if (componentName == null || componentName.isEmpty()) {
            componentName = "component";
        }

        try {
            Files.createDirectories(wasmOutputDir);
        } catch (IOException e) {
            throw new ComponentBuilderException(
                    "Failed to create WASM output directory: " + wasmOutputDir, e);
        }

        // Build classpath string
        String classpath = config.getClasspathEntries().stream()
                .map(Path::toString)
                .collect(Collectors.joining(System.getProperty("path.separator")));

        // Step 1: Compile Java → WASM via native-image
        NativeImageCompiler compiler = new NativeImageCompiler();
        CompilationResult nativeResult = compiler.compile(
                classpath, mainClass, wasmOutputDir, componentName);

        if (!nativeResult.isSuccess()) {
            return nativeResult;
        }

        // Step 2: Wrap core WASM as component via wasm-tools
        Path coreWasm = nativeResult.getOutputWasm()
                .orElseThrow(() -> new ComponentBuilderException(
                        "native-image succeeded but no WASM output found"));

        Path componentWasm = wasmOutputDir.resolve(componentName + ".component.wasm");
        Path witDir = config.getWitOutputDirectory();

        WasmToolsLinker linker = new WasmToolsLinker();

        // If WIT directory exists, embed WIT first
        if (witDir != null && Files.isDirectory(witDir)) {
            Path embeddedWasm = wasmOutputDir.resolve(componentName + ".embedded.wasm");
            linker.embedWit(coreWasm, witDir, embeddedWasm);
            linker.componentNew(embeddedWasm, componentWasm);
        } else {
            linker.componentNew(coreWasm, componentWasm);
        }

        return CompilationResult.builder()
                .success(true)
                .outputWasm(coreWasm)
                .outputJs(nativeResult.getOutputJs().orElse(null))
                .outputWat(nativeResult.getOutputWat().orElse(null))
                .componentWasm(componentWasm)
                .build();
    }

    /**
     * Runs the full build pipeline: generate WIT → compile → wrap as component.
     *
     * @return the compilation result including the component WASM path
     */
    public CompilationResult build() {
        // Phase 1: generate WIT
        generateWit();

        // Phase 2: compile to component
        return compileComponent();
    }

    private List<String> resolveClassNames(JavaInterfaceScanner scanner) {
        List<String> classNames = new ArrayList<>(config.getSourceClasses());

        // If scanPackage is specified, discover annotated classes from that package
        String scanPackage = config.getScanPackage();
        if (scanPackage != null && !scanPackage.isEmpty()) {
            List<String> discovered;
            if (!config.getClasspathEntries().isEmpty()) {
                ClassLoader classLoader = createClassLoader();
                discovered = PackageScanner.findAnnotatedClasses(
                        scanPackage, config.getClasspathEntries(), classLoader);
            } else {
                discovered = PackageScanner.findAnnotatedClasses(
                        scanPackage, Thread.currentThread().getContextClassLoader());
            }
            classNames.addAll(discovered);
        }

        if (classNames.isEmpty()) {
            throw new ComponentBuilderException(
                    "No source classes specified and no annotated classes found" +
                            (scanPackage != null ? " in package '" + scanPackage + "'" : ""));
        }

        return classNames;
    }

    private ClassLoader createClassLoader() {
        java.net.URL[] urls = config.getClasspathEntries().stream()
                .map(path -> {
                    try {
                        return path.toUri().toURL();
                    } catch (Exception e) {
                        throw new ComponentBuilderException(
                                "Invalid classpath entry: " + path, e);
                    }
                })
                .toArray(java.net.URL[]::new);
        return new java.net.URLClassLoader(urls, Thread.currentThread().getContextClassLoader());
    }
}
