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

import ai.tegmentum.webassembly4j.api.Engine;
import ai.tegmentum.webassembly4j.api.config.WebAssemblyConfig;
import ai.tegmentum.webassembly4j.provider.wasmos.jni.WasmosNative;
import ai.tegmentum.webassembly4j.spi.EngineProvider;
import ai.tegmentum.webassembly4j.spi.ProviderAvailability;
import ai.tegmentum.webassembly4j.spi.ProviderDescriptor;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * webassembly4j {@link EngineProvider} for wasmos — a wasmtime-backed
 * component runtime that pre-links the wasmos-runtime host surface
 * (host:wasmtime, host:bootstrap, host:caps, host:grants, wasmos:host/*)
 * and wasi:p2 onto every component instance.
 *
 * <p>Discovered via {@link java.util.ServiceLoader}. Selection: pass
 * provider id {@code "wasmos"} to
 * {@link ai.tegmentum.webassembly4j.api.EngineSelection}, or let the
 * default priority ordering pick it up (priority 90 — deliberately
 * below the plain-wasmtime provider so a caller that just wants
 * component-model support doesn't accidentally instantiate a
 * wasmos-shaped host on a component that doesn't need it).
 *
 * <p>The underlying runtime is wasmos-runtime (Rust crate at
 * {@code ~/git/wasmos}) accessed through a JNI wrapper cdylib bundled
 * in this jar's {@code /natives/} directory.
 */
public final class WasmosProvider implements EngineProvider {

    private static final String ENGINE_ID = "wasmos";
    private static final String PROVIDER_ID = "wasmos";
    private static final String VERSION = "1.0.0-SNAPSHOT";
    private static final int MIN_JAVA = 11;
    /** Priority sits between wasmtime (200) and graalwasm (150): a caller who
     *  wants any-provider-will-do lands on wasmtime; a caller who wants
     *  wasmos-shaped hosting asks for "wasmos" by id explicitly. */
    private static final int PRIORITY = 90;

    @Override
    public ProviderDescriptor descriptor() {
        return new ProviderDescriptor() {
            @Override public String engineId() { return ENGINE_ID; }
            @Override public String providerId() { return PROVIDER_ID; }
            @Override public String version() { return VERSION; }
            @Override public int minimumJavaVersion() { return MIN_JAVA; }
            @Override public Set<String> tags() {
                final Set<String> tags = new HashSet<>();
                tags.add("native");
                tags.add("component-model");
                tags.add("wasmos");
                return Collections.unmodifiableSet(tags);
            }
            @Override public int priority() { return PRIORITY; }
        };
    }

    /**
     * Probes availability by trying to create + immediately close a
     * wasmos engine handle. This exercises the whole native surface
     * (dylib extraction, JNI symbol resolution, wasmtime + Tokio
     * initialization) so a false "available" answer is very unlikely.
     */
    @Override
    public ProviderAvailability availability() {
        try {
            final long h = WasmosNative.engineCreate();
            if (h == 0) {
                return unavailable("engineCreate returned 0 handle without throwing");
            }
            WasmosNative.engineClose(h);
            return available();
        } catch (Throwable t) {
            return unavailable(t.getClass().getName() + ": " + t.getMessage());
        }
    }

    @Override
    public Engine create(WebAssemblyConfig config) {
        // WebAssemblyConfig is accepted for SPI symmetry but the MVP
        // provider has no engine-specific knobs; the wasmos-runtime side
        // is opinionated (async on, component-model on, wasi_p2 on) and
        // isn't tunable through the current webassembly4j API.
        return WasmosEngineAdapter.create();
    }

    private static ProviderAvailability available() {
        return new ProviderAvailability() {
            @Override public boolean available() { return true; }
            @Override public String message() { return "wasmos runtime available"; }
        };
    }

    private static ProviderAvailability unavailable(final String reason) {
        return new ProviderAvailability() {
            @Override public boolean available() { return false; }
            @Override public String message() { return "wasmos not available: " + reason; }
        };
    }
}
