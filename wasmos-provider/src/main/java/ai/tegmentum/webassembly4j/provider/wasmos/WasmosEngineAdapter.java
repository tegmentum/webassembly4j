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
import ai.tegmentum.webassembly4j.api.Engine;
import ai.tegmentum.webassembly4j.api.EngineCapabilities;
import ai.tegmentum.webassembly4j.api.EngineInfo;
import ai.tegmentum.webassembly4j.api.Module;
import ai.tegmentum.webassembly4j.api.exception.UnsupportedFeatureException;
import ai.tegmentum.webassembly4j.api.exception.WebAssemblyException;
import ai.tegmentum.webassembly4j.provider.wasmos.jni.WasmosNative;

import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * {@link Engine} adapter — thin Java-side owner of a native wasmos engine
 * handle. Lifecycle model:
 *
 * <ul>
 *   <li>{@link #create()} allocates the handle (which itself allocates a
 *       wasmtime {@code Engine} + a persistent Tokio runtime).</li>
 *   <li>{@link #loadComponent(byte[])} produces a
 *       {@link WasmosComponentAdapter} sharing this engine.</li>
 *   <li>{@link #close()} releases the native handle. Any components /
 *       instances still alive at that point produce undefined behaviour on
 *       subsequent use — mirroring wasmtime4j-provider's contract.</li>
 * </ul>
 *
 * <p>Core-module loading is intentionally unsupported: wasmos-runtime is a
 * component-model host. Callers that just want core wasm should pick the
 * {@code "wasmtime"} provider instead.
 */
final class WasmosEngineAdapter implements Engine {

    private final long handle;
    private volatile boolean closed = false;
    /**
     * Lazily-created worker pool for
     * {@link ai.tegmentum.webassembly4j.provider.wasmos.ext.WasmosAsyncExtension#invokeAsync}.
     * Null until first use; shut down on {@link #close()}. See
     * {@link WasmosAsyncExecutors} for sizing rationale.
     */
    private volatile ThreadPoolExecutor asyncPool;

    private WasmosEngineAdapter(final long handle) {
        this.handle = handle;
    }

    static WasmosEngineAdapter create() {
        final long h = WasmosNative.engineCreate();
        if (h == 0) {
            throw new WebAssemblyException(
                    "wasmos engineCreate returned 0 handle without throwing");
        }
        return new WasmosEngineAdapter(h);
    }

    long nativeHandle() {
        if (closed) {
            throw new IllegalStateException("wasmos engine has been closed");
        }
        return handle;
    }

    @Override
    public EngineInfo info() {
        return new WasmosEngineInfo();
    }

    @Override
    public EngineCapabilities capabilities() {
        return new WasmosEngineCapabilities();
    }

    /**
     * Core modules are intentionally unsupported — wasmos-runtime exposes
     * only the component-model surface. Callers that need core-wasm
     * hosting should select the {@code "wasmtime"} provider instead.
     */
    @Override
    public Module loadModule(byte[] bytes) {
        throw new UnsupportedFeatureException(
                "wasmos-provider does not host core wasm modules; "
                        + "use the \"wasmtime\" provider for core-module hosting");
    }

    @Override
    public Component loadComponent(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            throw new IllegalArgumentException("component bytes must be non-empty");
        }
        final long compHandle = WasmosNative.componentLoad(nativeHandle(), bytes);
        if (compHandle == 0) {
            throw new WebAssemblyException(
                    "wasmos componentLoad returned 0 handle without throwing");
        }
        return new WasmosComponentAdapter(this, compHandle);
    }

    /**
     * Deserialize a component from bytes previously produced by
     * {@link WasmosComponentAdapter#serialize()} on a compatible engine.
     * Skips the compile step — useful for warm-restart / hot-reload
     * scenarios.
     *
     * <p><strong>Trust boundary:</strong> the underlying wasmtime
     * {@code Component::deserialize} is {@code unsafe} — it assumes
     * the bytes are a well-formed AOT image from a compatible engine.
     * Passing arbitrary or tampered bytes is undefined behaviour on
     * the Rust side. Callers must trust the byte provenance.
     */
    Component deserializeComponent(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            throw new IllegalArgumentException("serialized bytes must be non-empty");
        }
        final long compHandle = WasmosNative.componentDeserialize(nativeHandle(), bytes);
        if (compHandle == 0) {
            throw new WebAssemblyException(
                    "wasmos componentDeserialize returned 0 handle without throwing");
        }
        return new WasmosComponentAdapter(this, compHandle);
    }

    @Override
    public <T> Optional<T> extension(Class<T> extensionType) {
        return Optional.empty();
    }

    @Override
    public <T> Optional<T> unwrap(Class<T> nativeType) {
        // The native handle is a plain long; there's no Java-side object
        // to hand out. A future refactor could expose HostState / caps
        // configuration through this hook.
        return Optional.empty();
    }

    /**
     * Package-private accessor for the shared async pool. Instantiated on
     * first call (double-checked locking) so engines that never dispatch
     * async invokes pay no thread-pool overhead. Returns the underlying
     * {@link ExecutorService} — callers should NOT shut it down; the engine
     * owns its lifecycle and stops it in {@link #close()}.
     */
    ExecutorService asyncExecutor() {
        ThreadPoolExecutor local = asyncPool;
        if (local == null) {
            synchronized (this) {
                local = asyncPool;
                if (local == null) {
                    if (closed) {
                        throw new IllegalStateException(
                                "wasmos engine has been closed; cannot start async executor");
                    }
                    local = WasmosAsyncExecutors.newEnginePool();
                    asyncPool = local;
                }
            }
        }
        return local;
    }

    @Override
    public synchronized void close() {
        if (closed) {
            return;
        }
        closed = true;
        // Stop accepting new async work first. Existing tasks finish on
        // their worker threads — we don't interrupt because a blocking JNI
        // invoke ignores Thread.interrupt() and interrupting would just
        // leave stale interrupt state on the worker.
        final ThreadPoolExecutor local = asyncPool;
        if (local != null) {
            asyncPool = null;
            local.shutdown();
            try {
                // Bounded wait so close() doesn't hang on a runaway invoke;
                // 2s is enough for well-behaved workloads to drain and short
                // enough that a hung guest doesn't block engine teardown.
                if (!local.awaitTermination(2, TimeUnit.SECONDS)) {
                    local.shutdownNow();
                }
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                local.shutdownNow();
            }
        }
        WasmosNative.engineClose(handle);
    }
}
