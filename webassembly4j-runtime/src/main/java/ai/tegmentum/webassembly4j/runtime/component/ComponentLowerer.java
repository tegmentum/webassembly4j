package ai.tegmentum.webassembly4j.runtime.component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Extracts a core WASM module from a component model binary using
 * {@code wasm-tools component lower}.
 * <p>
 * This enables runtimes that only support core modules (Chicory, WAMR, GraalWasm)
 * to execute component model WASM by lowering it to the canonical ABI core module
 * representation.
 */
public final class ComponentLowerer {

    private ComponentLowerer() {}

    /**
     * Lowers a component to a core module.
     *
     * @param componentBytes the component model WASM binary
     * @return the core module bytes
     * @throws RuntimeException if wasm-tools is not available or lowering fails
     */
    public static byte[] lower(byte[] componentBytes) {
        Path wasmTools = findWasmTools();
        if (wasmTools == null) {
            throw new RuntimeException(
                    "Cannot lower component: wasm-tools not found on PATH or WASM_TOOLS_HOME. " +
                    "Install wasm-tools (https://github.com/bytecodealliance/wasm-tools) or use a " +
                    "provider that supports the component model natively (e.g. wasmtime).");
        }

        Path tempDir = null;
        try {
            tempDir = Files.createTempDirectory("webassembly4j-lower");
            Path componentFile = tempDir.resolve("component.wasm");
            Path coreFile = tempDir.resolve("core.wasm");

            Files.write(componentFile, componentBytes);

            List<String> command = Arrays.asList(
                    wasmTools.toString(), "component", "lower",
                    componentFile.toString(), "-o", coreFile.toString());

            Process process = new ProcessBuilder(command)
                    .redirectErrorStream(true)
                    .start();

            boolean finished = process.waitFor(60, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new RuntimeException("wasm-tools component lower timed out");
            }

            if (process.exitValue() != 0) {
                String output;
                try (InputStream is = process.getInputStream()) {
                    byte[] buf = new byte[4096];
                    int n = is.read(buf);
                    output = n > 0 ? new String(buf, 0, n) : "";
                }
                throw new RuntimeException(
                        "wasm-tools component lower failed (exit " + process.exitValue() + "): " +
                        output);
            }

            return Files.readAllBytes(coreFile);
        } catch (IOException e) {
            throw new RuntimeException("Failed to lower component to core module", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while lowering component", e);
        } finally {
            if (tempDir != null) {
                try {
                    Files.deleteIfExists(tempDir.resolve("component.wasm"));
                    Files.deleteIfExists(tempDir.resolve("core.wasm"));
                    Files.deleteIfExists(tempDir);
                } catch (IOException ignored) {
                }
            }
        }
    }

    private static Path findWasmTools() {
        String home = System.getenv("WASM_TOOLS_HOME");
        if (home != null) {
            Path p = Path.of(home, "wasm-tools");
            if (Files.isExecutable(p)) {
                return p;
            }
            p = Path.of(home);
            if (Files.isExecutable(p) && p.getFileName().toString().startsWith("wasm-tools")) {
                return p;
            }
        }

        String pathEnv = System.getenv("PATH");
        if (pathEnv != null) {
            for (String dir : pathEnv.split(System.getProperty("path.separator"))) {
                Path p = Path.of(dir, "wasm-tools");
                if (Files.isExecutable(p)) {
                    return p;
                }
            }
        }
        return null;
    }
}
