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
package ai.tegmentum.webassembly4j.component.builder.it;

import ai.tegmentum.webassembly4j.component.builder.ComponentBuildPipeline;
import ai.tegmentum.webassembly4j.component.builder.ComponentBuilderConfig;
import ai.tegmentum.webassembly4j.component.builder.it.fixtures.Canvas;
import ai.tegmentum.webassembly4j.component.builder.it.fixtures.Color;
import ai.tegmentum.webassembly4j.component.builder.it.fixtures.Greeter;
import ai.tegmentum.webassembly4j.component.builder.it.fixtures.GreeterComponent;
import ai.tegmentum.webassembly4j.component.builder.it.fixtures.Logger;
import ai.tegmentum.webassembly4j.component.builder.it.fixtures.Point;
import ai.tegmentum.webassembly4j.component.builder.it.fixtures.math.MathComponent;
import ai.tegmentum.webassembly4j.component.builder.scan.ScannedComponent;
import ai.tegmentum.webassembly4j.component.builder.wit.WitEmitter;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end integration tests for WIT generation from annotated Java classes.
 *
 * <p>These tests use realistic fixture classes to verify the full scan → emit pipeline.
 * They do not require external tools (GraalVM, wasm-tools) and run as part of the
 * standard failsafe integration-test phase.
 */
class WitGenerationIT {

    @Test
    void greeterComponentGeneratesValidWit(@TempDir Path tempDir) throws IOException {
        ComponentBuilderConfig config = ComponentBuilderConfig.builder()
                .sourceClasses(Arrays.asList(
                        GreeterComponent.class.getName(),
                        Greeter.class.getName(),
                        Logger.class.getName()))
                .witOutputDirectory(tempDir)
                .build();

        ComponentBuildPipeline pipeline = new ComponentBuildPipeline(config);
        Path witFile = pipeline.generateWit();

        assertTrue(Files.exists(witFile));
        assertEquals("greeter-world.wit", witFile.getFileName().toString());

        String content = Files.readString(witFile);

        // Package declaration
        assertTrue(content.contains("package example:greeter@0.1.0;"),
                "Missing package declaration in:\n" + content);

        // Export interface with functions
        assertTrue(content.contains("interface greeter {"),
                "Missing greeter interface in:\n" + content);
        assertTrue(content.contains("greet: func("),
                "Missing greet function in:\n" + content);
        assertTrue(content.contains(") -> string;"),
                "Missing string return type in:\n" + content);
        assertTrue(content.contains("log: func("),
                "Missing log function in:\n" + content);

        // Import interface
        assertTrue(content.contains("interface logger {"),
                "Missing logger interface in:\n" + content);
        assertTrue(content.contains("info: func("),
                "Missing info function in:\n" + content);
        assertTrue(content.contains("error: func("),
                "Missing error function in:\n" + content);

        // World with imports and exports
        assertTrue(content.contains("world greeter-world {"),
                "Missing world declaration in:\n" + content);
        assertTrue(content.contains("import logger;"),
                "Missing import in world in:\n" + content);
        assertTrue(content.contains("export greeter;"),
                "Missing export in world in:\n" + content);
    }

    @Test
    void mathComponentWithInnerInterface(@TempDir Path tempDir) throws IOException {
        ComponentBuilderConfig config = ComponentBuilderConfig.builder()
                .sourceClasses(List.of(MathComponent.class.getName()))
                .witOutputDirectory(tempDir)
                .build();

        ComponentBuildPipeline pipeline = new ComponentBuildPipeline(config);
        Path witFile = pipeline.generateWit();

        String content = Files.readString(witFile);

        assertTrue(content.contains("package test:math@1.0.0;"));
        assertTrue(content.contains("world calculator {"));
        assertTrue(content.contains("interface calculator {"));
        assertTrue(content.contains("add: func("));
        assertTrue(content.contains("multiply: func("));
        assertTrue(content.contains("divide: func("));
        assertTrue(content.contains(") -> float64;"));
        assertTrue(content.contains("export calculator;"));
    }

    @Test
    void scanProducesCorrectModel() {
        ComponentBuilderConfig config = ComponentBuilderConfig.builder()
                .sourceClasses(Arrays.asList(
                        GreeterComponent.class.getName(),
                        Greeter.class.getName(),
                        Logger.class.getName()))
                .witOutputDirectory(Path.of("/tmp"))
                .build();

        ComponentBuildPipeline pipeline = new ComponentBuildPipeline(config);
        ScannedComponent component = pipeline.scan();

        assertEquals("example:greeter", component.getPackageName());
        assertEquals("0.1.0", component.getVersion());
        assertEquals("greeter-world", component.getWorldName());
        assertEquals(1, component.getExports().size());
        assertEquals(1, component.getImports().size());
        assertEquals("greeter", component.getExports().get(0).getWitName());
        assertEquals("logger", component.getImports().get(0).getWitName());

        // Greeter should have 2 functions
        assertEquals(2, component.getExports().get(0).getFunctions().size());

        // Logger should have 2 functions
        assertEquals(2, component.getImports().get(0).getFunctions().size());
    }

    @Test
    void dryRunEmitsWithoutWriting() {
        ComponentBuilderConfig config = ComponentBuilderConfig.builder()
                .sourceClasses(Arrays.asList(
                        GreeterComponent.class.getName(),
                        Greeter.class.getName()))
                .witOutputDirectory(Path.of("/tmp"))
                .build();

        ComponentBuildPipeline pipeline = new ComponentBuildPipeline(config);
        ScannedComponent component = pipeline.scan();
        String wit = WitEmitter.emit(component);

        // Valid WIT should have basic structure
        assertTrue(wit.contains("package "));
        assertTrue(wit.contains("interface "));
        assertTrue(wit.contains("world "));
        assertTrue(wit.contains("func("));
    }

    @Test
    void glueCodeGenerationEndToEnd(@TempDir Path tempDir) {
        ComponentBuilderConfig config = ComponentBuilderConfig.builder()
                .sourceClasses(Arrays.asList(
                        GreeterComponent.class.getName(),
                        Greeter.class.getName()))
                .witOutputDirectory(tempDir)
                .gluePackageName("example.greeter.generated")
                .build();

        ComponentBuildPipeline pipeline = new ComponentBuildPipeline(config);
        ScannedComponent component = pipeline.generateGlueCode(tempDir);

        // Bridge file should exist
        Path bridgePath = tempDir.resolve(
                "example/greeter/generated/bridge/GreeterBridge.java");
        assertTrue(Files.exists(bridgePath),
                "Expected bridge file at: " + bridgePath);

        // Main bridge should exist
        Path mainPath = tempDir.resolve(
                "example/greeter/generated/bridge/GreeterWorldMain.java");
        assertTrue(Files.exists(mainPath),
                "Expected main file at: " + mainPath);
    }

    @Test
    void fullWitAndGlueCodePipeline(@TempDir Path tempDir) throws IOException {
        Path witDir = tempDir.resolve("wit");
        Path glueDir = tempDir.resolve("glue");

        // Step 1: Generate WIT
        ComponentBuilderConfig witConfig = ComponentBuilderConfig.builder()
                .sourceClasses(Arrays.asList(
                        GreeterComponent.class.getName(),
                        Greeter.class.getName(),
                        Logger.class.getName()))
                .witOutputDirectory(witDir)
                .gluePackageName("example.greeter.bridge")
                .build();

        ComponentBuildPipeline pipeline = new ComponentBuildPipeline(witConfig);
        Path witFile = pipeline.generateWit();
        assertTrue(Files.exists(witFile));

        // Step 2: Generate glue code
        ScannedComponent component = pipeline.generateGlueCode(glueDir);

        // Verify WIT content
        String witContent = Files.readString(witFile);
        assertTrue(witContent.contains("package example:greeter@0.1.0;"));

        // Verify glue code content
        Path bridgePath = glueDir.resolve(
                "example/greeter/bridge/bridge/GreeterBridge.java");
        assertTrue(Files.exists(bridgePath));

        String bridgeContent = Files.readString(bridgePath);
        assertTrue(bridgeContent.contains("@JS.Export"));
        assertTrue(bridgeContent.contains("greet"));
        assertTrue(bridgeContent.contains("log"));
    }

    @Test
    void canvasWithInlineTypesGeneratesValidWit(@TempDir Path tempDir) throws IOException {
        ComponentBuilderConfig config = ComponentBuilderConfig.builder()
                .sourceClasses(Arrays.asList(
                        GreeterComponent.class.getName(),
                        Canvas.class.getName(),
                        Point.class.getName(),
                        Color.class.getName()))
                .witOutputDirectory(tempDir)
                .build();

        ComponentBuildPipeline pipeline = new ComponentBuildPipeline(config);
        Path witFile = pipeline.generateWit();

        String content = Files.readString(witFile);

        // Canvas interface should have inline type definitions
        assertTrue(content.contains("interface canvas {"),
                "Should have canvas interface in:\n" + content);

        // Record and enum types should be defined inline within the interface
        assertTrue(content.contains("    record point {"),
                "Point record should be inline in canvas interface in:\n" + content);
        assertTrue(content.contains("        x: float32,"),
                "Point.x field should be in record in:\n" + content);
        assertTrue(content.contains("        y: float32,"),
                "Point.y field should be in record in:\n" + content);
        assertTrue(content.contains("    enum color {"),
                "Color enum should be inline in canvas interface in:\n" + content);

        // Functions that reference these types
        assertTrue(content.contains("draw-point: func("),
                "Should have draw-point function in:\n" + content);
        assertTrue(content.contains("get-origin: func()"),
                "Should have get-origin function in:\n" + content);
    }
}
