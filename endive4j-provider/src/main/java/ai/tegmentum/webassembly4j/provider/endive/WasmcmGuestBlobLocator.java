package ai.tegmentum.webassembly4j.provider.endive;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Locates the {@code wasmcm_runtime_guest.wasm} blob — the portable Rust Component
 * Model runtime cross-compiled to {@code wasm32-unknown-unknown} — that the Endive
 * provider layers on top of Endive to obtain a real Component Model surface.
 *
 * <p>Lookup order:
 * <ol>
 *   <li>System property {@code wasmcm.runtime.guest.wasm} — absolute path to the {@code .wasm}.</li>
 *   <li>Environment variable {@code WASMCM_RUNTIME_GUEST_WASM} — absolute path to the {@code .wasm}.</li>
 *   <li>Environment variable {@code WASMCM_HOME} — the guest is resolved to
 *       {@code $WASMCM_HOME/target/wasm32-unknown-unknown/release/wasmcm_runtime_guest.wasm}.</li>
 *   <li>Classpath resource {@code wasmcm_runtime_guest.wasm} on the thread-context class loader
 *       (with a fallback to this class's loader).</li>
 *   <li>Sibling checkout heuristic {@code ~/git/wasm-cm/target/wasm32-unknown-unknown/release/wasmcm_runtime_guest.wasm}
 *       — convenient for local development when a companion {@code wasm-cm} tree is present.</li>
 * </ol>
 *
 * <p>Returns {@code null} when nothing matches; callers surface a clear error to the user
 * rather than pretending Component Model works with no guest present.
 */
final class WasmcmGuestBlobLocator {

    static final String SYSTEM_PROPERTY = "wasmcm.runtime.guest.wasm";
    static final String ENV_GUEST_WASM = "WASMCM_RUNTIME_GUEST_WASM";
    static final String ENV_HOME = "WASMCM_HOME";
    static final String CLASSPATH_RESOURCE = "wasmcm_runtime_guest.wasm";
    static final String HOME_RELATIVE_GUEST =
            "target/wasm32-unknown-unknown/release/wasmcm_runtime_guest.wasm";

    private WasmcmGuestBlobLocator() {}

    static byte[] locateOrNull() {
        String prop = System.getProperty(SYSTEM_PROPERTY);
        byte[] bytes = readIfRegular(prop);
        if (bytes != null) {
            return bytes;
        }

        String envGuest = System.getenv(ENV_GUEST_WASM);
        bytes = readIfRegular(envGuest);
        if (bytes != null) {
            return bytes;
        }

        String home = System.getenv(ENV_HOME);
        if (home != null && !home.isEmpty()) {
            bytes = readIfRegular(Paths.get(home, HOME_RELATIVE_GUEST).toString());
            if (bytes != null) {
                return bytes;
            }
        }

        bytes = readClasspath(Thread.currentThread().getContextClassLoader());
        if (bytes != null) {
            return bytes;
        }
        bytes = readClasspath(WasmcmGuestBlobLocator.class.getClassLoader());
        if (bytes != null) {
            return bytes;
        }

        String userHome = System.getProperty("user.home");
        if (userHome != null && !userHome.isEmpty()) {
            Path sibling = Paths.get(userHome, "git", "wasm-cm", HOME_RELATIVE_GUEST);
            bytes = readIfRegular(sibling.toString());
            if (bytes != null) {
                return bytes;
            }
        }

        return null;
    }

    /**
     * Human-readable summary of the lookup paths, embedded in error messages when
     * no blob can be found so callers know how to point the provider at one.
     */
    static String describeLookupOrder() {
        return "-D" + SYSTEM_PROPERTY + "=<path>"
                + " | $" + ENV_GUEST_WASM
                + " | $" + ENV_HOME + "/" + HOME_RELATIVE_GUEST
                + " | classpath:" + CLASSPATH_RESOURCE
                + " | ~/git/wasm-cm/" + HOME_RELATIVE_GUEST;
    }

    private static byte[] readIfRegular(String path) {
        if (path == null || path.isEmpty()) {
            return null;
        }
        Path p = Paths.get(path);
        if (!Files.isRegularFile(p)) {
            return null;
        }
        try {
            return Files.readAllBytes(p);
        } catch (IOException e) {
            return null;
        }
    }

    private static byte[] readClasspath(ClassLoader loader) {
        if (loader == null) {
            return null;
        }
        try (InputStream is = loader.getResourceAsStream(CLASSPATH_RESOURCE)) {
            if (is == null) {
                return null;
            }
            return is.readAllBytes();
        } catch (IOException e) {
            return null;
        }
    }
}
