package ai.tegmentum.webassembly4j.provider.wasmtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ai.tegmentum.wasmtime4j.wit.WitOwn;
import ai.tegmentum.webassembly4j.api.Component;
import ai.tegmentum.webassembly4j.api.ComponentInstance;
import ai.tegmentum.webassembly4j.api.DefaultLinkingContext;
import ai.tegmentum.webassembly4j.api.DefaultWasiContext;
import ai.tegmentum.webassembly4j.api.Engine;
import ai.tegmentum.webassembly4j.api.WitCallableResource;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.api.io.TempDir;

/**
 * End-to-end verification for the {@link WitCallableResource} surface — the deferred half of
 * Follow-up 2. Exercises the full resource lifecycle through the wasmtime4j provider using a
 * synthetic counter component (see {@code src/test/rust/counter_component/}):
 *
 * <ul>
 *   <li>constructor invocation → wasmtime {@link WitOwn}
 *   <li>{@link ComponentInstance#asCallableResource(Object)} → {@link WitCallableResource}
 *   <li>method invocation on the callable (state accumulates across calls)
 *   <li>{@link WitCallableResource#close()} idempotency + post-close rejection
 *   <li>try-with-resources triggers a drop
 * </ul>
 *
 * <p>Drop timing (the exact hazard Follow-up 2 flagged) is verified via a file marker the Rust
 * guest writes into a preopened directory from both its constructor and its Drop impl — Java
 * cannot capture WASI stderr through a {@code System.setErr} swap because WASI writes bypass the
 * Java PrintStream and land on FD 2 directly.
 */
class WasmtimeCallableResourceTest {

    private static final String COUNTER_INTERFACE =
            "tegmentum:test-counter/counter-api@0.1.0";
    private static final String CTOR_EXPORT = COUNTER_INTERFACE + "#[constructor]counter";
    private static final String INCREMENT_METHOD =
            COUNTER_INTERFACE + "#[method]counter.increment";
    private static final String VALUE_METHOD = COUNTER_INTERFACE + "#[method]counter.value";

    static boolean runtimeAvailable() {
        return new WasmtimeProvider().availability().available();
    }

    private static byte[] loadCounterComponent() throws IOException {
        try (InputStream is =
                WasmtimeCallableResourceTest.class.getResourceAsStream("/counter_component.wasm")) {
            assertNotNull(
                    is,
                    "counter_component.wasm not on classpath — run "
                            + "`cargo component build --release --target wasm32-wasip2` in "
                            + "src/test/rust/counter_component/ and copy the artifact into "
                            + "src/test/resources/ (the Maven build does this automatically).");
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            byte[] chunk = new byte[8192];
            int n;
            while ((n = is.read(chunk)) != -1) {
                buf.write(chunk, 0, n);
            }
            return buf.toByteArray();
        }
    }

    /**
     * A component built with cargo-component (any wit-bindgen ≥ 0.30) pulls in {@code wasi:cli} +
     * {@code wasi:io} imports as its ambient environment because {@code eprintln!} requires
     * stdout/stderr, and cargo-component's proxy adapter also pulls {@code wasi:filesystem}. The
     * linking context therefore always includes a WASI context — an empty one is enough for the
     * pure lifecycle tests since we never touch the filesystem or network. Tests that verify Drop
     * pass a preopen so the guest can leave a file marker.
     */
    private static DefaultLinkingContext wasiLinking() {
        return DefaultLinkingContext.builder()
                .wasiContext(DefaultWasiContext.builder().build())
                .build();
    }

    private static DefaultLinkingContext wasiLinkingWithLifecycleDir(final Path hostDir) {
        return DefaultLinkingContext.builder()
                .wasiContext(
                        DefaultWasiContext.builder()
                                .inheritStderr(true)
                                .preopenDir(hostDir.toString(), "/lifecycle", true)
                                .build())
                .build();
    }

    @Test
    @EnabledIf("runtimeAvailable")
    @DisplayName("constructor → invokeMethod × 3 → value returns 3 → close is idempotent")
    void resourceLifecycleAcrossInterfaceScopedMethods() throws Exception {
        byte[] wasm = loadCounterComponent();
        try (Engine engine = WasmtimeEngineAdapter.create(null);
             Component component = engine.loadComponent(wasm)) {
            ComponentInstance instance = component.instantiate(wasiLinking());

            Object rawHandle = instance.invokeWit(CTOR_EXPORT);
            assertNotNull(rawHandle, "constructor should return an own<counter> handle");
            // The wasmtime WIT deserializer surfaces `own<T>` as WitOwn (discriminator 22),
            // not WitResource. WasmtimeComponentInstanceAdapter#asCallableResource accepts
            // both — testing this here makes that contract explicit.
            assertTrue(
                    rawHandle instanceof WitOwn,
                    "wasmtime provider should hand back a WitOwn for own<counter>; got "
                            + rawHandle.getClass().getName());

            try (WitCallableResource counter = instance.asCallableResource(rawHandle)) {
                assertFalse(counter.isClosed(), "freshly-wrapped resource must be open");

                for (int i = 0; i < 3; i++) {
                    Object result = counter.invokeMethod(INCREMENT_METHOD);
                    assertEquals(
                            null,
                            result,
                            "increment is void; expected null from invokeMethod, got " + result);
                }

                Object value = counter.invokeMethod(VALUE_METHOD);
                assertEquals(
                        3,
                        ((Number) value).intValue(),
                        "value must be 3 after three increments — proves receiver + method "
                                + "dispatch actually landed on the same resource across calls");

                counter.close();
                assertTrue(counter.isClosed(), "close() must flip isClosed");
                // Second close is a no-op per the WitCallableResource contract — asserting it
                // doesn't blow up covers the try-with-resources double-close path too.
                counter.close();
            }
        }
    }

    @Test
    @EnabledIf("runtimeAvailable")
    @DisplayName("closed resource: further invokeMethod throws IllegalStateException")
    void invokingAfterCloseThrows() throws Exception {
        byte[] wasm = loadCounterComponent();
        try (Engine engine = WasmtimeEngineAdapter.create(null);
             Component component = engine.loadComponent(wasm)) {
            ComponentInstance instance = component.instantiate(wasiLinking());

            Object rawHandle = instance.invokeWit(CTOR_EXPORT);
            WitCallableResource counter = instance.asCallableResource(rawHandle);
            counter.close();

            assertTrue(counter.isClosed());
            // The interface contract: post-close invokeMethod must throw (either explicit
            // "resource has been closed" or an execution exception once the native handle is
            // gone). Both are honest failure modes — the resource is unusable either way.
            assertThrows(RuntimeException.class, () -> counter.invokeMethod(INCREMENT_METHOD));
            assertThrows(RuntimeException.class, () -> counter.invokeMethod(VALUE_METHOD));
        }
    }

    @Test
    @EnabledIf("runtimeAvailable")
    @DisplayName("try-with-resources close observably reaches the guest's Drop impl")
    void closeInvokesGuestSideDrop(@TempDir Path lifecycleDir) throws Exception {
        byte[] wasm = loadCounterComponent();

        // The Rust guest writes a lifecycle-marker file to a preopened directory in both the
        // constructor and its Drop impl. WASI stderr is also inherited so the JVM's console
        // still shows the diagnostic lines, but we don't assert on stderr — Java's
        // `System.setErr` only replaces the PrintStream, not the process's file descriptor 2,
        // so WASI writes bypass any Java-side redirect. The file marker is the durable signal
        // that survives the FD-2 bypass and proves Drop actually landed on the guest — which
        // is the exact concern Follow-up 2 called out.
        try (Engine engine = WasmtimeEngineAdapter.create(null);
             Component component = engine.loadComponent(wasm)) {
            ComponentInstance instance =
                    component.instantiate(wasiLinkingWithLifecycleDir(lifecycleDir));

            Object rawHandle = instance.invokeWit(CTOR_EXPORT);
            try (WitCallableResource counter = instance.asCallableResource(rawHandle)) {
                counter.invokeMethod(INCREMENT_METHOD);
                counter.invokeMethod(INCREMENT_METHOD);
                // Leaving the block triggers close() → dropResource → guest Drop.
            }
        }

        Path marker = lifecycleDir.resolve("marker.log");
        String directoryContents;
        try (Stream<Path> entries = Files.list(lifecycleDir)) {
            directoryContents = entries.map(Path::toString)
                    .reduce((a, b) -> a + ", " + b)
                    .orElse("(empty)");
        }
        assertTrue(
                Files.exists(marker),
                "guest should have written a lifecycle marker at " + marker
                        + "; directory contents: " + directoryContents);
        String contents = Files.readString(marker, StandardCharsets.UTF_8);
        // Constructor line proves the WASI preopen wiring works at all; drop line proves the
        // close on the callable resource actually reached the guest's Drop.
        assertTrue(
                contents.contains("constructor=0"),
                "marker file should record the constructor call; got:\n" + contents);
        assertTrue(
                contents.contains("drop=2"),
                "marker file should record the Drop call with final value = 2 "
                        + "(after two increments); got:\n" + contents);
    }
}
