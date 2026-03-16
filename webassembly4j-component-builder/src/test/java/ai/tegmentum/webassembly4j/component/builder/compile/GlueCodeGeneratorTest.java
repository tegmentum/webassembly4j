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
package ai.tegmentum.webassembly4j.component.builder.compile;

import ai.tegmentum.webassembly4j.component.builder.scan.ScannedComponent;
import ai.tegmentum.webassembly4j.component.builder.scan.ScannedFunction;
import ai.tegmentum.webassembly4j.component.builder.scan.ScannedInterface;
import ai.tegmentum.webassembly4j.component.builder.scan.ScannedType;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GlueCodeGeneratorTest {

    @Test
    void generatesBridgeClassForExport(@TempDir Path tempDir) throws IOException {
        Map<String, ScannedType> params = new LinkedHashMap<>();
        params.put("name", ScannedType.primitive("string", "String"));

        ScannedFunction greet = new ScannedFunction(
                "greet", "greet", params,
                ScannedType.primitive("string", "String"));

        ScannedInterface greeter = new ScannedInterface(
                "Greeter", "greeter", true,
                Collections.singletonList(greet), Collections.emptyList());

        ScannedComponent component = new ScannedComponent(
                "myorg:mypackage", "0.1.0", "my-component",
                Collections.singletonList(greeter));

        GlueCodeGenerator generator = new GlueCodeGenerator("com.example");
        List<GlueCodeGenerator.GeneratedGlueFile> files = generator.generate(component, tempDir);

        // Should generate bridge class + main class
        assertEquals(2, files.size());

        // Check bridge file exists
        GlueCodeGenerator.GeneratedGlueFile bridgeFile = files.stream()
                .filter(f -> f.getQualifiedName().contains("GreeterBridge"))
                .findFirst()
                .orElseThrow();

        Path bridgePath = tempDir.resolve("com/example/bridge/GreeterBridge.java");
        assertTrue(Files.exists(bridgePath));

        String content = Files.readString(bridgePath);
        assertTrue(content.contains("class GreeterBridge"));
        assertTrue(content.contains("@JS.Export"), "Expected @JS.Export annotation, got:\n" + content);
        assertTrue(content.contains("static String greet(String name)"));
        assertTrue(content.contains("return impl.greet(name)"));
        assertTrue(content.contains("setImplementation"));
    }

    @Test
    void generatesMainBridgeClass(@TempDir Path tempDir) throws IOException {
        ScannedInterface greeter = new ScannedInterface(
                "Greeter", "greeter", true,
                Collections.emptyList(), Collections.emptyList());

        ScannedComponent component = new ScannedComponent(
                "myorg:mypackage", "0.1.0", "my-component",
                Collections.singletonList(greeter));

        GlueCodeGenerator generator = new GlueCodeGenerator("com.example");
        List<GlueCodeGenerator.GeneratedGlueFile> files = generator.generate(component, tempDir);

        GlueCodeGenerator.GeneratedGlueFile mainFile = files.stream()
                .filter(f -> f.getQualifiedName().contains("Main"))
                .findFirst()
                .orElseThrow();

        Path mainPath = tempDir.resolve("com/example/bridge/MyComponentMain.java");
        assertTrue(Files.exists(mainPath));

        String content = Files.readString(mainPath);
        assertTrue(content.contains("class MyComponentMain"));
        assertTrue(content.contains("main(String[] args)"));
    }

    @Test
    void generatesBridgeWithVoidReturnAndMultipleParams(@TempDir Path tempDir) throws IOException {
        Map<String, ScannedType> params = new LinkedHashMap<>();
        params.put("x", ScannedType.primitive("s32", "int"));
        params.put("y", ScannedType.primitive("s32", "int"));

        ScannedFunction drawFunc = new ScannedFunction(
                "draw", "draw-point", params, null);

        ScannedInterface canvas = new ScannedInterface(
                "Canvas", "canvas", true,
                Collections.singletonList(drawFunc), Collections.emptyList());

        ScannedComponent component = new ScannedComponent(
                "test:pkg", null, "test-world",
                Collections.singletonList(canvas));

        GlueCodeGenerator generator = new GlueCodeGenerator("com.test");
        List<GlueCodeGenerator.GeneratedGlueFile> files = generator.generate(component, tempDir);

        Path bridgePath = tempDir.resolve("com/test/bridge/CanvasBridge.java");
        assertTrue(Files.exists(bridgePath));

        String content = Files.readString(bridgePath);
        assertTrue(content.contains("static void drawPoint(int x, int y)"));
        assertTrue(content.contains("impl.drawPoint(x, y)"));
    }

    @Test
    void noFilesForImportsOnly(@TempDir Path tempDir) {
        ScannedInterface imp = new ScannedInterface(
                "Logger", "logger", false,
                Collections.emptyList(), Collections.emptyList());

        ScannedComponent component = new ScannedComponent(
                "test:pkg", null, "test-world",
                Collections.singletonList(imp));

        GlueCodeGenerator generator = new GlueCodeGenerator("com.test");
        List<GlueCodeGenerator.GeneratedGlueFile> files = generator.generate(component, tempDir);

        assertTrue(files.isEmpty());
    }

    @Test
    void generatedGlueFileProperties() {
        GlueCodeGenerator.GeneratedGlueFile file = new GlueCodeGenerator.GeneratedGlueFile(
                "com.example.Bridge", Path.of("/out/Bridge.java"));

        assertEquals("com.example.Bridge", file.getQualifiedName());
        assertEquals(Path.of("/out/Bridge.java"), file.getFilePath());
    }
}
