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

import ai.tegmentum.webassembly4j.api.Component;
import ai.tegmentum.webassembly4j.api.ComponentInstance;
import ai.tegmentum.webassembly4j.api.LinkingContext;
import ai.tegmentum.webassembly4j.api.WasiContext;
import ai.tegmentum.webassembly4j.api.config.ComponentConfig;
import ai.tegmentum.webassembly4j.api.exception.InstantiationException;
import ai.tegmentum.webassembly4j.api.exception.UnsupportedFeatureException;
import ai.tegmentum.webassembly4j.api.exception.WebAssemblyException;
import ai.tegmentum.webassembly4j.provider.wasmos.jni.WasmosNative;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Adapter over a native wasmos component handle. Instantiation defers to
 * the JNI shim, which wires {@code wasmos_runtime::add_all_to_linker} +
 * {@code caps::wasi_p2} on a fresh Linker&lt;HostState&gt; per instance —
 * matching the reference Rust flow in {@code wasmos/tests/e2e_smoke.rs}.
 *
 * <p>Supported linking-context features:
 * <ul>
 *   <li>{@link LinkingContext#wasiContext()} — args, env, preopens
 *       (read-only + read-write), stdio inheritance are all plumbed
 *       through to {@code wasmtime_wasi::WasiCtxBuilder} on the Rust
 *       side.</li>
 * </ul>
 *
 * <p>Rejected linking-context features:
 * <ul>
 *   <li>{@link LinkingContext#hostFunctions()} + {@link
 *       LinkingContext#witHostFunctions()} — wasmos's philosophy is
 *       WIT-first; guest imports are satisfied through wasmos's own host
 *       surfaces (host:wasmtime, host:bootstrap, host:caps, host:grants,
 *       wasmos:host/*), not by Java-provided closures. Callers who want
 *       Java-hosted imports should use the {@code "wasmtime"} provider
 *       instead.</li>
 *   <li>{@link LinkingContext#wasiNnConfig()} — wasi:nn isn't wired into
 *       wasmos-runtime yet.</li>
 * </ul>
 *
 * <p>Supported {@link ComponentConfig} features (all optional, all
 * routed through wasmtime's outer-store surface):
 * <ul>
 *   <li>{@link ComponentConfig#maxMemoryBytes()} — surfaced as
 *       {@code wasmtime::StoreLimitsBuilder::memory_size} on the outer
 *       store.</li>
 *   <li>{@link ComponentConfig#epochDeadline()} — surfaced as a
 *       {@code Store::set_epoch_deadline} + {@code epoch_deadline_trap}
 *       on the fresh store. Callers must drive the epoch counter via
 *       {@link WasmosNative#engineIncrementEpoch(long)} from an
 *       out-of-band scheduler; the provider doesn't spawn a background
 *       ticker.</li>
 *   <li>{@link ComponentConfig#fuelLimit()} — surfaced as
 *       {@code Store::set_fuel} on the fresh store. The engine has
 *       {@code Config::consume_fuel(true)} enabled globally so this is
 *       a cheap per-store opt-in.</li>
 *   <li>{@link ComponentConfig#maxTableElements()} — surfaced as
 *       {@code StoreLimitsBuilder::table_elements}.</li>
 *   <li>{@link ComponentConfig#maxInstances()} — surfaced as
 *       {@code StoreLimitsBuilder::instances}.</li>
 *   <li>{@link ComponentConfig#maxTables()} — surfaced as
 *       {@code StoreLimitsBuilder::tables}.</li>
 *   <li>{@link ComponentConfig#maxMemories()} — surfaced as
 *       {@code StoreLimitsBuilder::memories}.</li>
 * </ul>
 *
 * <p>{@link ComponentConfig#trapOnGrowFailure()} is silently accepted —
 * wasmtime's {@code StoreLimits} always traps on limit exceed; there's
 * no separate switch to gate.
 */
final class WasmosComponentAdapter implements Component {

    private final WasmosEngineAdapter engine;
    private final long handle;
    private volatile boolean closed = false;

    WasmosComponentAdapter(final WasmosEngineAdapter engine, final long handle) {
        this.engine = engine;
        this.handle = handle;
    }

    private long nativeHandle() {
        if (closed) {
            throw new IllegalStateException("wasmos component has been closed");
        }
        return handle;
    }

    @Override
    public ComponentInstance instantiate() {
        return instantiateInternal(null, null);
    }

    @Override
    public ComponentInstance instantiate(LinkingContext linkingContext) {
        rejectUnsupportedLinkingContext(linkingContext);
        return instantiateInternal(linkingContext, null);
    }

    @Override
    public ComponentInstance instantiate(ComponentConfig config) {
        rejectUnsupportedComponentConfig(config);
        return instantiateInternal(null, config);
    }

    @Override
    public ComponentInstance instantiate(LinkingContext linkingContext, ComponentConfig config) {
        rejectUnsupportedLinkingContext(linkingContext);
        rejectUnsupportedComponentConfig(config);
        return instantiateInternal(linkingContext, config);
    }

    private void rejectUnsupportedLinkingContext(LinkingContext ctx) {
        if (ctx == null) return;
        if (!ctx.hostFunctions().isEmpty() || !ctx.witHostFunctions().isEmpty()) {
            throw new UnsupportedFeatureException(
                    "wasmos-provider does not accept Java-hosted host functions — "
                            + "wasmos's design is WIT-first, guest imports must be satisfied via "
                            + "wasmos's own host surfaces (host:wasmtime, wasmos:host/*). "
                            + "For Java-hosted imports, use the \"wasmtime\" provider instead.");
        }
        if (ctx.wasiNnConfig() != null) {
            throw new UnsupportedFeatureException(
                    "wasmos-provider does not surface wasi:nn (WASI Neural Networks)");
        }
        if (!ctx.externImports().isEmpty()) {
            throw new UnsupportedFeatureException(
                    "wasmos-provider does not accept externImports — the wasmos host "
                            + "linker resolves imports through its own registered surfaces");
        }
    }

    private void rejectUnsupportedComponentConfig(ComponentConfig config) {
        if (config == null) return;
        // As of the deferred-items pass every ComponentConfig field is
        // plumbed through to the outer store — nothing to reject here
        // beyond a sanity check that the caller didn't ask for a negative
        // number (which would collide with the `-1 = unset` sentinel on
        // the JNI boundary).
        rejectNegative("fuelLimit", config.fuelLimit());
        rejectNegative("maxMemoryBytes", config.maxMemoryBytes());
        rejectNegative("epochDeadline", config.epochDeadline());
        rejectNegative("maxTableElements", config.maxTableElements());
        rejectNegative("maxInstances", config.maxInstances());
        rejectNegative("maxTables", config.maxTables());
        rejectNegative("maxMemories", config.maxMemories());
        // trapOnGrowFailure is implied by any positive limit — wasmtime's
        // StoreLimits always traps on limit exceed; no separate switch. So we
        // silently accept it.
    }

    private static void rejectNegative(String name, java.util.OptionalLong opt) {
        if (opt.isPresent() && opt.getAsLong() < 0) {
            throw new IllegalArgumentException(
                    "ComponentConfig." + name + " must be >= 0; got " + opt.getAsLong()
                            + " (the wasmos-provider JNI uses -1 as the 'unset' sentinel)");
        }
    }

    private ComponentInstance instantiateInternal(LinkingContext ctx, ComponentConfig config) {
        final long inst;
        try {
            if (ctx == null && config == null) {
                inst = WasmosNative.componentInstantiate(nativeHandle());
            } else {
                inst = instantiateWithConfig(ctx, config);
            }
            if (inst == 0) {
                throw new InstantiationException(
                        "wasmos componentInstantiate returned 0 handle without throwing");
            }
            return new WasmosComponentInstanceAdapter(this, inst);
        } catch (WebAssemblyException e) {
            // Preserve upstream messages (which include the wasmos-runtime
            // error) but classify as an instantiation-time failure so
            // downstream error handling can distinguish it from invoke-time
            // failures.
            throw new InstantiationException(e.getMessage(), e);
        }
    }

    /**
     * Marshal a WasiContext + ComponentConfig into the flat argument set the
     * native {@code componentInstantiateWithConfig} shim expects, then call
     * it. See {@link WasmosNative#componentInstantiateWithConfig} for the
     * flag-bit and array-shape contract.
     */
    private long instantiateWithConfig(LinkingContext ctx, ComponentConfig config) {
        final WasiContext wasi = ctx == null ? null : ctx.wasiContext();
        int flags = 0;
        String[] args = new String[0];
        String[] envKeys = new String[0];
        String[] envVals = new String[0];
        String[] preopenHosts = new String[0];
        String[] preopenGuests = new String[0];
        byte[] preopenWritable = new byte[0];

        if (wasi != null) {
            flags |= 0x08;
            if (wasi.inheritStdin())  flags |= 0x01;
            if (wasi.inheritStdout()) flags |= 0x02;
            if (wasi.inheritStderr()) flags |= 0x04;

            final List<String> argsList = wasi.args();
            args = argsList == null ? new String[0] : argsList.toArray(new String[0]);

            final Map<String, String> envMap = wasi.env();
            if (envMap != null && !envMap.isEmpty()) {
                envKeys = new String[envMap.size()];
                envVals = new String[envMap.size()];
                int i = 0;
                for (Map.Entry<String, String> e : envMap.entrySet()) {
                    envKeys[i] = e.getKey();
                    envVals[i] = e.getValue();
                    i++;
                }
            }

            final List<String> preopens = wasi.preopenDirs();
            if (preopens != null && !preopens.isEmpty()) {
                final Map<String, String> guestMap = wasi.preopenGuestPaths();
                final List<String> readOnly = wasi.readOnlyPreopenDirs();
                final int n = preopens.size();
                preopenHosts = new String[n];
                preopenGuests = new String[n];
                preopenWritable = new byte[n];
                for (int i = 0; i < n; i++) {
                    final String host = preopens.get(i);
                    preopenHosts[i] = host;
                    final String guest = guestMap == null ? null : guestMap.get(host);
                    // Convention when no remap is supplied: guest sees the
                    // host path unchanged.
                    preopenGuests[i] = guest != null ? guest : host;
                    preopenWritable[i] = (readOnly != null && readOnly.contains(host))
                            ? (byte) 0
                            : (byte) 1;
                }
            }

            if (wasi.wasiHttpEnabled()) {
                throw new UnsupportedFeatureException(
                        "wasmos-provider does not surface wasi:http yet");
            }
            if (wasi.allowNetwork() && !wasi.egressRules().isEmpty()) {
                // wasmtime-wasi 47 sockets are all-or-nothing at the ctx
                // level; we don't yet plumb per-endpoint allow-lists.
                throw new UnsupportedFeatureException(
                        "wasmos-provider does not enforce network egress allow-lists yet");
            }
        }

        return WasmosNative.componentInstantiateWithConfig(
                nativeHandle(),
                flags,
                args,
                envKeys,
                envVals,
                preopenHosts,
                preopenGuests,
                preopenWritable,
                optLong(config == null ? null : config.maxMemoryBytes()),
                optLong(config == null ? null : config.epochDeadline()),
                optLong(config == null ? null : config.fuelLimit()),
                optLong(config == null ? null : config.maxTableElements()),
                optLong(config == null ? null : config.maxInstances()),
                optLong(config == null ? null : config.maxTables()),
                optLong(config == null ? null : config.maxMemories()));
    }

    /** {@code -1} sentinel for absent OptionalLongs — matches the JNI
     *  convention documented on
     *  {@link WasmosNative#componentInstantiateWithConfig}. */
    private static long optLong(java.util.OptionalLong opt) {
        return (opt != null && opt.isPresent()) ? opt.getAsLong() : -1L;
    }

    @Override
    public List<String> exportedInterfaces() {
        // The MVP JNI surface only lists exported functions, not the
        // component's exported interfaces (which requires walking the
        // component-type's nested Instance items). Return empty rather
        // than pretending; callers doing introspection should probe
        // exports directly against the instance.
        return Collections.emptyList();
    }

    @Override
    public List<String> importedInterfaces() {
        return Collections.emptyList();
    }

    @Override
    public boolean exportsInterface(String name) {
        return false;
    }

    @Override
    public boolean importsInterface(String name) {
        return false;
    }

    /**
     * Serialize the compiled component into wasmtime's cached AOT byte
     * representation. Bytes can be fed to
     * {@link WasmosEngineAdapter#deserializeComponent(byte[])} on a
     * compatible engine to skip re-compiling — the primary use case is
     * warm-restart / hot-reload where the same component gets loaded
     * repeatedly across process launches.
     *
     * <p>Byte-compat scope: tied to wasmtime version, engine
     * {@code Config}, and target ISA. A mismatched engine surfaces a
     * clean deserialize error rather than silently misbehaving.
     */
    @Override
    public byte[] serialize() {
        final byte[] bytes = WasmosNative.componentSerialize(nativeHandle());
        if (bytes == null) {
            // The JNI shim throws WebAssemblyException on failure; null
            // here would be a contract violation on the Rust side —
            // convert to something diagnosable rather than a nondescript
            // NPE downstream.
            throw new WebAssemblyException(
                    "wasmos componentSerialize returned null without throwing");
        }
        return bytes;
    }

    /**
     * Returns the component's exported function names. Not part of the
     * {@link Component} API but useful for tests / debugging — callers can
     * grab it via {@code component.exportedFunctionNames()} from a cast
     * reference.
     */
    List<String> exportedFunctionNames() {
        final byte[] blob = WasmosNative.componentExportedFunctionNames(nativeHandle());
        if (blob == null || blob.length == 0) {
            return Collections.emptyList();
        }
        final String joined = new String(blob, StandardCharsets.UTF_8);
        // Rust side joins on NUL (0x00); splitting on it recovers the names.
        // Previous code split on space, which silently coalesced multi-name
        // exports into one — invisible only when a component happens to
        // export exactly one function.
        final String[] parts = joined.split("\0");
        final List<String> out = new ArrayList<>(parts.length);
        for (String p : parts) {
            if (!p.isEmpty()) {
                out.add(p);
            }
        }
        return out;
    }

    WasmosEngineAdapter engine() {
        return engine;
    }

    @Override
    public synchronized void close() {
        if (closed) {
            return;
        }
        closed = true;
        WasmosNative.componentClose(handle);
    }
}
