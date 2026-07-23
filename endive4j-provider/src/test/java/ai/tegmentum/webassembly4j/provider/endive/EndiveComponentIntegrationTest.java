package ai.tegmentum.webassembly4j.provider.endive;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import ai.tegmentum.webassembly4j.api.Component;
import ai.tegmentum.webassembly4j.api.ComponentInstance;
import ai.tegmentum.webassembly4j.api.DefaultLinkingContext;
import ai.tegmentum.webassembly4j.api.Engine;
import ai.tegmentum.webassembly4j.api.WitHostFunctionDefinition;

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
     * Instantiate a real WASI Preview 2 component that imports
     * {@code wasip2:host/primitives@0.1.0} and {@code wasi:io/poll@0.2.3}.
     *
     * <p>Walls 2, 3, 8 and 9 have landed: the register/dispatch
     * surface, the runtime-side {@code ImportedInstance::HostCallback}
     * plumbing, the guest-side {@code HostEngineWasm::create_host_func}
     * + {@code instantiate}-with-CoreFunc-imports pair, and — most
     * recently — the variable-length import descriptor codec that
     * turns {@code HostEngineWasm::module_imports} into a real
     * enumeration are all wired end-to-end. The runtime's
     * {@code instantiate_with_ref_imports} builds a full
     * {@code ext_imports} list from the compiled module's declared
     * imports and Endive's linker successfully binds every row.</p>
     *
     * <p>Exit criterion: {@code component.instantiate(ctx)} returns a
     * non-null instance and the guest surfaces at least one export.
     * If a Wall 10 emerges (e.g. an ImportedInstance::Component gap or
     * a Resource wiring issue), this test will fail with a fresh
     * diagnostic and the assertions get flipped again per the
     * milestone discipline.</p>
     */
    @Test
    void wasiClocksComponentInstantiatesCleanly() throws Exception {
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
                    // wasi:io/poll@0.2.3 is a resource-type-only import
                    // (num_funcs=0 in the sibling Rust probe); a
                    // placeholder entry keeps the guest's provider
                    // cross-check happy.
                    .addWitHostFunction(
                            new WitHostFunctionDefinition(
                                    "wasi:io/poll@0.2.3#__placeholder",
                                    args -> new Object[0]))
                    .build();

            ComponentInstance instance = component.instantiate(ctx);
            assertNotNull(instance, "instantiate should return a live instance after Wall 9");
            assertTrue(
                    !instance.exportedFunctions().isEmpty(),
                    "wasi_p2_clocks should surface at least one export; got "
                            + instance.exportedFunctions());
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
