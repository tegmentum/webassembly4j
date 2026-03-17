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

import ai.tegmentum.webassembly4j.component.builder.ComponentBuilderException;
import ai.tegmentum.webassembly4j.component.builder.tool.ExternalTool;
import ai.tegmentum.webassembly4j.component.builder.tool.ProcessRunner;
import ai.tegmentum.webassembly4j.component.builder.tool.ToolResolver;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Invokes GraalVM web-image to compile Java to standalone WasmGC.
 *
 * <p>Uses the tegmentum/graal fork with {@code -H:+StandaloneWasm} support.
 * The tool is resolved via {@code GRAALVM_HOME}, PATH, or auto-downloaded.
 */
public final class NativeImageCompiler {

    private final ExternalTool webImage;

    /**
     * Creates a compiler, auto-downloading GraalVM if needed.
     */
    public NativeImageCompiler() {
        this(true);
    }

    /**
     * Creates a compiler with optional auto-download.
     *
     * @param autoDownload whether to download GraalVM if not found locally
     */
    public NativeImageCompiler(boolean autoDownload) {
        this.webImage = ToolResolver.resolveWebImage(autoDownload)
                .orElseThrow(() -> new ComponentBuilderException(
                        "web-image not found. Set GRAALVM_HOME or add web-image to PATH. " +
                                "The tool is available from https://github.com/tegmentum/graal/releases"));
    }

    public NativeImageCompiler(ExternalTool webImage) {
        this.webImage = webImage;
    }

    /**
     * Returns the resolved web-image tool.
     */
    public ExternalTool getTool() {
        return webImage;
    }

    /**
     * Compiles Java classes to standalone WasmGC.
     *
     * @param classpath the classpath entries joined by the path separator
     * @param mainClass the main class to compile
     * @param outputDir the output directory
     * @param name the output file name (without extension)
     * @return the compilation result
     */
    public CompilationResult compile(String classpath, String mainClass,
                                     Path outputDir, String name) {
        List<String> command = new ArrayList<>();
        command.add(webImage.getExecutablePath().toString());
        command.add("-H:+StandaloneWasm");
        command.add("-H:Backend=WASMGC");
        command.add("-H:-AutoRunVM");
        command.add("-H:+UseBinaryen");
        command.add("-cp");
        command.add(classpath);
        command.add("-o");
        command.add(outputDir.resolve(name).toString());
        command.add(mainClass);

        ProcessRunner.Result result = ProcessRunner.run(command, null, 600);

        if (result.isSuccess()) {
            return CompilationResult.builder()
                    .success(true)
                    .outputWasm(outputDir.resolve(name + ".js.wasm"))
                    .outputJs(outputDir.resolve(name + ".js"))
                    .outputWat(outputDir.resolve(name + ".js.wat"))
                    .build();
        } else {
            return CompilationResult.builder()
                    .success(false)
                    .errorMessage(result.getStderr())
                    .build();
        }
    }
}
