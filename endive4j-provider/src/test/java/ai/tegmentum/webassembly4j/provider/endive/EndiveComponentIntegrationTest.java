package ai.tegmentum.webassembly4j.provider.endive;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import ai.tegmentum.webassembly4j.api.Component;
import ai.tegmentum.webassembly4j.api.ComponentInstance;
import ai.tegmentum.webassembly4j.api.DefaultLinkingContext;
import ai.tegmentum.webassembly4j.api.Engine;
import ai.tegmentum.webassembly4j.api.WitHostFunctionDefinition;
import ai.tegmentum.webassembly4j.api.exception.InstantiationException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.jupiter.api.Test;

/**
 * End-to-end verification of Endive's Component Model surface: parse a real CM
 * binary, instantiate it under the layered runtime guest, invoke an exported
 * function and inspect exported names / interfaces.
 *
 * <p>The tests are skipped when the {@code wasmcm_runtime_guest.wasm} blob is
 * not resolvable via {@link WasmcmGuestBlobLocator} — a fresh checkout that has
 * not built the sibling {@code wasm-cm} tree gets a green {@code mvn test}
 * rather than a red failure.
 */
final class EndiveComponentIntegrationTest {

    @Test
    void lookupOrderStringNamesBlobExplicitly() {
        // The UnsupportedFeatureException raised when the guest is missing embeds
        // this string; verify it stays actionable ("names the blob and every knob").
        String s = WasmcmGuestBlobLocator.describeLookupOrder();
        assertTrue(s.contains("wasmcm_runtime_guest.wasm"),
                "lookup order should name the blob: " + s);
        assertTrue(s.contains(WasmcmGuestBlobLocator.SYSTEM_PROPERTY),
                "lookup order should name the system property: " + s);
        assertTrue(s.contains(WasmcmGuestBlobLocator.ENV_HOME),
                "lookup order should name WASMCM_HOME: " + s);
        assertTrue(s.contains("classpath"),
                "lookup order should mention classpath: " + s);
    }

    @Test
    void scalarComponentRoundTripsAddThroughGuest() throws Exception {
        assumeGuestPresent();
        byte[] bytes = loadClasspath("scalar-component.wasm");
        assumeTrue(bytes != null, "scalar-component.wasm missing from test resources");

        try (Engine engine = EndiveEngineAdapter.create(null);
                Component component = engine.loadComponent(bytes)) {
            assertNotNull(component, "loadComponent must not return null");
            ComponentInstance instance = component.instantiate();
            assertNotNull(instance, "instantiate must not return null");

            // The scalar-component fixture exposes exactly `add: func(u32, u32) -> u32`
            // at the component root. hasFunction / exportedFunctions rely on the guest's
            // wasmcm_instance_export_count / wasmcm_instance_export_name.
            assertTrue(instance.hasFunction("add"),
                    "scalar-component should export 'add', got " + instance.exportedFunctions());

            // U32 args round-trip through Boxed(TAG_U32, int); the fixture returns U32.
            Object result = instance.invoke(
                    "add",
                    new WasmcmValueCodec.Boxed(WasmcmValueCodec.TAG_U32, 2),
                    new WasmcmValueCodec.Boxed(WasmcmValueCodec.TAG_U32, 3));
            assertEquals(5L, ((Number) result).longValue(),
                    "add(2, 3) should be 5; got " + result);
        }
    }

    /**
     * Attempt to instantiate a real WASI Preview 2 component that imports
     * {@code wasip2:host/primitives@0.1.0} and {@code wasi:io/poll@0.2.3}.
     *
     * <p>Walls 2, 3 and 8 have landed: the register/dispatch surface, the
     * runtime-side {@code ImportedInstance::HostCallback} plumbing, and
     * the guest-side {@code HostEngineWasm::create_host_func} +
     * {@code instantiate}-with-CoreFunc-imports pair are all wired
     * (see the sibling wasm-cm change that adds
     * {@code env.host_create_host_func},
     * {@code env.host_instantiate_with_imports}, and the
     * {@code wasmcm_dispatch_host_func} export).</p>
     *
     * <p>The current wall is <b>Wall 9</b>:
     * {@code HostEngineWasm::module_imports} at
     * {@code crates/wasmcm-runtime-guest/src/host_engine.rs:262-269}
     * still returns an empty vector — the variable-length import
     * descriptor codec + the paired {@code host_module_import_at}
     * handler are not yet wired. The runtime's
     * {@code instantiate_with_ref_imports} at
     * {@code crates/wasmcm-runtime/src/lib.rs:828} therefore builds an
     * empty {@code ext_imports} for a module that actually declares
     * imports, and Endive's own linker surfaces that mismatch as
     * {@code HOST_ERR_UNLINKABLE: "missing import: <mod>.<name>"}.</p>
     *
     * <p>Surfaces through the WASI P2 lane as a
     * {@link InstantiationException} whose message names the missing
     * import. When Wall 9 closes (module_imports/exports introspection
     * fully wired), flip to
     * {@code assertNotNull(component.instantiate(ctx))} and assert the
     * expected exports.</p>
     */
    @Test
    void wasiClocksComponentReachesInstantiationSite() throws Exception {
        assumeGuestPresent();
        byte[] bytes = loadClasspath("wasi_p2_clocks.component.wasm");
        assumeTrue(bytes != null, "wasi_p2_clocks.component.wasm missing from test resources");

        try (Engine engine = EndiveEngineAdapter.create(null);
                Component component = engine.loadComponent(bytes)) {
            assertNotNull(component, "loadComponent should succeed on a WASI P2 component binary");

            DefaultLinkingContext ctx = DefaultLinkingContext.builder()
                    .addWitHostFunction(
                            new WitHostFunctionDefinition(
                                    "wasip2:host/primitives@0.1.0#clock-monotonic-now-ns",
                                    args -> new Object[] {
                                        new WasmcmValueCodec.Boxed(
                                                WasmcmValueCodec.TAG_U64, 42L)
                                    }))
                    .addWitHostFunction(
                            new WitHostFunctionDefinition(
                                    "wasip2:host/primitives@0.1.0#clock-wall-now-ns",
                                    args -> new Object[] {
                                        new WasmcmValueCodec.Boxed(
                                                WasmcmValueCodec.TAG_U64, 43L)
                                    }))
                    .addWitHostFunction(
                            new WitHostFunctionDefinition(
                                    "wasip2:host/primitives@0.1.0#clock-monotonic-resolution-ns",
                                    args -> new Object[] {
                                        new WasmcmValueCodec.Boxed(
                                                WasmcmValueCodec.TAG_U64, 1L)
                                    }))
                    // wasi:io/poll@0.2.3 is a resource-type-only import in
                    // this component (num_funcs=0 in the sibling Rust
                    // probe); we register a placeholder entry so the guest
                    // provider table has a row for it. Wall 9 (missing
                    // module_imports introspection) is the current
                    // blocker — the callback is not yet reached.
                    .addWitHostFunction(
                            new WitHostFunctionDefinition(
                                    "wasi:io/poll@0.2.3#__placeholder",
                                    args -> new Object[0]))
                    .build();

            // Wall 9: expect HOST_ERR_UNLINKABLE from the linker step,
            // carrying a "missing import: <interface>.<name>" message.
            // The message text is the strong signal Wall 8 actually
            // works — the runtime got past create_host_func and reached
            // Endive's own linker with a module whose imports the
            // empty module_imports stub can't enumerate.
            InstantiationException ex = assertThrows(
                    InstantiationException.class,
                    () -> component.instantiate(ctx));
            assertNotNull(ex.getMessage(), "guest should have written a diagnostic");
            assertTrue(
                    ex.getMessage().contains("missing import"),
                    "expected the honest module_imports (Wall 9) wall — 'missing import' "
                            + "— but got: " + ex.getMessage());
            assertTrue(
                    ex.getMessage().contains("wasip2:host/primitives")
                            || ex.getMessage().contains("wasi:io/poll"),
                    "expected the missing-import to name a wasi_p2 interface "
                            + "(confirming Wall 8 was cleared); was: " + ex.getMessage());
        }
    }

    private static void assumeGuestPresent() {
        byte[] guest = WasmcmGuestBlobLocator.locateOrNull();
        assumeTrue(
                guest != null,
                () ->
                        "wasmcm_runtime_guest.wasm not found; searched: "
                                + WasmcmGuestBlobLocator.describeLookupOrder());
    }

    private static byte[] loadClasspath(String resource) throws IOException {
        try (InputStream is = EndiveComponentIntegrationTest.class
                .getClassLoader()
                .getResourceAsStream(resource)) {
            if (is == null) {
                // Fallback: check the module test resources on disk. Convenience path so
                // running from an IDE without a fresh test-compile still works.
                Path fallback = Paths.get(
                        System.getProperty("user.dir"),
                        "src", "test", "resources", resource);
                if (Files.isRegularFile(fallback)) {
                    return Files.readAllBytes(fallback);
                }
                return null;
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = is.read(buf)) > 0) {
                out.write(buf, 0, n);
            }
            return out.toByteArray();
        }
    }
}
