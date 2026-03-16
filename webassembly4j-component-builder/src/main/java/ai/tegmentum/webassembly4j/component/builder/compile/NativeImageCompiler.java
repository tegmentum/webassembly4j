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
 * Invokes GraalVM native-image with {@code --tool:svm-wasm} to compile Java to WASM.
 *
 * <p>Requires GraalVM 25 EA+ and Binaryen v119+.
 */
public final class NativeImageCompiler {

    private final ExternalTool nativeImage;

    public NativeImageCompiler() {
        this.nativeImage = ToolResolver.resolveNativeImage()
                .orElseThrow(() -> new ComponentBuilderException(
                        "native-image not found. Set GRAALVM_HOME or add native-image to PATH. " +
                                "Requires GraalVM 25 EA+."));
    }

    public NativeImageCompiler(ExternalTool nativeImage) {
        this.nativeImage = nativeImage;
    }

    /**
     * Compiles Java classes to WASM using GraalVM Web Image.
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
        command.add(nativeImage.getExecutablePath().toString());
        command.add("--tool:svm-wasm");
        command.add("-H:-AutoRunVM");
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
