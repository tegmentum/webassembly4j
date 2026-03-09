package ai.tegmentum.webassembly4j.api;

import ai.tegmentum.webassembly4j.api.exception.WebAssemblyException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

public interface Engine extends AutoCloseable {

    EngineInfo info();

    EngineCapabilities capabilities();

    Module loadModule(byte[] bytes);

    /**
     * Loads a WebAssembly module from a file path.
     *
     * @param path the path to the WASM file
     * @return the loaded module
     * @throws WebAssemblyException if loading or compilation fails
     */
    default Module loadModule(Path path) {
        try {
            return loadModule(Files.readAllBytes(path));
        } catch (IOException e) {
            throw new WebAssemblyException("Failed to read WASM file: " + path, e);
        }
    }

    /**
     * Loads a WebAssembly module from an input stream.
     * The stream is read fully but not closed by this method.
     *
     * @param stream the input stream containing WASM bytes
     * @return the loaded module
     * @throws WebAssemblyException if loading or compilation fails
     */
    default Module loadModule(InputStream stream) {
        try {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = stream.read(buf)) != -1) {
                buffer.write(buf, 0, n);
            }
            return loadModule(buffer.toByteArray());
        } catch (IOException e) {
            throw new WebAssemblyException("Failed to read WASM bytes from stream", e);
        }
    }

    /**
     * Loads a WebAssembly module from a URL.
     *
     * @param url the URL pointing to WASM bytes
     * @return the loaded module
     * @throws WebAssemblyException if loading or compilation fails
     */
    default Module loadModule(URL url) {
        try (InputStream stream = url.openStream()) {
            return loadModule(stream);
        } catch (IOException e) {
            throw new WebAssemblyException("Failed to read WASM bytes from URL: " + url, e);
        }
    }

    Component loadComponent(byte[] bytes);

    <T> Optional<T> extension(Class<T> extensionType);

    <T> Optional<T> unwrap(Class<T> nativeType);

    @Override
    void close();
}
