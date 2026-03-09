package ai.tegmentum.webassembly4j.api;

import ai.tegmentum.webassembly4j.api.exception.WebAssemblyException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EngineLoadingTest {

    /**
     * Stub engine that records the bytes passed to loadModule(byte[]).
     */
    private static class StubEngine implements Engine {
        byte[] lastBytes;

        @Override
        public Module loadModule(byte[] bytes) {
            lastBytes = bytes;
            return new StubModule();
        }

        @Override
        public EngineInfo info() { return null; }
        @Override
        public EngineCapabilities capabilities() { return null; }
        @Override
        public Component loadComponent(byte[] bytes) { return null; }
        @Override
        public <T> Optional<T> extension(Class<T> extensionType) {
            return Optional.empty();
        }
        @Override
        public <T> Optional<T> unwrap(Class<T> nativeType) {
            return Optional.empty();
        }
        @Override
        public void close() {}
    }

    private static class StubModule implements Module {
        @Override
        public Instance instantiate() { return null; }
        @Override
        public Instance instantiate(LinkingContext linkingContext) { return null; }
        @Override
        public void close() {}
    }

    @Test
    void loadModuleFromPath(@TempDir Path tempDir) throws IOException {
        byte[] wasmBytes = {0x00, 0x61, 0x73, 0x6D}; // WASM magic
        Path wasmFile = tempDir.resolve("test.wasm");
        Files.write(wasmFile, wasmBytes);

        StubEngine engine = new StubEngine();
        Module module = engine.loadModule(wasmFile);

        assertNotNull(module);
        assertArrayEquals(wasmBytes, engine.lastBytes);
    }

    @Test
    void loadModuleFromInputStream() {
        byte[] wasmBytes = {0x00, 0x61, 0x73, 0x6D, 0x01, 0x00, 0x00, 0x00};

        StubEngine engine = new StubEngine();
        Module module = engine.loadModule(new ByteArrayInputStream(wasmBytes));

        assertNotNull(module);
        assertArrayEquals(wasmBytes, engine.lastBytes);
    }

    @Test
    void loadModuleFromUrl(@TempDir Path tempDir) throws Exception {
        byte[] wasmBytes = {0x00, 0x61, 0x73, 0x6D};
        Path wasmFile = tempDir.resolve("test.wasm");
        Files.write(wasmFile, wasmBytes);

        StubEngine engine = new StubEngine();
        Module module = engine.loadModule(wasmFile.toUri().toURL());

        assertNotNull(module);
        assertArrayEquals(wasmBytes, engine.lastBytes);
    }

    @Test
    void loadModuleFromNonexistentPathThrows() {
        StubEngine engine = new StubEngine();
        assertThrows(WebAssemblyException.class,
                () -> engine.loadModule(Path.of("/nonexistent/path.wasm")));
    }

    @Test
    void loadModuleFromFailingStream() {
        StubEngine engine = new StubEngine();
        InputStream failingStream = new InputStream() {
            @Override
            public int read() throws IOException {
                throw new IOException("Stream failure");
            }
        };
        assertThrows(WebAssemblyException.class,
                () -> engine.loadModule(failingStream));
    }

    @Test
    void loadModuleFromEmptyStream() {
        byte[] empty = new byte[0];
        StubEngine engine = new StubEngine();
        Module module = engine.loadModule(new ByteArrayInputStream(empty));
        assertNotNull(module);
        assertArrayEquals(empty, engine.lastBytes);
    }

    @Test
    void loadModuleFromLargeStream() {
        byte[] large = new byte[100_000];
        for (int i = 0; i < large.length; i++) {
            large[i] = (byte) (i & 0xFF);
        }

        StubEngine engine = new StubEngine();
        Module module = engine.loadModule(new ByteArrayInputStream(large));

        assertNotNull(module);
        assertArrayEquals(large, engine.lastBytes);
    }
}
