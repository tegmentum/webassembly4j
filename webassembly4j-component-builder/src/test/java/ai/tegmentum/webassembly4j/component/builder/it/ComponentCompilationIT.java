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

import ai.tegmentum.webassembly4j.component.builder.compile.CompilationResult;
import ai.tegmentum.webassembly4j.component.builder.compile.NativeImageCompiler;
import ai.tegmentum.webassembly4j.component.builder.compile.WasmToolsLinker;
import ai.tegmentum.webassembly4j.component.builder.tool.ExternalTool;
import ai.tegmentum.webassembly4j.component.builder.tool.ToolResolver;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests for WASM compilation that require external tools.
 *
 * <p>These tests check for tool presence at runtime via {@code @EnabledIf}.
 * They require GraalVM 25 EA+ with native-image and/or wasm-tools on PATH.
 *
 * <p>Set {@code GRAALVM_HOME} to point to GraalVM 25 if native-image is not on PATH.
 */
class ComponentCompilationIT {

    @Test
    @EnabledIf("nativeImageAvailable")
    void nativeImageToolIsResolvable() {
        ExternalTool tool = ToolResolver.resolveNativeImage().orElseThrow();
        assertTrue(tool.getVersion().isPresent(),
                "native-image should report its version");
        assertTrue(tool.getVersion().get().contains("25"),
                "Expected GraalVM 25, got: " + tool.getVersion().get());
    }

    @Test
    @EnabledIf("wasmToolsAvailable")
    void wasmToolsIsResolvable() {
        ExternalTool tool = ToolResolver.resolveWasmTools().orElseThrow();
        assertTrue(tool.getVersion().isPresent(),
                "wasm-tools should report its version");
    }

    @Test
    @EnabledIf("nativeImageAvailable")
    void compileTrivialJavaToWasm(@TempDir Path tempDir) throws IOException {
        // Compile a trivial Java class with a main method
        // First, create and compile the Java source
        Path srcDir = tempDir.resolve("src");
        Files.createDirectories(srcDir);
        Files.writeString(srcDir.resolve("TrivialApp.java"),
                """
                public class TrivialApp {
                    public static int add(int a, int b) {
                        return a + b;
                    }
                    public static void main(String[] args) {
                        System.out.println(add(1, 2));
                    }
                }
                """);

        // Compile Java source to .class
        ExternalTool nativeImage = ToolResolver.resolveNativeImage().orElseThrow();
        Path graalvmHome = nativeImage.getExecutablePath().getParent().getParent();
        Path javac = graalvmHome.resolve("bin/javac");

        var javacResult = ai.tegmentum.webassembly4j.component.builder.tool.ProcessRunner.run(
                java.util.List.of(javac.toString(), "-d", srcDir.toString(),
                        srcDir.resolve("TrivialApp.java").toString()),
                null, 30);
        assertTrue(javacResult.isSuccess(),
                "javac should succeed: " + javacResult.getStderr());

        // Compile to WASM via native-image
        Path outputDir = tempDir.resolve("output");
        Files.createDirectories(outputDir);

        NativeImageCompiler compiler = new NativeImageCompiler(nativeImage);
        CompilationResult result = compiler.compile(
                srcDir.toString(), "TrivialApp", outputDir, "trivial");

        assertTrue(result.isSuccess(),
                "native-image compilation should succeed: " +
                        result.getErrorMessage().orElse("unknown error"));

        // Verify outputs
        assertTrue(result.getOutputWasm().isPresent(),
                "Should produce .wasm output");
        assertTrue(Files.exists(result.getOutputWasm().get()),
                "WASM file should exist at: " + result.getOutputWasm().get());
        assertTrue(Files.size(result.getOutputWasm().get()) > 0,
                "WASM file should not be empty");

        assertTrue(result.getOutputJs().isPresent(),
                "Should produce .js output");
        assertTrue(Files.exists(result.getOutputJs().get()),
                "JS file should exist");

        assertTrue(result.getOutputWat().isPresent(),
                "Should produce .wat output");
        assertTrue(Files.exists(result.getOutputWat().get()),
                "WAT file should exist");
    }

    @Test
    @EnabledIf("wasmToolsAvailable")
    void wasmToolsValidatesModule(@TempDir Path tempDir) throws IOException {
        // Create a minimal valid WASM module (magic + version + empty)
        // Magic: \0asm, Version: 1
        byte[] minimalWasm = new byte[] {
                0x00, 0x61, 0x73, 0x6d, // magic: \0asm
                0x01, 0x00, 0x00, 0x00  // version: 1
        };
        Path wasmFile = tempDir.resolve("minimal.wasm");
        Files.write(wasmFile, minimalWasm);

        // wasm-tools component new should work on a minimal valid module
        ExternalTool wasmTools = ToolResolver.resolveWasmTools().orElseThrow();
        Path componentWasm = tempDir.resolve("minimal.component.wasm");

        WasmToolsLinker linker = new WasmToolsLinker(wasmTools);
        linker.componentNew(wasmFile, componentWasm);

        assertTrue(Files.exists(componentWasm),
                "Component WASM should be created");
        assertTrue(Files.size(componentWasm) > Files.size(wasmFile),
                "Component WASM should be larger than core module (has component wrapper)");
    }

    // Condition methods for @EnabledIf

    static boolean nativeImageAvailable() {
        try {
            return ToolResolver.resolveNativeImage().isPresent();
        } catch (Exception e) {
            return false;
        }
    }

    static boolean wasmToolsAvailable() {
        try {
            return ToolResolver.resolveWasmTools().isPresent();
        } catch (Exception e) {
            return false;
        }
    }
}
