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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import ai.tegmentum.webassembly4j.api.Component;
import ai.tegmentum.webassembly4j.api.ComponentInstance;
import ai.tegmentum.webassembly4j.api.Engine;
import ai.tegmentum.webassembly4j.spi.EngineProvider;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.ServiceLoader;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * End-to-end demo — load the wasmos {@code composed_wasmtime.wasm}
 * fixture through the wasmos-provider, call the {@code run} export, assert
 * 42. This is the wasmos-provider MVP's north-star acceptance test:
 * passing it means a caller using ONLY webassembly4j's public API +
 * selecting {@code "wasmos"} as the provider can drive a full
 * {@code guest-demo-portable + adapter-wasmtime} composition through the
 * bundled Rust wasmos-runtime.
 *
 * <p>The reference Rust equivalent lives at
 * {@code ~/git/wasmos/tests/e2e_smoke.rs} and is the shape we mirror
 * end-to-end.
 *
 * <p>Fixture location: {@code ~/git/wasmos/tests/e2e-fixtures/composed_wasmtime.wasm}.
 * If absent, {@link org.junit.jupiter.api.Assumptions#assumeTrue} skips the
 * test — regenerate via the wasmos build instructions.
 */
class WasmosProviderDemoTest {

    private static final Path COMPOSED_FIXTURE = Path.of(
            System.getProperty("user.home"),
            "git", "wasmos", "tests", "e2e-fixtures", "composed_wasmtime.wasm");

    @Test
    @DisplayName("wasmos-provider is registered via ServiceLoader with id=\"wasmos\"")
    void serviceLoaderPicksUpWasmosProvider() {
        boolean found = false;
        for (EngineProvider p : ServiceLoader.load(EngineProvider.class)) {
            if ("wasmos".equals(p.descriptor().providerId())) {
                found = true;
                assertEquals("wasmos", p.descriptor().engineId());
                assertTrue(p.descriptor().tags().contains("wasmos"));
                break;
            }
        }
        assertTrue(found,
                "expected an EngineProvider with providerId=\"wasmos\" on the classpath — "
                        + "check META-INF/services/ai.tegmentum.webassembly4j.spi.EngineProvider");
    }

    @Test
    @DisplayName("composed guest.run() returns 42 through wasmos-provider + JNI wasmos-runtime")
    void composedRunReturns42() throws Exception {
        assumeTrue(Files.exists(COMPOSED_FIXTURE),
                "composed_wasmtime.wasm fixture not present at " + COMPOSED_FIXTURE
                        + " — rebuild via wasmos e2e_smoke instructions");

        final byte[] bytes = Files.readAllBytes(COMPOSED_FIXTURE);
        assertTrue(bytes.length > 0, "fixture must be non-empty");

        final EngineProvider provider = ServiceLoader.load(EngineProvider.class)
                .stream()
                .map(ServiceLoader.Provider::get)
                .filter(p -> "wasmos".equals(p.descriptor().providerId()))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "wasmos provider not on classpath — see the ServiceLoader test above"));

        try (Engine engine = provider.create(null);
             Component component = engine.loadComponent(bytes)) {
            assertNotNull(component, "wasmos-provider should compile the composed component");

            // Sanity check: the exported functions should include "run" (the
            // guest-demo-portable's entry point). If this fails, the fixture
            // was rebuilt with a different shape and the whole test needs
            // updating.
            final List<String> exports = ((WasmosComponentAdapter) component)
                    .exportedFunctionNames();
            assertTrue(exports.contains("run"),
                    "composed component should export 'run' — got: " + exports);

            final ComponentInstance instance = component.instantiate();
            try {
                final Object result = instance.invoke("run");
                assertEquals(42, ((Number) result).intValue(),
                        "composed guest-demo-portable.run() must return 42 "
                                + "(same as wasmos/tests/e2e_smoke.rs)");
            } finally {
                if (instance instanceof WasmosComponentInstanceAdapter) {
                    ((WasmosComponentInstanceAdapter) instance).closeInternal();
                }
            }
        }
    }
}
