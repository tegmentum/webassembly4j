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
 * Wraps core WASM modules into the component model format using {@code wasm-tools}.
 */
public final class WasmToolsLinker {

    private final ExternalTool wasmTools;

    public WasmToolsLinker() {
        this.wasmTools = ToolResolver.resolveWasmTools()
                .orElseThrow(() -> new ComponentBuilderException(
                        "wasm-tools not found. Set WASM_TOOLS_HOME or add wasm-tools to PATH."));
    }

    public WasmToolsLinker(ExternalTool wasmTools) {
        this.wasmTools = wasmTools;
    }

    /**
     * Embeds WIT metadata into a core WASM module.
     *
     * @param coreWasm the core WASM module
     * @param witDir the directory containing WIT files
     * @param outputWasm the output path
     */
    public void embedWit(Path coreWasm, Path witDir, Path outputWasm) {
        List<String> command = new ArrayList<>();
        command.add(wasmTools.getExecutablePath().toString());
        command.add("component");
        command.add("embed");
        command.add("--wit");
        command.add(witDir.toString());
        command.add(coreWasm.toString());
        command.add("-o");
        command.add(outputWasm.toString());

        ProcessRunner.Result result = ProcessRunner.run(command, null);
        if (!result.isSuccess()) {
            throw new ComponentBuilderException(
                    "wasm-tools embed failed: " + result.getStderr());
        }
    }

    /**
     * Wraps a core WASM module as a component.
     *
     * @param coreWasm the core WASM module (optionally with embedded WIT)
     * @param componentWasm the output component path
     */
    public void componentNew(Path coreWasm, Path componentWasm) {
        List<String> command = new ArrayList<>();
        command.add(wasmTools.getExecutablePath().toString());
        command.add("component");
        command.add("new");
        command.add(coreWasm.toString());
        command.add("-o");
        command.add(componentWasm.toString());

        ProcessRunner.Result result = ProcessRunner.run(command, null);
        if (!result.isSuccess()) {
            throw new ComponentBuilderException(
                    "wasm-tools component new failed: " + result.getStderr());
        }
    }
}
