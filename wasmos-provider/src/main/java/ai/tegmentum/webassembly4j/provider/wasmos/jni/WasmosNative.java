/*
 * Copyright 2026 Tegmentum AI
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package ai.tegmentum.webassembly4j.provider.wasmos.jni;

/**
 * Raw JNI surface into the wasmos-provider Rust cdylib (see
 * {@code wasmos-provider/native/src/lib.rs}). Every method here maps 1:1 to
 * an {@code extern "C"} fn on the Rust side; the Rust side throws
 * {@link ai.tegmentum.webassembly4j.api.exception.WebAssemblyException} on
 * failure and returns a sentinel value (0 for {@code long}, {@code null} for
 * arrays / strings).
 *
 * <p>All handles are opaque {@code long} pointers to Rust-heap-owned
 * structs. Every {@code *Create} / {@code *Load} / {@code *Instantiate}
 * call must be paired with the corresponding {@code *Close} call —
 * failure to do so leaks a wasmtime Engine or Store, and (in the case of
 * the engine handle) a Tokio runtime with a worker-thread pool.
 *
 * <p>Package-private on purpose: this is not part of the provider's public
 * API. Callers should use the framework-neutral webassembly4j
 * {@link ai.tegmentum.webassembly4j.api.Engine} /
 * {@link ai.tegmentum.webassembly4j.api.Component} /
 * {@link ai.tegmentum.webassembly4j.api.ComponentInstance} surface, which the
 * wasmos-provider adapters implement on top of this class.
 */
public final class WasmosNative {

    static {
        WasmosNativeLoader.load();
    }

    private WasmosNative() {}

    // ---- Engine ------------------------------------------------------------

    /**
     * Create a wasmtime Engine wired for the wasmos-runtime host surface,
     * plus a persistent Tokio runtime for driving async instantiation /
     * invocation. Returns an opaque handle.
     */
    public static native long engineCreate();

    /** Drop the engine + its Tokio runtime. */
    public static native void engineClose(long engineHandle);

    /**
     * Increment the engine's epoch counter. Only meaningful for instances
     * instantiated with a non-negative {@code epochDeadline} via
     * {@link #componentInstantiateWithConfig}. Callers running without a
     * deadline can ignore this method.
     */
    public static native void engineIncrementEpoch(long engineHandle);

    // ---- Component ---------------------------------------------------------

    /**
     * Compile a Component from raw bytes. The engine handle must outlive
     * the returned component handle.
     */
    public static native long componentLoad(long engineHandle, byte[] bytes);

    /** Drop a component. */
    public static native void componentClose(long componentHandle);

    /**
     * Serialize the component into wasmtime's cached AOT byte
     * representation. Bytes can be fed back to
     * {@link #componentDeserialize(long, byte[])} on a compatible engine
     * to skip re-compiling. Intended for warm-restart / hot-reload
     * scenarios; compatibility is tied to wasmtime version + Config +
     * target ISA (a mismatched engine will fail to deserialize rather
     * than silently misbehave).
     */
    public static native byte[] componentSerialize(long componentHandle);

    /**
     * Deserialize bytes produced by {@link #componentSerialize(long)}
     * against a compatible engine. Returns a fresh component handle.
     *
     * <p>Note: wasmtime's underlying {@code Component::deserialize} is
     * {@code unsafe} — it trusts the input to be a well-formed AOT
     * image from a compatible engine. Passing arbitrary or tampered
     * bytes is undefined behaviour. Callers are responsible for the
     * provenance of the bytes.
     */
    public static native long componentDeserialize(long engineHandle, byte[] bytes);

    /**
     * List the component's exported function names as a null-separated
     * UTF-8 blob. Cheap-and-cheerful serialization to avoid a jstring
     * allocation loop for what's a metadata query.
     */
    public static native byte[] componentExportedFunctionNames(long componentHandle);

    // ---- Instance lifecycle ------------------------------------------------

    /**
     * Instantiate a component with the full wasmos-runtime host surface
     * (bootstrap / caps / grants / host:wasmtime) plus wasi:p2. The engine
     * associated with the component must still be alive. Uses a default
     * {@code HostState::new()} — empty WASI ctx, no limits, no epoch.
     */
    public static native long componentInstantiate(long componentHandle);

    /**
     * Instantiate with optional WASI ctx + optional resource / epoch limits.
     *
     * <p>Flag bits:
     * <ul>
     *   <li>{@code 0x01} — inheritStdin</li>
     *   <li>{@code 0x02} — inheritStdout</li>
     *   <li>{@code 0x04} — inheritStderr</li>
     *   <li>{@code 0x08} — hasWasiContext (when unset, all WASI args are
     *       ignored and a default empty WasiCtx is used — equivalent to
     *       calling {@link #componentInstantiate})</li>
     * </ul>
     *
     * <p>String arrays are index-aligned:
     * {@code envKeys[i]} pairs with {@code envVals[i]};
     * {@code preopenHosts[i]} maps to {@code preopenGuests[i]} with
     * writability decided by the i-th byte of {@code preopenWritable}
     * ({@code 0}=read-only, non-zero=read-write).
     *
     * <p>Numeric args use {@code -1} to mean "unset":
     * {@code maxMemoryBytes<0} skips the memory limiter,
     * {@code epochDeadline<0} skips epoch wiring, and so on. Each
     * outer-store limit is independent — a caller can set only fuel or
     * only tables without dragging the others in.
     *
     * @param componentHandle from {@link #componentLoad}
     * @param flags see the bit-flag doc above
     * @param args wasi arguments (empty array if not needed)
     * @param envKeys wasi env keys
     * @param envVals wasi env vals (same length as envKeys)
     * @param preopenHosts host paths to preopen
     * @param preopenGuests guest paths for each preopen (same length)
     * @param preopenWritable per-preopen writability flag (same length)
     * @param maxMemoryBytes memory-size limit in bytes, or {@code -1} for none
     * @param epochDeadline epoch deadline (ticks beyond current), or {@code -1}
     * @param fuelLimit initial store fuel budget (engine has
     *     {@code Config::consume_fuel(true)} on globally so this is a cheap
     *     opt-in), or {@code -1} for unlimited
     * @param maxTableElements per-table element cap, or {@code -1}
     * @param maxInstances max component-model instances per store, or {@code -1}
     * @param maxTables max tables per store, or {@code -1}
     * @param maxMemories max linear memories per store, or {@code -1}
     */
    public static native long componentInstantiateWithConfig(
            long componentHandle,
            int flags,
            String[] args,
            String[] envKeys,
            String[] envVals,
            String[] preopenHosts,
            String[] preopenGuests,
            byte[] preopenWritable,
            long maxMemoryBytes,
            long epochDeadline,
            long fuelLimit,
            long maxTableElements,
            long maxInstances,
            long maxTables,
            long maxMemories);

    /** Drop an instance + its Store<HostState>. */
    public static native void instanceClose(long instanceHandle);

    // ---- Instance invoke ---------------------------------------------------

    /**
     * Invoke {@code name: func() -> i32} on the instance. Fast path — no
     * marshalling, no JSON. Throws
     * {@link ai.tegmentum.webassembly4j.api.exception.WebAssemblyException} on
     * any wasmtime error, including a signature mismatch (Rust side calls
     * {@code get_typed_func::<(), (i32,)>}).
     */
    public static native int instanceInvokeReturningI32(long instanceHandle, String name);

    /**
     * General invoke path. {@code argsJson} is a JSON array of typed
     * {@code JsonVal} objects (see the schema on the Rust side or the
     * {@code WasmosMarshalling} Java helper). Returns a JSON array of typed
     * {@code JsonVal} result objects.
     */
    public static native String instanceInvokeJson(
            long instanceHandle, String name, String argsJson);

    /**
     * Byte-array-returning variant. Callers who know the exported function
     * returns exactly one {@code list<u8>} (or {@code string}) can skip the
     * JSON detour on the return path — the Rust side hands back a raw
     * {@code byte[]}. Args still go through JSON.
     */
    public static native byte[] instanceInvokeJsonReturningBytes(
            long instanceHandle, String name, String argsJson);

    /**
     * Introspect an exported function's arity as an ASCII byte blob of
     * the form {@code "argCount:resultCount"} (e.g. {@code "2:1"} for a
     * function taking two args and returning one result). Used by the
     * Java marshalling helper when it needs to size a param list without
     * a WIT descriptor in hand.
     */
    public static native byte[] instanceFunctionArity(long instanceHandle, String name);

    /**
     * Cheap {@code exports.contains(name)} probe against a live instance.
     * Avoids the whole export list when a caller only needs a boolean.
     */
    public static native boolean instanceHasFunction(long instanceHandle, String name);

    // ---- Future handle lifecycle ------------------------------------------
    //
    // Val::Future marshalling (slice-1 async, r.2): FutureAny values returned
    // from a guest call are parked in a per-instance FutureRegistry on the
    // Rust side; Java receives a {@code WitFuture} carrying just the u64 slot
    // id + a Debug-formatted type-name hint. These two natives are the
    // Java-visible lifecycle surface for those parked handles.

    /**
     * Close a parked {@code FutureAny} — evicts it from the instance's
     * future registry and calls wasmtime's {@code FutureAny::close(store)}
     * so the writer end sees the read end as dropped. Throws
     * {@link ai.tegmentum.webassembly4j.api.exception.WebAssemblyException}
     * if the slot id is unknown (already closed, transferred to a guest via
     * pass-in, or never parked) or the instance handle is null.
     */
    public static native void futureClose(long instanceHandle, long futureId);

    /**
     * Attempt to await a parked {@code FutureAny}. Currently ALWAYS throws
     * {@link ai.tegmentum.webassembly4j.api.exception.WebAssemblyException}
     * with a clear "wasmtime 47 API gap" message. See the Rust-side
     * {@code futureAwait} doc for the underlying reason — the public
     * {@code FutureAny} surface has no dynamic-payload-type await/poll
     * method, so runtime-typed marshalling can't lift the resolved value.
     *
     * <p>Kept as a JNI entry point so the Java surface is future-proof:
     * when wasmtime exposes a dynamic await API, only the Rust body needs
     * to swap in — the JNI signature and the Java-side wrapper stay put.
     */
    public static native String futureAwait(long instanceHandle, long futureId);

    // ---- Stream handle lifecycle ------------------------------------------
    //
    // Val::Stream marshalling (slice-2 async): structurally identical to
    // futures — StreamAny values returned from a guest call are parked in a
    // per-instance StreamRegistry on the Rust side; Java receives a {@code
    // WitStream} carrying just the u64 slot id + a Debug-formatted type-name
    // hint. Same wasmtime 47 "typed-reader only" API gap applies to reading.

    /**
     * Close a parked {@code StreamAny} — evicts it from the instance's
     * stream registry and calls wasmtime's {@code StreamAny::close(store)}
     * so the writer end sees the read end as dropped. Symmetric with
     * {@link #futureClose}.
     */
    public static native void streamClose(long instanceHandle, long streamId);

    /**
     * Attempt to read from a parked {@code StreamAny}. Currently ALWAYS
     * throws {@link ai.tegmentum.webassembly4j.api.exception.WebAssemblyException}
     * with a "wasmtime 47 API gap" message — same story as
     * {@link #futureAwait}, since {@code StreamAny::try_into_stream_reader<T>}
     * requires a compile-time {@code T}. Kept as a stable JNI entry so the
     * Java surface is future-proof; only the Rust body swaps in when
     * wasmtime exposes a dynamic API.
     */
    public static native String streamRead(long instanceHandle, long streamId);

    // ---- ErrorContext handle lifecycle ------------------------------------
    //
    // Val::ErrorContext marshalling (slice-3): wasmtime 47's ErrorContextAny
    // is a placeholder (see FIXME(#11161) upstream) with `pub(crate) u32`
    // internals and no publicly-defined dispose. We park the Val on the Rust
    // side purely to allow round-trip pass-back; Java sees a slot id + the
    // parsed numeric rep as an equality-adjacent hint.

    /**
     * Evict a parked error-context handle from the instance's registry.
     * Pure Rust-side eviction — wasmtime 47 has no dispose surface on
     * error-context, so this simply drops the parked handle so subsequent
     * pass-in attempts against this id fail cleanly.
     */
    public static native void errorContextClose(long instanceHandle, long errorContextId);
}
