/*
 * Copyright 2026 Tegmentum AI
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package ai.tegmentum.webassembly4j.provider.wasmos.ext;

import java.util.concurrent.CompletableFuture;

/**
 * Provider-specific async extension exposed by the {@code wasmos} engine
 * provider. Obtain from a wasmos-backed {@link ai.tegmentum.webassembly4j.api.ComponentInstance}
 * via
 * <pre>
 *     instance.extension(WasmosAsyncExtension.class)
 *             .ifPresent(async -&gt; async.invokeAsync("run").thenAccept(System.out::println));
 * </pre>
 *
 * <p>This is the slice-1 (Option C hybrid) async surface: {@code Val::Future},
 * {@code Val::Stream}, and {@code Val::ErrorContext} marshalling live in
 * later slices. What ships here is a Java-side {@link CompletableFuture}
 * wrapper around the blocking JNI invoke path — Java consumers preferring
 * async ergonomics can chain component invocations non-blockingly while the
 * work runs on a dedicated worker pool.
 *
 * <p><b>Threading:</b> Each invocation is scheduled onto the engine's
 * shared {@code wasmos-provider-async} pool (bounded cached executor, daemon
 * threads). The store is Rust-side {@code Mutex}-protected, so concurrent
 * invocations against the <em>same</em> {@link ai.tegmentum.webassembly4j.api.ComponentInstance}
 * still serialize; only work targeting different instances runs in
 * parallel. Callers doing sustained parallel workloads should instantiate
 * one component per parallel stream of calls.
 *
 * <p><b>Cancellation:</b> {@code future.cancel(true)} on the returned
 * future is <b>best-effort</b>. Semantics:
 * <ul>
 *   <li>If the future has not yet started running on the worker pool
 *       (still queued), it is dropped and the WASM invoke never runs.</li>
 *   <li>If the future has started, the Java-side promise is marked
 *       cancelled and downstream {@code thenApply} / {@code thenCompose}
 *       chains stop propagating. The provider additionally invokes
 *       {@link ai.tegmentum.webassembly4j.provider.wasmos.jni.WasmosNative#engineIncrementEpoch}
 *       on the underlying engine as a nudge — this reliably traps the
 *       running invoke only when the instance was instantiated with an
 *       epoch deadline (via {@link ai.tegmentum.webassembly4j.api.config.ComponentConfig#epochDeadline}).
 *       Instances without an epoch deadline will finish their current
 *       WASM call to completion; the {@link CompletableFuture} contract
 *       still returns {@code true} from {@code cancel} to signal that the
 *       downstream chain will not observe the result.</li>
 * </ul>
 *
 * <p>Callers that need authoritative cancellation should always instantiate
 * with a non-negative {@code epochDeadline}. The nudge above is safe on a
 * shared engine — {@code engineIncrementEpoch} only bumps the counter, it
 * doesn't touch any store state.
 *
 * @since 2.5.2
 */
public interface WasmosAsyncExtension {

    /**
     * Async counterpart of {@link ai.tegmentum.webassembly4j.api.ComponentInstance#invoke}.
     * Returns a {@link CompletableFuture} that completes with the invoke
     * result on the {@code wasmos-provider-async} worker pool, or
     * completes exceptionally with the wrapping
     * {@link ai.tegmentum.webassembly4j.api.exception.ExecutionException}
     * that the sync path would have thrown.
     *
     * <p>Argument / return marshalling is identical to the sync path — see
     * {@link ai.tegmentum.webassembly4j.api.ComponentInstance#invoke} for
     * the type-mapping table.
     *
     * <p>See the class-level javadoc for cancellation semantics; the
     * returned future's {@code cancel(true)} is best-effort and its
     * authoritative-ness depends on whether the instance was instantiated
     * with an epoch deadline.
     */
    CompletableFuture<Object> invokeAsync(String functionName, Object... args);

    /**
     * Async counterpart of
     * {@link ai.tegmentum.webassembly4j.api.ComponentInstance#invokeBytes}.
     * Skips the JSON detour on the return path for functions returning
     * {@code list<u8>} or {@code string}.
     *
     * <p>Cancellation semantics mirror {@link #invokeAsync}.
     */
    CompletableFuture<byte[]> invokeBytesAsync(String functionName, Object... args);

    /**
     * Await a parked {@code WitFuture} (a {@code future<T>} handle returned
     * by an earlier guest call) and complete with its resolved value.
     *
     * <p><b>Currently unsupported due to a wasmtime 47 public-API gap.</b>
     * The returned {@link CompletableFuture} always completes exceptionally
     * with an
     * {@link ai.tegmentum.webassembly4j.api.exception.WebAssemblyException}
     * explaining the gap.
     *
     * <p>The wasmtime 47 {@code FutureAny} API only exposes:
     * <ul>
     *   <li>{@code try_into_future_reader::<T>()} — requires a compile-time
     *       {@code T}. Incompatible with our runtime-typed JSON marshalling.</li>
     *   <li>{@code close(store)} — plain disposal, no value extraction.</li>
     * </ul>
     * {@code FutureConsumer::Item} is likewise a compile-time associated
     * type, so there's no type-erased await surface upstream today.
     *
     * <p>The wrapping method is stable in shape: when wasmtime exposes a
     * dynamic await API upstream, only the JNI body and this method's
     * implementation change; callers using
     * {@code future.awaitFuture(handle).thenApply(...)} chains keep working
     * without recompilation.
     *
     * <p>In the meantime, callers can still round-trip a {@code WitFuture}
     * through a guest import that consumes it (that path IS wired
     * end-to-end via {@link #invokeAsync}), or dispose of it via
     * {@link #closeFuture(Object)}.
     *
     * @param witFuture a {@code WitFuture} produced by the marshalling
     *     layer as a return value of an earlier invoke call; passed as
     *     {@link Object} to avoid leaking the package-private carrier type
     *     into the public API.
     */
    CompletableFuture<Object> awaitFuture(Object witFuture);

    /**
     * Close a parked {@code WitFuture} — evicts it from the Rust-side
     * future registry and calls wasmtime's {@code FutureAny::close(store)}.
     * Should always be called in a {@code finally} block when a caller
     * receives a {@code WitFuture} but doesn't pass it back to a guest
     * import (which would consume the parked entry naturally).
     *
     * <p>Throws
     * {@link ai.tegmentum.webassembly4j.api.exception.WebAssemblyException}
     * if the slot id is unknown (already closed, or transferred to a guest).
     *
     * @param witFuture a {@code WitFuture} produced by the marshalling
     *     layer, passed as {@link Object} to avoid leaking the carrier type.
     */
    void closeFuture(Object witFuture);

    /**
     * Read a parked {@code WitStream} and complete with a materialised
     * value.
     *
     * <p><b>Currently unsupported due to a wasmtime 47 public-API gap.</b>
     * Structurally identical to {@link #awaitFuture} — the returned
     * {@link CompletableFuture} always completes exceptionally with a
     * {@link ai.tegmentum.webassembly4j.api.exception.WebAssemblyException}
     * explaining the gap.
     *
     * <p>Wasmtime 47's {@code StreamAny} API only exposes
     * {@code try_into_stream_reader::<T>()} (compile-time typed) and
     * {@code close(store)}. There is no dynamic-payload-type read/poll
     * surface, so runtime-typed marshalling can't lift stream items to
     * Java. We deliberately expose this as a single-value
     * {@code CompletableFuture} (rather than a {@link java.util.concurrent.Flow.Publisher})
     * because the underlying gap means only the "hit the wall" signal is
     * actionable today — a multi-value publisher over a
     * gap-that-throws-immediately would be over-engineering. When
     * wasmtime lands a dynamic read surface upstream, this shape can be
     * revisited (either evolved to return a publisher, or paired with a
     * new streaming-shaped method) without breaking the current no-op
     * contract.
     *
     * <p>In the meantime, callers can still round-trip a {@code WitStream}
     * through a guest import that consumes it (that path IS wired
     * end-to-end via {@link #invokeAsync}), or dispose of it via
     * {@link #closeStream(Object)}.
     *
     * @param witStream a {@code WitStream} produced by the marshalling
     *     layer as a return value of an earlier invoke call; passed as
     *     {@link Object} to avoid leaking the package-private carrier type.
     */
    CompletableFuture<Object> readStream(Object witStream);

    /**
     * Close a parked {@code WitStream} — evicts it from the Rust-side
     * stream registry and calls wasmtime's {@code StreamAny::close(store)}.
     * Mirrors {@link #closeFuture}.
     *
     * <p>Throws
     * {@link ai.tegmentum.webassembly4j.api.exception.WebAssemblyException}
     * if the slot id is unknown (already closed or transferred to a guest).
     *
     * @param witStream a {@code WitStream} produced by the marshalling
     *     layer, passed as {@link Object} to avoid leaking the carrier type.
     */
    void closeStream(Object witStream);

    /**
     * Close a parked {@code WitErrorContext} — evicts it from the
     * Rust-side error-context registry. Wasmtime 47's
     * {@code ErrorContextAny} has no publicly-defined dispose surface
     * (see wasmtime's {@code FIXME(#11161)}), so this is a pure
     * eviction — no wasmtime-side call is made. Callers who received a
     * {@code WitErrorContext} but don't pass it back to a guest should
     * call this in a {@code finally} block to keep the registry from
     * growing unbounded.
     *
     * <p>Throws
     * {@link ai.tegmentum.webassembly4j.api.exception.ExecutionException}
     * if the slot id is unknown (already closed or transferred to a guest).
     *
     * @param witErrorContext a {@code WitErrorContext} produced by the
     *     marshalling layer, passed as {@link Object} to avoid leaking
     *     the carrier type.
     */
    void closeErrorContext(Object witErrorContext);

    /**
     * Convenience wrapper that transforms a Java-visible
     * {@code WitErrorContext} into a
     * {@link WitErrorContextException} so callers can complete a
     * downstream {@link CompletableFuture} exceptionally with an
     * inspectable exception type.
     *
     * <p>Intended usage — inside a {@code handle(...)} or {@code compose(...)}
     * chain, when the caller has parsed a {@code result<T, error-context>}
     * return value and wants to lift its {@code err} branch into a
     * standard exception path:
     *
     * <pre>
     * invokeAsync("get-thing")
     *     .thenApply(v -&gt; {
     *         if (v instanceof WasmosMarshalling.WitResult) {
     *             final var r = (WasmosMarshalling.WitResult) v;
     *             if (!r.isOk &amp;&amp; r.err instanceof WasmosMarshalling.WitErrorContext) {
     *                 throw async.wrapErrorContext((WasmosMarshalling.WitErrorContext) r.err);
     *             }
     *             return r.ok;
     *         }
     *         return v;
     *     });
     * </pre>
     *
     * <p>The parameter is typed as {@link Object} because the concrete
     * {@code WasmosMarshalling.WitErrorContext} type is package-private
     * on purpose.
     *
     * @param witErrorContext an opaque {@code WitErrorContext} produced
     *     by the marshalling layer
     * @return a {@link WitErrorContextException} carrying the handle;
     *     ready to be thrown from a {@code CompletableFuture}
     *     transformer to trigger exceptional completion
     * @throws IllegalArgumentException if {@code witErrorContext} isn't a
     *     recognised WitErrorContext carrier
     */
    WitErrorContextException wrapErrorContext(Object witErrorContext);
}
