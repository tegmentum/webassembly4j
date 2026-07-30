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

import ai.tegmentum.webassembly4j.api.ComponentInstance;
import ai.tegmentum.webassembly4j.api.Function;
import ai.tegmentum.webassembly4j.api.Global;
import ai.tegmentum.webassembly4j.api.Memory;
import ai.tegmentum.webassembly4j.api.Table;
import ai.tegmentum.webassembly4j.api.exception.ExecutionException;
import ai.tegmentum.webassembly4j.api.exception.WebAssemblyException;
import ai.tegmentum.webassembly4j.provider.wasmos.ext.WasmosAsyncExtension;
import ai.tegmentum.webassembly4j.provider.wasmos.ext.WitErrorContextException;
import ai.tegmentum.webassembly4j.provider.wasmos.jni.WasmosNative;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

/**
 * Adapter over a native wasmos component instance handle.
 *
 * <p>Invoke path:
 * <ul>
 *   <li>{@link #invoke(String, Object...)} — general path via JSON
 *       marshalling ({@link WasmosMarshalling}). Handles primitives (bool,
 *       s8/s16/s32/s64, u8/u16/u32/u64 via wrappers, f32/f64, char),
 *       string, list, record, tuple, option, result, variant, enum, flags,
 *       plus a byte[] fast path for {@code list<u8>}. Wasmtime type-checks
 *       against the exported function's WIT signature on the Rust side.</li>
 *   <li>{@link #invokeBytes(String, Object...)} — byte[] fast path for a
 *       single {@code list<u8>} (or {@code string}) return. Skips the
 *       JSON detour on the return path.</li>
 *   <li>{@link #invokeWit(String, Object...)} — currently identical to
 *       {@link #invoke(String, Object...)}. A future WIT-value overload
 *       would branch here.</li>
 * </ul>
 *
 * <p>The old nullary-i32 fast path is preserved internally: any
 * {@code invoke(name)} whose exported function has arity {@code 0:1} and
 * whose result parses cleanly as an {@code S32} takes it, saving the JSON
 * round-trip on the demo path.
 */
final class WasmosComponentInstanceAdapter implements ComponentInstance {

    private final WasmosComponentAdapter component;
    private final long handle;
    private volatile boolean closed = false;

    WasmosComponentInstanceAdapter(final WasmosComponentAdapter component, final long handle) {
        this.component = component;
        this.handle = handle;
    }

    private long nativeHandle() {
        if (closed) {
            throw new IllegalStateException("wasmos component instance has been closed");
        }
        return handle;
    }

    @Override
    public Object invoke(String functionName, Object... args) {
        if (functionName == null || functionName.isEmpty()) {
            throw new IllegalArgumentException("functionName must be non-empty");
        }
        try {
            // Fast path: preserve the pre-marshalling `run() -> i32` shape for
            // the demo test. Only applies when caller passes no args AND the
            // exported function's arity is exactly (0 args, 1 result).
            if (args == null || args.length == 0) {
                final ArityInfo arity = readArity(functionName);
                if (arity != null && arity.args == 0 && arity.results == 1) {
                    try {
                        return WasmosNative.instanceInvokeReturningI32(nativeHandle(), functionName);
                    } catch (WebAssemblyException wex) {
                        // Signature mismatch (not i32-returning) — fall back
                        // to the general JSON path. Don't leak the fast-path
                        // failure to the caller: it's an implementation detail.
                    }
                }
            }
            final String argsJson = WasmosMarshalling.marshalArgs(args);
            final String resultJson = WasmosNative.instanceInvokeJson(
                    nativeHandle(), functionName, argsJson);
            final List<Object> results = WasmosMarshalling.unmarshalResults(resultJson);
            return unwrapResults(results);
        } catch (WebAssemblyException e) {
            throw new ExecutionException(
                    "wasmos invoke failed for '" + functionName + "': " + e.getMessage(), e);
        }
    }

    @Override
    public byte[] invokeBytes(String functionName, Object... args) {
        if (functionName == null || functionName.isEmpty()) {
            throw new IllegalArgumentException("functionName must be non-empty");
        }
        try {
            final String argsJson = WasmosMarshalling.marshalArgs(args);
            return WasmosNative.instanceInvokeJsonReturningBytes(
                    nativeHandle(), functionName, argsJson);
        } catch (WebAssemblyException e) {
            throw new ExecutionException(
                    "wasmos invokeBytes failed for '" + functionName + "': " + e.getMessage(), e);
        }
    }

    // invokeWit currently mirrors invoke. Kept as an override rather than
    // inheriting the default so future WIT-shape returns (records / variants
    // with typed carriers) can diverge here without touching invoke's
    // simpler natural-shape contract.
    @Override
    public Object invokeWit(String functionName, Object... args) {
        return invoke(functionName, args);
    }

    /**
     * Result flattener — the shape callers expect from {@link ComponentInstance#invoke}
     * for the return arity varies:
     * <ul>
     *   <li>arity 0 → null</li>
     *   <li>arity 1 → the sole value directly</li>
     *   <li>arity 2+ → a {@link List} of values</li>
     * </ul>
     */
    private static Object unwrapResults(List<Object> results) {
        if (results.isEmpty()) return null;
        if (results.size() == 1) return results.get(0);
        return results;
    }

    /** Package-private for tests + `invoke` fast-path arity probe. */
    ArityInfo readArity(String name) {
        try {
            final byte[] blob = WasmosNative.instanceFunctionArity(nativeHandle(), name);
            if (blob == null) return null;
            final String s = new String(blob, StandardCharsets.UTF_8);
            final int idx = s.indexOf(':');
            if (idx < 0) return null;
            return new ArityInfo(
                    Integer.parseInt(s.substring(0, idx)),
                    Integer.parseInt(s.substring(idx + 1)));
        } catch (Throwable t) {
            return null;
        }
    }

    static final class ArityInfo {
        final int args;
        final int results;

        ArityInfo(int args, int results) {
            this.args = args;
            this.results = results;
        }
    }

    @Override
    public boolean hasFunction(String name) {
        return WasmosNative.instanceHasFunction(nativeHandle(), name);
    }

    @Override
    public List<String> exportedFunctions() {
        return component.exportedFunctionNames();
    }

    @Override
    public List<String> exportedInterfaces() {
        return component.exportedInterfaces();
    }

    @Override
    public boolean exportsInterface(String name) {
        return component.exportsInterface(name);
    }

    // Component instances don't expose core module exports; empty is the
    // documented convention (see webassembly4j api ComponentInstance javadoc).

    @Override
    public Optional<Function> function(String name) { return Optional.empty(); }

    @Override
    public Optional<Memory> memory(String name)    { return Optional.empty(); }

    @Override
    public Optional<Table> table(String name)      { return Optional.empty(); }

    @Override
    public Optional<Global> global(String name)    { return Optional.empty(); }

    @Override
    public <T> Optional<T> unwrap(Class<T> nativeType) {
        // Bare-long handle only; no Java-object native representation to expose.
        return Optional.empty();
    }

    /**
     * Provider-specific extension surface. Currently exposes
     * {@link WasmosAsyncExtension} for the slice-1 async invoke path;
     * later slices will add stream / error-context extensions here.
     */
    @Override
    @SuppressWarnings("unchecked")
    public <T> Optional<T> extension(Class<T> extensionType) {
        if (extensionType == WasmosAsyncExtension.class) {
            return Optional.of((T) new AsyncExtensionImpl());
        }
        return Optional.empty();
    }

    // ---- Async invoke ------------------------------------------------------

    /**
     * Package-private async invoke entry — schedules the blocking JNI
     * {@link #invoke} on the engine's shared {@code wasmos-provider-async}
     * pool and returns a cancellation-aware {@link CompletableFuture}.
     * Exposed publicly through {@link WasmosAsyncExtension}.
     */
    CompletableFuture<Object> invokeAsync(String functionName, Object... args) {
        if (functionName == null || functionName.isEmpty()) {
            throw new IllegalArgumentException("functionName must be non-empty");
        }
        final ExecutorService pool = component.engine().asyncExecutor();
        final CancelAwareFuture<Object> cf = new CancelAwareFuture<>(component.engine());
        pool.execute(() -> {
            if (cf.isCancelled()) return; // dropped before the worker picked it up
            try {
                cf.complete(invoke(functionName, args));
            } catch (Throwable t) {
                cf.completeExceptionally(t);
            }
        });
        return cf;
    }

    /** Byte-array-returning counterpart, mirroring {@link #invokeBytes}. */
    CompletableFuture<byte[]> invokeBytesAsync(String functionName, Object... args) {
        if (functionName == null || functionName.isEmpty()) {
            throw new IllegalArgumentException("functionName must be non-empty");
        }
        final ExecutorService pool = component.engine().asyncExecutor();
        final CancelAwareFuture<byte[]> cf = new CancelAwareFuture<>(component.engine());
        pool.execute(() -> {
            if (cf.isCancelled()) return;
            try {
                cf.complete(invokeBytes(functionName, args));
            } catch (Throwable t) {
                cf.completeExceptionally(t);
            }
        });
        return cf;
    }

    /**
     * CompletableFuture subclass that fires a best-effort
     * {@code engineIncrementEpoch} nudge on cancellation. See the
     * {@link WasmosAsyncExtension} class-level javadoc for the exact
     * semantics — the nudge only reliably traps guests instantiated with
     * an epoch deadline; guests without a deadline finish their WASM call
     * regardless. The {@link CompletableFuture} promise is always marked
     * cancelled so downstream chains stop propagating.
     */
    private static final class CancelAwareFuture<T> extends CompletableFuture<T> {
        private final WasmosEngineAdapter engine;

        CancelAwareFuture(WasmosEngineAdapter engine) {
            this.engine = engine;
        }

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            final boolean wasCancelled = super.cancel(mayInterruptIfRunning);
            if (wasCancelled && mayInterruptIfRunning) {
                // Best-effort nudge — safe on a shared engine (only bumps a
                // counter, doesn't touch store state). Wrapped in try/catch
                // so a failure here (e.g. engine closed racing with cancel)
                // never leaks past the Future contract.
                try {
                    WasmosNative.engineIncrementEpoch(engine.nativeHandle());
                } catch (Throwable ignored) {
                    // Swallow — cancellation is best-effort by contract.
                }
            }
            return wasCancelled;
        }
    }

    /**
     * Trivial delegate to the enclosing adapter — separate class so the
     * public {@link WasmosAsyncExtension} interface doesn't require the
     * adapter itself to be public.
     */
    private final class AsyncExtensionImpl implements WasmosAsyncExtension {
        @Override
        public CompletableFuture<Object> invokeAsync(String functionName, Object... args) {
            return WasmosComponentInstanceAdapter.this.invokeAsync(functionName, args);
        }

        @Override
        public CompletableFuture<byte[]> invokeBytesAsync(String functionName, Object... args) {
            return WasmosComponentInstanceAdapter.this.invokeBytesAsync(functionName, args);
        }

        @Override
        public CompletableFuture<Object> awaitFuture(Object witFuture) {
            // Route through the async pool for shape parity with the other
            // await-flavoured entry points — callers get a CompletableFuture
            // that they can chain regardless of underlying blocking semantics.
            // The JNI call is expected to throw (wasmtime API gap); we
            // surface that as an exceptional completion rather than a
            // synchronous throw so `thenApply` / `handle` chains behave
            // uniformly and the failure isn't observed off the calling
            // thread's stack.
            final CompletableFuture<Object> cf = new CompletableFuture<>();
            if (!(witFuture instanceof WasmosMarshalling.WitFuture)) {
                cf.completeExceptionally(new IllegalArgumentException(
                        "awaitFuture expects a WitFuture; got "
                                + (witFuture == null ? "null" : witFuture.getClass().getName())));
                return cf;
            }
            final WasmosMarshalling.WitFuture wf = (WasmosMarshalling.WitFuture) witFuture;
            component.engine().asyncExecutor().execute(() -> {
                try {
                    final String json = WasmosNative.futureAwait(nativeHandle(), wf.tableId());
                    // If wasmtime ever grows a dynamic await, the JNI method
                    // returns the JSON blob for the resolved value; wrap it
                    // through the same unmarshaller as invoke() so callers
                    // get natural Java types.
                    final List<Object> results = WasmosMarshalling.unmarshalResults(json);
                    cf.complete(unwrapResults(results));
                } catch (Throwable t) {
                    cf.completeExceptionally(new ExecutionException(
                            "wasmos awaitFuture failed for tableId=" + wf.tableId()
                                    + ": " + t.getMessage(), t));
                }
            });
            return cf;
        }

        @Override
        public void closeFuture(Object witFuture) {
            if (!(witFuture instanceof WasmosMarshalling.WitFuture)) {
                throw new IllegalArgumentException(
                        "closeFuture expects a WitFuture; got "
                                + (witFuture == null ? "null" : witFuture.getClass().getName()));
            }
            final WasmosMarshalling.WitFuture wf = (WasmosMarshalling.WitFuture) witFuture;
            try {
                WasmosNative.futureClose(nativeHandle(), wf.tableId());
            } catch (WebAssemblyException e) {
                throw new ExecutionException(
                        "wasmos closeFuture failed for tableId=" + wf.tableId()
                                + ": " + e.getMessage(), e);
            }
        }

        @Override
        public CompletableFuture<Object> readStream(Object witStream) {
            // Same shape as awaitFuture — route through the async pool so
            // callers see uniform CompletableFuture semantics. The JNI is
            // expected to throw (wasmtime API gap) and we surface that as
            // an exceptional completion.
            final CompletableFuture<Object> cf = new CompletableFuture<>();
            if (!(witStream instanceof WasmosMarshalling.WitStream)) {
                cf.completeExceptionally(new IllegalArgumentException(
                        "readStream expects a WitStream; got "
                                + (witStream == null ? "null" : witStream.getClass().getName())));
                return cf;
            }
            final WasmosMarshalling.WitStream ws = (WasmosMarshalling.WitStream) witStream;
            component.engine().asyncExecutor().execute(() -> {
                try {
                    final String json = WasmosNative.streamRead(nativeHandle(), ws.tableId());
                    // Future-proofing: when wasmtime exposes a dynamic read
                    // surface, the JNI is expected to hand back the
                    // resolved value(s) as a JSON blob using the same
                    // marshalling schema as invokeAsync's results. Callers
                    // get the natural Java-typed value(s).
                    final List<Object> results = WasmosMarshalling.unmarshalResults(json);
                    cf.complete(unwrapResults(results));
                } catch (Throwable t) {
                    cf.completeExceptionally(new ExecutionException(
                            "wasmos readStream failed for tableId=" + ws.tableId()
                                    + ": " + t.getMessage(), t));
                }
            });
            return cf;
        }

        @Override
        public void closeStream(Object witStream) {
            if (!(witStream instanceof WasmosMarshalling.WitStream)) {
                throw new IllegalArgumentException(
                        "closeStream expects a WitStream; got "
                                + (witStream == null ? "null" : witStream.getClass().getName()));
            }
            final WasmosMarshalling.WitStream ws = (WasmosMarshalling.WitStream) witStream;
            try {
                WasmosNative.streamClose(nativeHandle(), ws.tableId());
            } catch (WebAssemblyException e) {
                throw new ExecutionException(
                        "wasmos closeStream failed for tableId=" + ws.tableId()
                                + ": " + e.getMessage(), e);
            }
        }

        @Override
        public void closeErrorContext(Object witErrorContext) {
            if (!(witErrorContext instanceof WasmosMarshalling.WitErrorContext)) {
                throw new IllegalArgumentException(
                        "closeErrorContext expects a WitErrorContext; got "
                                + (witErrorContext == null ? "null"
                                        : witErrorContext.getClass().getName()));
            }
            final WasmosMarshalling.WitErrorContext we =
                    (WasmosMarshalling.WitErrorContext) witErrorContext;
            try {
                WasmosNative.errorContextClose(nativeHandle(), we.tableId());
            } catch (WebAssemblyException e) {
                throw new ExecutionException(
                        "wasmos closeErrorContext failed for tableId=" + we.tableId()
                                + ": " + e.getMessage(), e);
            }
        }

        @Override
        public WitErrorContextException wrapErrorContext(Object witErrorContext) {
            if (!(witErrorContext instanceof WasmosMarshalling.WitErrorContext)) {
                throw new IllegalArgumentException(
                        "wrapErrorContext expects a WitErrorContext; got "
                                + (witErrorContext == null ? "null"
                                        : witErrorContext.getClass().getName()));
            }
            final WasmosMarshalling.WitErrorContext we =
                    (WasmosMarshalling.WitErrorContext) witErrorContext;
            // Message includes tableId + rep so the exception's message alone
            // is diagnosable without pulling the carrier out via
            // errorContext() — matches the "cheap first look" ergonomics of
            // ExecutionException elsewhere in the provider.
            return new WitErrorContextException(
                    "wasmos guest returned error-context (tableId=" + we.tableId()
                            + ", rep=" + we.rep() + ')',
                    we);
        }
    }

    /**
     * Instances are dropped implicitly with the component (the wasmos
     * runtime keeps them together). The API doesn't declare
     * {@code close()} here, so we free the native handle via a
     * package-private cleanup hook driven from
     * {@link WasmosComponentAdapter#close()}.
     */
    synchronized void closeInternal() {
        if (closed) {
            return;
        }
        closed = true;
        WasmosNative.instanceClose(handle);
    }
}
