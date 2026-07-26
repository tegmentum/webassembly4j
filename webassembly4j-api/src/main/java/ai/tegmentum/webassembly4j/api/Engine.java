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
     * Loads a WebAssembly module using bytes, sharing the underlying store with
     * the given related instance. The returned module can be instantiated with
     * a {@link LinkingContext} whose extern-imports reference exports of the
     * related instance without triggering store-mismatch failures at the
     * provider layer.
     *
     * <p>Use when the returned module needs to import memory, table, global,
     * or function objects that were created in {@code shareStoreWith}'s store
     * — e.g. JIT-emitted-module install, host-plus-plugin composition,
     * incremental linking scenarios. See spec at
     * {@code doctrine/specs/f-webassembly4j-cross-module-store-sharing-charter-2026-07-26.md}.
     *
     * <p>Providers that do not support cross-module store sharing throw
     * {@link UnsupportedOperationException} by default. Consumers may probe via
     * try/catch or via {@link EngineCapabilities}.
     *
     * @param bytes the module bytes
     * @param shareStoreWith an existing instance whose store the new module
     *                       should share; must be an {@link Instance} produced
     *                       by this same engine
     * @return the loaded module bound to {@code shareStoreWith}'s store
     */
    default Module loadModule(byte[] bytes, Instance shareStoreWith) {
        throw new UnsupportedOperationException(
                "Engine.loadModule(byte[], Instance) not implemented by this provider");
    }

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
