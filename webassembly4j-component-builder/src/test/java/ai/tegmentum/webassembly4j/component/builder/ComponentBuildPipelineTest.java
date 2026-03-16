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

import ai.tegmentum.webassembly4j.component.builder.annotation.WitComponent;
import ai.tegmentum.webassembly4j.component.builder.annotation.WitExport;
import ai.tegmentum.webassembly4j.component.builder.annotation.WitWorld;
import ai.tegmentum.webassembly4j.component.builder.scan.ScannedComponent;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ComponentBuildPipelineTest {

    @Test
    void scanReturnsComponent() {
        ComponentBuilderConfig config = ComponentBuilderConfig.builder()
                .sourceClasses(Arrays.asList(
                        TestComponent.class.getName(),
                        TestGreeter.class.getName()))
                .witOutputDirectory(Path.of("/tmp"))
                .build();

        ComponentBuildPipeline pipeline = new ComponentBuildPipeline(config);
        ScannedComponent component = pipeline.scan();

        assertEquals("example:test", component.getPackageName());
        assertEquals("test-component", component.getWorldName());
        assertEquals(1, component.getExports().size());
        assertEquals("test-greeter", component.getExports().get(0).getWitName());
    }

    @Test
    void generateWitCreatesFile(@TempDir Path tempDir) throws IOException {
        ComponentBuilderConfig config = ComponentBuilderConfig.builder()
                .sourceClasses(Arrays.asList(
                        TestComponent.class.getName(),
                        TestGreeter.class.getName()))
                .witOutputDirectory(tempDir)
                .build();

        ComponentBuildPipeline pipeline = new ComponentBuildPipeline(config);
        Path witFile = pipeline.generateWit();

        assertTrue(Files.exists(witFile));
        assertEquals("test-component.wit", witFile.getFileName().toString());

        String content = Files.readString(witFile);
        assertTrue(content.contains("package example:test@1.0.0;"));
        assertTrue(content.contains("interface test-greeter"));
        assertTrue(content.contains("greet: func("));
        assertTrue(content.contains("-> string"));
        assertTrue(content.contains("world test-component"));
        assertTrue(content.contains("export test-greeter"));
    }

    @Test
    void generateGlueCodeCreatesFiles(@TempDir Path tempDir) {
        ComponentBuilderConfig config = ComponentBuilderConfig.builder()
                .sourceClasses(Arrays.asList(
                        TestComponent.class.getName(),
                        TestGreeter.class.getName()))
                .witOutputDirectory(tempDir)
                .gluePackageName("com.example.generated")
                .build();

        ComponentBuildPipeline pipeline = new ComponentBuildPipeline(config);
        ScannedComponent component = pipeline.generateGlueCode(tempDir);

        assertEquals("example:test", component.getPackageName());

        // Bridge files should exist
        Path bridgePath = tempDir.resolve("com/example/generated/bridge/TestGreeterBridge.java");
        assertTrue(Files.exists(bridgePath));

        Path mainPath = tempDir.resolve("com/example/generated/bridge/TestComponentMain.java");
        assertTrue(Files.exists(mainPath));
    }

    @Test
    void compileComponentThrowsWithoutMainClass(@TempDir Path tempDir) {
        ComponentBuilderConfig config = ComponentBuilderConfig.builder()
                .sourceClasses(Collections.singletonList(TestComponent.class.getName()))
                .witOutputDirectory(tempDir)
                .wasmOutputDirectory(tempDir)
                .build();

        ComponentBuildPipeline pipeline = new ComponentBuildPipeline(config);
        assertThrows(ComponentBuilderException.class, pipeline::compileComponent);
    }

    @Test
    void compileComponentThrowsWithoutWasmOutputDir(@TempDir Path tempDir) {
        ComponentBuilderConfig config = ComponentBuilderConfig.builder()
                .sourceClasses(Collections.singletonList(TestComponent.class.getName()))
                .witOutputDirectory(tempDir)
                .mainClass("com.example.Main")
                .build();

        ComponentBuildPipeline pipeline = new ComponentBuildPipeline(config);
        assertThrows(ComponentBuilderException.class, pipeline::compileComponent);
    }

    @Test
    void configBuilderProperties(@TempDir Path tempDir) {
        ComponentBuilderConfig config = ComponentBuilderConfig.builder()
                .sourceClasses(Arrays.asList("a.B", "c.D"))
                .scanPackage("com.example")
                .classpathEntries(Collections.singletonList(tempDir))
                .witOutputDirectory(tempDir.resolve("wit"))
                .wasmOutputDirectory(tempDir.resolve("wasm"))
                .componentName("my-component")
                .mainClass("com.example.Main")
                .gluePackageName("com.example.glue")
                .build();

        assertEquals(Arrays.asList("a.B", "c.D"), config.getSourceClasses());
        assertEquals("com.example", config.getScanPackage());
        assertEquals(Collections.singletonList(tempDir), config.getClasspathEntries());
        assertEquals(tempDir.resolve("wit"), config.getWitOutputDirectory());
        assertEquals(tempDir.resolve("wasm"), config.getWasmOutputDirectory());
        assertEquals("my-component", config.getComponentName());
        assertEquals("com.example.Main", config.getMainClass());
        assertEquals("com.example.glue", config.getGluePackageName());
    }

    // Test fixtures (must be public for scanner to access)

    @WitComponent(packageName = "example:test", version = "1.0.0")
    @WitWorld(name = "test-component")
    public static class TestComponent {}

    @WitExport
    public interface TestGreeter {
        String greet(String name);
    }
}
