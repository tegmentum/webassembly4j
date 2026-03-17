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
package ai.tegmentum.webassembly4j.component.builder.tool;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Discovers external tool executables on PATH or via environment variables.
 */
public final class ToolResolver {

    private ToolResolver() {}

    /**
     * Resolves the native-image tool from GraalVM.
     *
     * @return the resolved tool, or empty if not found
     */
    public static Optional<ExternalTool> resolveNativeImage() {
        // Check GRAALVM_HOME first
        String graalvmHome = System.getenv("GRAALVM_HOME");
        if (graalvmHome != null) {
            Path nativeImage = Paths.get(graalvmHome, "bin", "native-image");
            if (Files.isExecutable(nativeImage)) {
                String version = getToolVersion(nativeImage, "--version");
                return Optional.of(new ExternalTool("native-image", nativeImage, version));
            }
        }

        // Fall back to PATH
        return resolveOnPath("native-image", "--version");
    }

    /**
     * Resolves the web-image tool from a GraalVM distribution with standalone WASM support.
     * <p>
     * Search order:
     * <ol>
     *   <li>{@code GRAALVM_HOME/bin/web-image}</li>
     *   <li>{@code web-image} on PATH</li>
     *   <li>Auto-download from tegmentum/graal GitHub release</li>
     * </ol>
     *
     * @param autoDownload whether to auto-download if not found locally
     * @return the resolved tool
     */
    public static Optional<ExternalTool> resolveWebImage(boolean autoDownload) {
        // Check GRAALVM_HOME first
        String graalvmHome = System.getenv("GRAALVM_HOME");
        if (graalvmHome != null) {
            Path webImage = Paths.get(graalvmHome, "bin", "web-image");
            if (Files.isExecutable(webImage)) {
                String version = getToolVersion(webImage, "--version");
                return Optional.of(new ExternalTool("web-image", webImage, version));
            }
        }

        // Check PATH
        Optional<ExternalTool> onPath = resolveOnPath("web-image", "--version");
        if (onPath.isPresent()) {
            return onPath;
        }

        // Auto-download
        if (autoDownload) {
            try {
                return Optional.of(GraalVMDistribution.resolveWebImage());
            } catch (Exception e) {
                return Optional.empty();
            }
        }

        return Optional.empty();
    }

    /**
     * Resolves wasm-tools.
     *
     * @return the resolved tool, or empty if not found
     */
    public static Optional<ExternalTool> resolveWasmTools() {
        // Check WASM_TOOLS_HOME
        String wasmToolsHome = System.getenv("WASM_TOOLS_HOME");
        if (wasmToolsHome != null) {
            Path wasmTools = Paths.get(wasmToolsHome, "wasm-tools");
            if (Files.isExecutable(wasmTools)) {
                String version = getToolVersion(wasmTools, "--version");
                return Optional.of(new ExternalTool("wasm-tools", wasmTools, version));
            }
        }

        // Fall back to PATH
        return resolveOnPath("wasm-tools", "--version");
    }

    private static Optional<ExternalTool> resolveOnPath(String name, String versionFlag) {
        String pathEnv = System.getenv("PATH");
        if (pathEnv == null) {
            return Optional.empty();
        }

        for (String dir : pathEnv.split(System.getProperty("path.separator"))) {
            Path candidate = Paths.get(dir, name);
            if (Files.isExecutable(candidate)) {
                String version = getToolVersion(candidate, versionFlag);
                return Optional.of(new ExternalTool(name, candidate, version));
            }
        }

        return Optional.empty();
    }

    static String getToolVersionSafe(Path executable, String versionFlag) {
        return getToolVersion(executable, versionFlag);
    }

    private static String getToolVersion(Path executable, String versionFlag) {
        try {
            List<String> command = Arrays.asList(executable.toString(), versionFlag);
            ProcessRunner.Result result = ProcessRunner.run(command, null, 10);
            if (result.isSuccess()) {
                return result.getStdout().trim();
            }
        } catch (Exception ignored) {
            // Version detection is best-effort
        }
        return null;
    }
}
