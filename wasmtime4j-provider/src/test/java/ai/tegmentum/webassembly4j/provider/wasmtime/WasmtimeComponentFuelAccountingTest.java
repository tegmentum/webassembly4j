package ai.tegmentum.webassembly4j.provider.wasmtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ai.tegmentum.webassembly4j.api.Component;
import ai.tegmentum.webassembly4j.api.ComponentInstance;
import ai.tegmentum.webassembly4j.api.DefaultLinkingContext;
import ai.tegmentum.webassembly4j.api.DefaultWasiContext;
import ai.tegmentum.webassembly4j.api.Engine;
import ai.tegmentum.webassembly4j.api.config.ComponentConfig;
import ai.tegmentum.webassembly4j.api.config.WebAssemblyConfig;
import ai.tegmentum.webassembly4j.api.exception.ExecutionException;
import ai.tegmentum.webassembly4j.provider.wasmtime.config.WasmtimeConfig;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

/**
 * Exercises {@link ComponentInstance#consumeFuel(long)} + {@link ComponentInstance#fuelConsumed()}
 * end-to-end through the wasmtime4j provider — the wasmtime4j 1.4.7 accessors that let the host
 * charge fuel against the same wasmtime store the guest consumes, and read back the amount spent.
 *
 * <p>Reuses the checked-in counter component (see {@code src/test/rust/counter_component/}) — this
 * test only cares that the component instantiates + invocations actually burn fuel; the counter
 * semantics themselves are covered by {@link WasmtimeCallableResourceTest}.
 */
class WasmtimeComponentFuelAccountingTest {

    private static final String COUNTER_INTERFACE = "tegmentum:test-counter/counter-api@0.1.0";
    private static final String CTOR_EXPORT = COUNTER_INTERFACE + "#[constructor]counter";
    private static final String INCREMENT_METHOD =
            COUNTER_INTERFACE + "#[method]counter.increment";

    static boolean runtimeAvailable() {
        return new WasmtimeProvider().availability().available();
    }

    private static byte[] loadCounterComponent() throws IOException {
        try (InputStream is =
                WasmtimeComponentFuelAccountingTest.class.getResourceAsStream(
                        "/counter_component.wasm")) {
            assertNotNull(is, "counter_component.wasm not on classpath");
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
     * A cargo-component-built component imports {@code wasi:cli} / {@code wasi:io} for its
     * ambient environment, so instantiation always needs a WASI context — an empty one is enough
     * since these tests never touch the filesystem or network.
     */
    private static DefaultLinkingContext wasiLinking() {
        return DefaultLinkingContext.builder()
                .wasiContext(DefaultWasiContext.builder().build())
                .build();
    }

    /**
     * Enable fuel metering on the wasmtime engine so the provider actually pushes the
     * {@link ComponentConfig#fuelLimit()} down as a hard cap on the store the guest runs against.
     * Without this the provider treats the config as advisory only (see
     * {@code WasmtimeComponentAdapter#newStore} — the {@code setFuel} call is gated on
     * {@code engine.isFuelEnabled()}) and consumeFuel wouldn't have a real cap to enforce.
     */
    private static Engine engineWithFuel() {
        WebAssemblyConfig cfg = WebAssemblyConfig.builder()
                .engineConfig(WasmtimeConfig.builder().consumeFuel(true).build())
                .build();
        return WasmtimeEngineAdapter.create(cfg);
    }

    @Test
    @EnabledIf("runtimeAvailable")
    @DisplayName("consumeFuel(K) then fuelConsumed() returns K (against a fresh instance)")
    void consumeFuelAndReadItBack() throws Exception {
        byte[] wasm = loadCounterComponent();
        final long fuelCap = 1_000_000L;
        try (Engine engine = engineWithFuel();
                Component component = engine.loadComponent(wasm)) {
            ComponentInstance instance =
                    component.instantiate(
                            wasiLinking(), ComponentConfig.builder().fuelLimit(fuelCap).build());

            // Baseline is captured at instantiation; nothing has burned fuel yet.
            assertEquals(
                    0L,
                    instance.fuelConsumed(),
                    "fresh instance should report zero fuel consumed against the just-set baseline");

            instance.consumeFuel(1234L);
            assertEquals(
                    1234L,
                    instance.fuelConsumed(),
                    "fuelConsumed() must reflect the exact host-side deduction");

            instance.consumeFuel(766L);
            assertEquals(
                    2000L,
                    instance.fuelConsumed(),
                    "successive consumeFuel calls must be additive against the baseline");
        }
    }

    @Test
    @EnabledIf("runtimeAvailable")
    @DisplayName("consumeFuel beyond the store's remaining fuel throws (no partial deduction)")
    void consumeFuelBeyondRemainingThrows() throws Exception {
        byte[] wasm = loadCounterComponent();
        // Big enough for cargo-component's wasi:cli init (~16 KiB of fuel), then leaves plenty
        // of headroom for the consumeFuel arithmetic below.
        final long fuelCap = 100_000L;
        try (Engine engine = engineWithFuel();
                Component component = engine.loadComponent(wasm)) {
            ComponentInstance instance =
                    component.instantiate(
                            wasiLinking(), ComponentConfig.builder().fuelLimit(fuelCap).build());

            // Burn 4000 units first so the failure isn't just "asked for > cap on a fresh store"
            // — it's "asked for > remaining after a real prior deduction".
            instance.consumeFuel(4_000L);
            assertEquals(4_000L, instance.fuelConsumed());

            // Ask for far more than what remains and observe the failure. Use a value larger than
            // the WHOLE cap so the test doesn't have to reason about exactly how much fuel wasm
            // init consumed.
            final long tooMuch = fuelCap + 1_000L;
            ExecutionException ex =
                    assertThrows(
                            ExecutionException.class,
                            () -> instance.consumeFuel(tooMuch),
                            "deducting more than the remaining fuel must throw");
            assertTrue(
                    ex.getMessage().toLowerCase().contains("insufficient")
                            || (ex.getCause() != null
                                    && ex.getCause()
                                            .getMessage()
                                            .toLowerCase()
                                            .contains("insufficient")),
                    "message should identify the insufficiency; got: " + ex.getMessage());
            assertEquals(
                    4_000L,
                    instance.fuelConsumed(),
                    "a failed consumeFuel must leave the store's remaining fuel unchanged");
        }
    }

    @Test
    @EnabledIf("runtimeAvailable")
    @DisplayName("guest invocation actually burns store fuel — fuelConsumed reflects it")
    void guestInvocationBurnsStoreFuel() throws Exception {
        byte[] wasm = loadCounterComponent();
        final long fuelCap = 10_000_000L;
        try (Engine engine = engineWithFuel();
                Component component = engine.loadComponent(wasm)) {
            ComponentInstance instance =
                    component.instantiate(
                            wasiLinking(), ComponentConfig.builder().fuelLimit(fuelCap).build());

            // Drive a real component call. Not asserting the resource plumbing here (that's the
            // callable-resource test); the value returned is a WitOwn — we only care that a wasm
            // call executed, so that fuelConsumed reads a positive amount afterwards.
            Object handle = instance.invokeWit(CTOR_EXPORT);
            assertNotNull(handle);

            long afterCtor = instance.fuelConsumed();
            assertTrue(
                    afterCtor > 0L,
                    "constructor invocation should burn some wasm fuel; got " + afterCtor);
        }
    }
}
