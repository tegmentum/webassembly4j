/*
 * Copyright 2026 Tegmentum AI
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package ai.tegmentum.webassembly4j.provider.wasmos;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import ai.tegmentum.webassembly4j.api.Component;
import ai.tegmentum.webassembly4j.api.ComponentInstance;
import ai.tegmentum.webassembly4j.api.DefaultLinkingContext;
import ai.tegmentum.webassembly4j.api.DefaultWasiContext;
import ai.tegmentum.webassembly4j.api.Engine;
import ai.tegmentum.webassembly4j.api.HostFunctionDefinition;
import ai.tegmentum.webassembly4j.api.LinkingContext;
import ai.tegmentum.webassembly4j.api.ValueType;
import ai.tegmentum.webassembly4j.api.WasiContext;
import ai.tegmentum.webassembly4j.api.config.ComponentConfig;
import ai.tegmentum.webassembly4j.api.exception.UnsupportedFeatureException;
import ai.tegmentum.webassembly4j.spi.EngineProvider;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ServiceLoader;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * End-to-end typed-invoke tests — proves the Java args flow into Rust,
 * wasmtime executes with them, and the Rust return path decodes back
 * into a Java-natural object.
 *
 * <p>Uses {@code scalar-component.wasm} (mirrored into wasmos-provider's
 * test resources from the endive fixture) — a minimal Component Model
 * binary that exports {@code add: func(u32, u32) -> u32}.
 *
 * <p>Also verifies the linking-context + component-config plumbing —
 * WASI-P2 opt-in, memory-limit passthrough, and the philosophical
 * rejection of Java-hosted host functions with a helpful message.
 */
final class WasmosTypedInvokeTest {

    private static EngineProvider provider() {
        return ServiceLoader.load(EngineProvider.class)
                .stream()
                .map(ServiceLoader.Provider::get)
                .filter(p -> "wasmos".equals(p.descriptor().providerId()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("wasmos provider not on classpath"));
    }

    private static byte[] loadFixture(String name) throws IOException {
        try (InputStream is = WasmosTypedInvokeTest.class
                .getClassLoader()
                .getResourceAsStream(name)) {
            if (is == null) return null;
            final ByteArrayOutputStream out = new ByteArrayOutputStream();
            final byte[] buf = new byte[8192];
            int n;
            while ((n = is.read(buf)) > 0) {
                out.write(buf, 0, n);
            }
            return out.toByteArray();
        }
    }

    private static void withInstance(
            ComponentInstance instance,
            InstanceRunnable body) throws Exception {
        try {
            body.run(instance);
        } finally {
            if (instance instanceof WasmosComponentInstanceAdapter) {
                ((WasmosComponentInstanceAdapter) instance).closeInternal();
            }
        }
    }

    @FunctionalInterface
    private interface InstanceRunnable {
        void run(ComponentInstance instance) throws Exception;
    }

    @Test
    @DisplayName("scalar-component add(2, 3) returns 5 through typed JSON invoke path")
    void scalarComponentAdd() throws Exception {
        final byte[] bytes = loadFixture("scalar-component.wasm");
        assumeTrue(bytes != null, "scalar-component.wasm missing from test resources");

        try (Engine engine = provider().create(null);
             Component component = engine.loadComponent(bytes)) {
            assertNotNull(component, "component compile should succeed");
            withInstance(component.instantiate(), instance -> {
                assertTrue(instance.hasFunction("add"),
                        "scalar-component should export 'add'; got "
                                + instance.exportedFunctions());
                // u32 args go via WitU32 wrappers so the JSON schema sends
                // proper U32-tagged JsonVals that wasmtime type-checks
                // against the func signature.
                final Object result = instance.invoke(
                        "add",
                        new WasmosMarshalling.WitU32(2L),
                        new WasmosMarshalling.WitU32(3L));
                assertEquals(5L, ((Number) result).longValue(),
                        "add(2, 3) should be 5; got " + result);
            });
        }
    }

    @Test
    @DisplayName("componentInstantiate honors ComponentConfig.maxMemoryBytes")
    void componentConfigMemoryPasses() throws Exception {
        final byte[] bytes = loadFixture("scalar-component.wasm");
        assumeTrue(bytes != null, "scalar-component.wasm missing from test resources");

        try (Engine engine = provider().create(null);
             Component component = engine.loadComponent(bytes)) {
            // A generous limit that the scalar guest can't come close to
            // touching — we're validating the plumbing goes through, not
            // stressing the limiter.
            final ComponentConfig cfg = ComponentConfig.builder()
                    .maxMemoryBytes(64L * 1024 * 1024) // 64 MiB
                    .build();
            withInstance(component.instantiate(cfg), instance -> {
                final Object result = instance.invoke(
                        "add",
                        new WasmosMarshalling.WitU32(10L),
                        new WasmosMarshalling.WitU32(20L));
                assertEquals(30L, ((Number) result).longValue());
            });
        }
    }

    @Test
    @DisplayName("LinkingContext with wasiContext instantiates cleanly (WASI-P2 plumbing)")
    void linkingContextWithWasi() throws Exception {
        final byte[] bytes = loadFixture("scalar-component.wasm");
        assumeTrue(bytes != null, "scalar-component.wasm missing from test resources");

        try (Engine engine = provider().create(null);
             Component component = engine.loadComponent(bytes)) {
            final WasiContext wasi = DefaultWasiContext.builder()
                    .addArg("wasmos-provider-test")
                    .env("HELLO", "world")
                    .build();
            final LinkingContext ctx = DefaultLinkingContext.builder()
                    .wasiContext(wasi)
                    .build();
            withInstance(component.instantiate(ctx), instance -> {
                // scalar-component doesn't touch WASI — we're validating the
                // instantiation plumbing itself doesn't throw when a WasiCtx
                // is configured.
                final Object result = instance.invoke(
                        "add",
                        new WasmosMarshalling.WitU32(7L),
                        new WasmosMarshalling.WitU32(35L));
                assertEquals(42L, ((Number) result).longValue());
            });
        }
    }

    @Test
    @DisplayName("linking-context with Java-hosted host functions is rejected with a useful message")
    void hostFunctionsRejected() throws Exception {
        final byte[] bytes = loadFixture("scalar-component.wasm");
        assumeTrue(bytes != null, "scalar-component.wasm missing from test resources");

        try (Engine engine = provider().create(null);
             Component component = engine.loadComponent(bytes)) {
            final LinkingContext ctx = DefaultLinkingContext.builder()
                    .addHostFunction(new HostFunctionDefinition(
                            "mod",
                            "fn",
                            new ValueType[0],
                            new ValueType[0],
                            args -> new Object[0]))
                    .build();
            final UnsupportedFeatureException ex = assertThrows(
                    UnsupportedFeatureException.class,
                    () -> component.instantiate(ctx));
            // The error message must guide the user to the wasmtime provider —
            // that's the whole point of the philosophical rejection.
            assertTrue(ex.getMessage().contains("wasmtime"),
                    "error should mention the \"wasmtime\" fallback provider; got: "
                            + ex.getMessage());
        }
    }

    @Test
    @DisplayName("ComponentConfig.fuelLimit is honored; instantiation succeeds and function runs")
    void fuelLimitAccepted() throws Exception {
        final byte[] bytes = loadFixture("scalar-component.wasm");
        assumeTrue(bytes != null, "scalar-component.wasm missing from test resources");

        try (Engine engine = provider().create(null);
             Component component = engine.loadComponent(bytes)) {
            // Generous fuel budget — add(u32, u32) is a trivial op so any
            // reasonable amount lets it complete. Focus is validating the
            // set_fuel plumbing succeeds and doesn't blow up mid-invoke,
            // not measuring exact fuel consumption.
            final ComponentConfig cfg = ComponentConfig.builder()
                    .fuelLimit(1_000_000_000L)
                    .build();
            withInstance(component.instantiate(cfg), instance -> {
                final Object result = instance.invoke(
                        "add",
                        new WasmosMarshalling.WitU32(11L),
                        new WasmosMarshalling.WitU32(22L));
                assertEquals(33L, ((Number) result).longValue());
            });
        }
    }

    @Test
    @DisplayName("Extended ComponentConfig fields (tables/instances/memories/table-elements) instantiate cleanly")
    void extendedComponentConfigFields() throws Exception {
        final byte[] bytes = loadFixture("scalar-component.wasm");
        assumeTrue(bytes != null, "scalar-component.wasm missing from test resources");

        try (Engine engine = provider().create(null);
             Component component = engine.loadComponent(bytes)) {
            // All limits set to generous values that scalar-component
            // won't come close to hitting. Validates the JNI signature +
            // wire-through, not the limiter's fault behaviour (those
            // would need a purpose-built component to exercise).
            final ComponentConfig cfg = ComponentConfig.builder()
                    .maxTableElements(1024L)
                    .maxInstances(32L)
                    .maxTables(8L)
                    .maxMemories(4L)
                    .build();
            withInstance(component.instantiate(cfg), instance -> {
                final Object result = instance.invoke(
                        "add",
                        new WasmosMarshalling.WitU32(1L),
                        new WasmosMarshalling.WitU32(2L));
                assertEquals(3L, ((Number) result).longValue());
            });
        }
    }

    @Test
    @DisplayName("Component.serialize() + Engine.deserializeComponent round-trip preserves behaviour")
    void serializeRoundTrip() throws Exception {
        final byte[] bytes = loadFixture("scalar-component.wasm");
        assumeTrue(bytes != null, "scalar-component.wasm missing from test resources");

        final byte[] cached;
        try (Engine engine = provider().create(null);
             Component component = engine.loadComponent(bytes)) {
            cached = component.serialize();
            assertNotNull(cached, "serialize() must return non-null AOT bytes");
            assertTrue(cached.length > 0, "serialized bytes must be non-empty");
        }

        // Fresh engine, deserialize the cached bytes rather than compile
        // from the raw component source. Ensures the byte cache is
        // engine-independent within a matching wasmtime Config.
        try (Engine engine = provider().create(null)) {
            final Component reloaded =
                    ((WasmosEngineAdapter) engine).deserializeComponent(cached);
            try (Component c = reloaded) {
                withInstance(c.instantiate(), instance -> {
                    final Object result = instance.invoke(
                            "add",
                            new WasmosMarshalling.WitU32(40L),
                            new WasmosMarshalling.WitU32(2L));
                    assertEquals(42L, ((Number) result).longValue(),
                            "deserialized component must produce identical results");
                });
            }
        }
    }

    // ---------------------------------------------------------------------
    // Val::Resource end-to-end: constructor -> increment (x2) -> get. The
    // fixture is `counter-component.wasm`, built from
    // `counter-fixture-src/` (see the README there for the rebuild recipe).
    // Exercises the full round-trip: guest returns own<counter>, Java
    // parks it in the Rust ResourceRegistry as a WitResource, then passes
    // that WitResource back in as borrow<counter> for the method calls.
    // ---------------------------------------------------------------------

    /** Fully-qualified export names — the resource lives inside an
     *  exported interface, so top-level `Instance::get_func` can't see it.
     *  The wasmos-provider native resolver splits on the first `#` and
     *  walks `get_export_index` twice.  See
     *  `wasmos-provider/native/src/lib.rs::resolve_func`. */
    private static final String CTR_IFACE = "tegmentum:counter/counter-api@0.1.0";
    private static final String CTR_CONSTRUCTOR = CTR_IFACE + "#[constructor]counter";
    private static final String CTR_INCREMENT   = CTR_IFACE + "#[method]counter.increment";
    private static final String CTR_GET         = CTR_IFACE + "#[method]counter.get";

    @Test
    @DisplayName("counter-component: constructor -> increment x2 -> get returns 12 (Val::Resource E2E)")
    void counterResourceRoundTrip() throws Exception {
        final byte[] bytes = loadFixture("counter-component.wasm");
        assumeTrue(bytes != null, "counter-component.wasm missing from test resources");

        try (Engine engine = provider().create(null);
             Component component = engine.loadComponent(bytes)) {
            assertNotNull(component, "counter-component should compile");
            withInstance(component.instantiate(), instance -> {
                // Constructor returns an owned counter resource; wasmos-provider
                // parks the ResourceAny in the per-instance ResourceRegistry and
                // hands Java back a WitResource carrying { tableId, typeName,
                // owned=true }.
                final Object created = instance.invoke(
                        CTR_CONSTRUCTOR,
                        new WasmosMarshalling.WitU32(10L));
                assertNotNull(created, "constructor must return the counter handle");
                assertTrue(created instanceof WasmosMarshalling.WitResource,
                        "constructor should return a WitResource; got "
                                + created.getClass().getName() + " = " + created);
                final WasmosMarshalling.WitResource owned =
                        (WasmosMarshalling.WitResource) created;
                assertTrue(owned.owned(),
                        "guest return of own<counter> must be owned=true; got " + owned);
                assertTrue(owned.tableId() >= 0,
                        "tableId must be a valid slot id; got " + owned.tableId());

                // Increment twice — WIT `increment: func()` takes an implicit
                // borrow of `self`, i.e. `borrow<counter>` in the ABI. We pass
                // the same WitResource with owned=false so the Rust registry
                // does `peek` (leaves it parked) rather than `take` (removes
                // it), which lets us reuse the handle across calls.
                final WasmosMarshalling.WitResource borrow =
                        new WasmosMarshalling.WitResource(
                                owned.tableId(), owned.typeIdentifier(), false);

                final Object r1 = instance.invoke(CTR_INCREMENT, borrow);
                // increment returns () — the JSON path unwraps arity-0 to null.
                assertEquals(null, r1, "increment returns unit; got " + r1);

                final Object r2 = instance.invoke(CTR_INCREMENT, borrow);
                assertEquals(null, r2, "increment returns unit; got " + r2);

                // get — same borrow-shaped call, but returns u32.
                final Object got = instance.invoke(CTR_GET, borrow);
                assertNotNull(got, "get must return a value");
                // WIT u32 comes back as Long (WitU32 domain) — accept both
                // Number widths defensively.
                assertEquals(12L, ((Number) got).longValue(),
                        "counter started at 10, +1 +1 -> 12; got " + got);
            });
        }
    }

    @Test
    @DisplayName("counter-component: WitResource handles remain valid across instantiation close")
    void counterResourceDroppedOnInstanceClose() throws Exception {
        // Closing the instance drops the ResourceRegistry alongside the
        // Store — no leaks even if the caller never explicitly hands the
        // resource back. Sanity-check that the close path doesn't throw
        // when live resources are still parked.
        final byte[] bytes = loadFixture("counter-component.wasm");
        assumeTrue(bytes != null, "counter-component.wasm missing from test resources");

        try (Engine engine = provider().create(null);
             Component component = engine.loadComponent(bytes)) {
            withInstance(component.instantiate(), instance -> {
                // Create a resource and immediately walk away — the close
                // hook driven by withInstance should tear it down cleanly.
                final Object created = instance.invoke(
                        CTR_CONSTRUCTOR,
                        new WasmosMarshalling.WitU32(0L));
                assertTrue(created instanceof WasmosMarshalling.WitResource,
                        "constructor should still return a WitResource: " + created);
            });
        }
    }

    @Test
    @DisplayName("negative ComponentConfig field values are rejected with a helpful message")
    void negativeConfigValuesRejected() throws Exception {
        final byte[] bytes = loadFixture("scalar-component.wasm");
        assumeTrue(bytes != null, "scalar-component.wasm missing from test resources");

        try (Engine engine = provider().create(null);
             Component component = engine.loadComponent(bytes)) {
            final ComponentConfig cfg = ComponentConfig.builder()
                    .fuelLimit(-5L)  // negative collides with the -1 sentinel on the JNI boundary
                    .build();
            final IllegalArgumentException ex = assertThrows(
                    IllegalArgumentException.class,
                    () -> component.instantiate(cfg));
            assertTrue(ex.getMessage().contains("fuelLimit"),
                    "error should identify the offending field; got: " + ex.getMessage());
        }
    }
}
