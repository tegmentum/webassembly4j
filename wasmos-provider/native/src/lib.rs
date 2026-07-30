//! JNI wrapper around `wasmos-runtime` — the Rust crate at
//! `~/git/wasmos` that layers wasmos-shaped WIT-typed host bindings
//! (host:wasmtime, host:bootstrap, host:caps, host:grants,
//! wasmos:host/*) on top of an internal wasmtime linkage.
//!
//! # Surface
//!
//! Engine / component / instance lifecycle mirrors the wasmtime4j
//! bindings 1:1 as opaque `long` handles. The invoke path has two
//! flavours:
//!
//! * `instanceInvokeReturningI32` — nullary fast path for the demo
//!   (`guest-demo-portable`'s `run() -> i32`). Skips JSON round-trip.
//! * `instanceInvokeJson` — general path: Java hands over a JSON blob
//!   of typed `JsonVal` argument values, Rust converts to
//!   `wasmtime::component::Val`, calls the function, converts results
//!   back into JSON. The JSON strategy is the one wasmtime4j-native
//!   uses for its concurrent-call path — cheap-and-cheerful, easy to
//!   evolve. A binary marshaller can replace it later without touching
//!   the Java-side API.
//! * `instanceInvokeJsonReturningBytes` — same as `instanceInvokeJson`
//!   but returns the first `list<u8>` result as a raw jbyteArray,
//!   dodging the base64 detour on the JSON path.
//!
//! Instantiation has two flavours:
//!
//! * `componentInstantiate` — default WASI ctx, no limits, no epoch.
//!   Kept for the MVP demo path.
//! * `componentInstantiateWithConfig` — accepts optional WASI settings
//!   (args / env / preopens / stdio inheritance / http toggle) plus
//!   optional `maxMemoryBytes` and `epochDeadline`. Handles the
//!   `LinkingContext.wasiContext()` + `ComponentConfig` plumbing.
//!
//! # Concurrency
//!
//! wasmos-runtime's `add_all_to_linker` uses async wasmtime imports
//! (see the "Async linker" doc comment on `caps::wasi_p2` in wasmos
//! ~/git/wasmos/src/lib.rs), so instantiation and invocation must
//! run inside a Tokio runtime. A multi-thread runtime is created
//! once per engine and reused for every `block_on` on that engine's
//! components / instances.
//!
//! # Error handling
//!
//! Every entry point catches `anyhow::Error`s and throws a Java
//! `WebAssemblyException`. Rust panics in this crate would be a bug
//! (we take input from Java-side validated shims), not a normal
//! path — we do NOT `catch_unwind` on the JNI boundary because
//! `&mut JNIEnv` isn't `UnwindSafe`. A panic would abort the JVM;
//! any code path that could plausibly panic should return a Result
//! instead.

#![allow(non_snake_case)]
#![allow(clippy::missing_safety_doc)]

use std::collections::HashMap;
use std::sync::Mutex;

use jni::objects::{JByteArray, JClass, JObjectArray, JString};
use jni::sys::{jboolean, jbyteArray, jint, jlong, jstring};
use jni::JNIEnv;

use wasmtime::component::{Component, FutureAny, Linker, ResourceAny, StreamAny, Val};
use wasmtime::{AsContextMut, Config, Engine, Store};
use wasmos_runtime::{caps, HostState};
use wasmtime_wasi::{DirPerms, FilePerms, WasiCtxBuilder};

use serde::{Deserialize, Serialize};

// -----------------------------------------------------------------------------
// Handle wrappers
// -----------------------------------------------------------------------------
// Each Java `long` handle is a `Box::into_raw(Box::new(_))` pointer. We hand
// the JVM the pointer as an opaque `jlong`; ownership returns to Rust via the
// matching `*Close` entry point which reconstructs the Box and drops it.

/// Owned engine + persistent Tokio runtime. The runtime is created eagerly at
/// engine creation time so subsequent instantiate / invoke calls can `block_on`
/// with no per-call setup cost — and, more importantly, so async wasi:p2
/// timers/clocks see a live runtime for their whole lifetime.
///
/// The engine has `epoch_interruption(true)` set so
/// `componentInstantiateWithConfig` can wire an epoch deadline without needing
/// a second engine — the flag is a no-op for callers that don't set a deadline.
struct EngineHandle {
    engine: Engine,
    runtime: tokio::runtime::Runtime,
}

/// Owned component + a back-pointer to the engine (needed at instantiate
/// time to build a fresh Linker + Store).
struct ComponentHandle {
    engine_ptr: *mut EngineHandle,
    component: Component,
}

/// Owned component instance + its Store<HostState>. The store lives here so
/// subsequent invoke calls have the store to thread through wasmtime's
/// call_async. Also carries the engine back-pointer so invoke can
/// grab the same Tokio runtime that instantiated it.
///
/// `resources` is a per-instance registry that parks `ResourceAny` values
/// returned from guest calls. Java receives an opaque `WitResource{ tableId,
/// typeName, owned }` — the wasm ResourceAny stays in Rust; only its slot id
/// crosses the JNI boundary. On the way back in, we look up the parked value
/// and hand it back to wasmtime. `ResourceAny` is `Copy`, so lookups are
/// cheap; no reference-counting needed.
struct InstanceHandle {
    engine_ptr: *mut EngineHandle,
    store: Mutex<Store<HostState>>,
    instance: wasmtime::component::Instance,
    resources: Mutex<ResourceRegistry>,
    /// Per-instance registry for `FutureAny` values returned by guest calls.
    /// Slot ids are handed to Java as `long`s (see `JsonVal::Future.table_id`);
    /// monotonic, never reused (same policy as `ResourceRegistry`). See
    /// `FutureRegistry` docs for lifecycle details, and the module-level
    /// "wasmtime Val::Future API gap" note for what awaiting looks like
    /// today (spoiler: type-erased polling isn't in the public API).
    futures: Mutex<FutureRegistry>,
    /// Per-instance registry for `StreamAny` values returned by guest calls.
    /// Mirrors `FutureRegistry` exactly — `StreamAny`'s public API is
    /// structurally identical to `FutureAny`'s in wasmtime 47 (Clone, Debug,
    /// `try_into_stream_reader::<T>` requires compile-time `T`, and `close(store)`
    /// for disposal). Park + close + pass-in work; host-side dynamic reading
    /// is the same API gap as futures. See `StreamRegistry` for docs.
    streams: Mutex<StreamRegistry>,
    /// Per-instance registry for `ErrorContextAny` values returned by guest
    /// calls or received on `result<_, error-context>` err branches. Uses the
    /// same parking-lot shape as the other registries so pass-back into a
    /// guest is possible even though wasmtime 47 exposes no `close`/`drop` on
    /// `ErrorContextAny` (see the FIXME on
    /// `wasmtime::component::ErrorContextAny`). The parked value is only ever
    /// released when the instance handle is dropped, or when Java explicitly
    /// evicts it via `errorContextClose`.
    error_contexts: Mutex<ErrorContextRegistry>,
}

/// Per-instance parking lot for `ResourceAny` values. Slot ids are handed to
/// Java as `long`s (see `JsonVal::Resource.table_id`). Ids are monotonically
/// increasing; we never reuse a freed slot so the "stale handle after
/// take-owned" case surfaces as a clean "unknown resource id" error rather
/// than an aliased hit on a newly parked resource.
///
/// Ownership semantics — driven by the `owned` bit on the way IN from Java:
/// * `owned=true`  → the Java caller is transferring the resource to the
///   guest (matches WIT `own<T>`). We remove the entry; subsequent uses of
///   that WitResource fail.
/// * `owned=false` → the Java caller is lending the resource for the length
///   of this call (matches WIT `borrow<T>`). The entry stays.
///
/// On the way OUT from a guest return, we park with the ResourceAny's own
/// `owned()` flag reported back to Java verbatim.
#[derive(Default)]
struct ResourceRegistry {
    next_id: u64,
    entries: HashMap<u64, ResourceAny>,
}

impl ResourceRegistry {
    /// Park a `ResourceAny` and hand out a fresh slot id. Ids monotonically
    /// increase; freed slots are never reused (see struct docs).
    fn park(&mut self, any: ResourceAny) -> u64 {
        let id = self.next_id;
        self.next_id = self
            .next_id
            .checked_add(1)
            .expect("wasmos-provider: resource id counter overflow");
        self.entries.insert(id, any);
        id
    }

    /// Fetch (borrow-shaped) — returns a copy but keeps the entry so the same
    /// WitResource can be lent multiple times.
    fn peek(&self, id: u64) -> Option<ResourceAny> {
        self.entries.get(&id).copied()
    }

    /// Fetch and remove (own-shaped) — the entry is consumed; subsequent
    /// lookups against this id will miss.
    fn take(&mut self, id: u64) -> Option<ResourceAny> {
        self.entries.remove(&id)
    }
}

/// Per-instance parking lot for `FutureAny` values. Mirrors `ResourceRegistry`
/// exactly — monotonic u64 ids, no reuse, take-vs-peek semantics driven by
/// the Java caller's intent. See the module-level docs for how this interacts
/// with the wasmtime 47 `FutureAny` public API (short version: return-direction
/// parking works; pass-in direction works; dynamic *awaiting* a `FutureAny`
/// with a runtime-known payload type is a wasmtime public-API gap — the
/// `FutureAny::try_into_future_reader::<T>` path requires `T` at compile time
/// and `FutureConsumer::Item` is an associated type, so there's no
/// type-erased await surface today).
#[derive(Default)]
struct FutureRegistry {
    next_id: u64,
    entries: HashMap<u64, FutureAny>,
}

impl FutureRegistry {
    fn park(&mut self, any: FutureAny) -> u64 {
        let id = self.next_id;
        self.next_id = self
            .next_id
            .checked_add(1)
            .expect("wasmos-provider: future id counter overflow");
        self.entries.insert(id, any);
        id
    }

    /// Borrow-shaped fetch. `FutureAny: Clone` (wasmtime derives it) so we
    /// can hand a copy to a guest call while keeping the entry parked, which
    /// matches the "read end" semantics — the same `FutureAny` may in
    /// principle be lowered into more than one call before being closed.
    ///
    /// Currently unused by production code (there's no borrow-shaped Future
    /// arm in `to_val` — the JsonVal::Future decode always takes; see the
    /// enum docs for why); kept for parity with `ResourceRegistry::peek` and
    /// exercised by the test suite. Suppress dead-code warning accordingly.
    #[allow(dead_code)]
    fn peek(&self, id: u64) -> Option<FutureAny> {
        self.entries.get(&id).cloned()
    }

    /// Own-shaped fetch. Removes the entry; subsequent lookups miss.
    fn take(&mut self, id: u64) -> Option<FutureAny> {
        self.entries.remove(&id)
    }
}

/// Per-instance parking lot for `StreamAny` values. Mirrors `FutureRegistry`
/// exactly — monotonic u64 ids, no reuse, take-vs-peek semantics driven by
/// the Java caller's intent. See the module-level docs for how this interacts
/// with the wasmtime 47 `StreamAny` public API (short version: same story as
/// `FutureAny` — park and close and pass-in are wired; dynamic host-side
/// *reading* of a `StreamAny` with a runtime-known payload type is a wasmtime
/// public-API gap because `StreamAny::try_into_stream_reader::<T>` requires
/// `T` at compile time).
#[derive(Default)]
struct StreamRegistry {
    next_id: u64,
    entries: HashMap<u64, StreamAny>,
}

impl StreamRegistry {
    fn park(&mut self, any: StreamAny) -> u64 {
        let id = self.next_id;
        self.next_id = self
            .next_id
            .checked_add(1)
            .expect("wasmos-provider: stream id counter overflow");
        self.entries.insert(id, any);
        id
    }

    /// Borrow-shaped fetch. `StreamAny: Clone`; see `FutureRegistry::peek`
    /// docs for symmetry rationale. Currently unused by production code
    /// (there's no borrow-shaped Stream arm in `to_val`), kept for parity
    /// with `FutureRegistry` and exercised by tests.
    #[allow(dead_code)]
    fn peek(&self, id: u64) -> Option<StreamAny> {
        self.entries.get(&id).cloned()
    }

    /// Own-shaped fetch. Removes the entry; subsequent lookups miss.
    fn take(&mut self, id: u64) -> Option<StreamAny> {
        self.entries.remove(&id)
    }
}

/// Per-instance parking lot for `error-context` handles. Mirrors the shape
/// of the other registries but differs on lifecycle: wasmtime 47's
/// `ErrorContextAny` is a placeholder (see `FIXME(#11161)` on the wasmtime
/// side) with `pub(crate) u32` internals and no publicly-defined `close` or
/// `drop`. It is also not publicly re-exported at the `wasmtime::component`
/// module boundary — its `pub` visibility only reaches as far as being usable
/// through the `Val::ErrorContext(...)` enum variant. To sidestep the naming
/// problem we park the whole `Val::ErrorContext(any)` value; `Val: Clone`
/// preserves the parking-lot copy semantics without needing to name the
/// inner type.
///
/// Eviction is Java-driven only (`errorContextClose`) or automatic on
/// instance handle drop. On pass-in, the parked `Val` is cloned back
/// verbatim, so wasmtime sees the original ErrorContextAny handle.
#[derive(Default)]
struct ErrorContextRegistry {
    next_id: u64,
    entries: HashMap<u64, Val>,
}

impl ErrorContextRegistry {
    /// Park a `Val::ErrorContext(any)` value. The debug caller-passed value
    /// MUST be an `ErrorContext`-shaped `Val`; other variants would still
    /// park but round-trip incoherently on pass-in — the type is asserted
    /// upstream in `from_val`.
    fn park(&mut self, ec: Val) -> u64 {
        debug_assert!(
            matches!(&ec, Val::ErrorContext(_)),
            "ErrorContextRegistry::park requires a Val::ErrorContext, got {ec:?}"
        );
        let id = self.next_id;
        self.next_id = self
            .next_id
            .checked_add(1)
            .expect("wasmos-provider: error-context id counter overflow");
        self.entries.insert(id, ec);
        id
    }

    #[allow(dead_code)]
    fn peek(&self, id: u64) -> Option<Val> {
        self.entries.get(&id).cloned()
    }

    fn take(&mut self, id: u64) -> Option<Val> {
        self.entries.remove(&id)
    }
}

// -----------------------------------------------------------------------------
// Error helpers
// -----------------------------------------------------------------------------

const EXCEPTION_CLASS: &str = "ai/tegmentum/webassembly4j/api/exception/WebAssemblyException";

/// Throw a `WebAssemblyException` with the given message. Ignores JNI errors
/// (the JVM will already be in an exception-pending state on any failure here).
fn throw(env: &mut JNIEnv, msg: &str) {
    let _ = env.throw_new(EXCEPTION_CLASS, msg);
}

/// Ergonomic helper — translate an `anyhow::Result<T>` into a JNI return
/// value: on `Ok`, return the value; on `Err`, throw
/// `WebAssemblyException` and return the caller-supplied sentinel.
fn or_throw<T>(env: &mut JNIEnv, result: anyhow::Result<T>, sentinel: T) -> T {
    match result {
        Ok(v) => v,
        Err(e) => {
            throw(env, &format!("wasmos-provider: {:#}", e));
            sentinel
        }
    }
}

/// Read a jstring into a `String`, translating any JNI error into `anyhow`.
fn jstring_to_string(env: &mut JNIEnv, s: &JString) -> anyhow::Result<String> {
    env.get_string(s)
        .map(|jni_str| jni_str.into())
        .map_err(|e| anyhow::anyhow!("get_string: {e}"))
}

/// Read a JObjectArray of jstrings into `Vec<String>`. Skips null entries
/// (returns them as empty strings — callers pass tightly-packed arrays).
fn jstringarray_to_vec(env: &mut JNIEnv, arr: &JObjectArray) -> anyhow::Result<Vec<String>> {
    let len = env
        .get_array_length(arr)
        .map_err(|e| anyhow::anyhow!("get_array_length: {e}"))?;
    let mut out = Vec::with_capacity(len as usize);
    for i in 0..len {
        let elem = env
            .get_object_array_element(arr, i)
            .map_err(|e| anyhow::anyhow!("get_object_array_element[{i}]: {e}"))?;
        if elem.is_null() {
            out.push(String::new());
            continue;
        }
        let js: JString = elem.into();
        out.push(jstring_to_string(env, &js)?);
    }
    Ok(out)
}

// -----------------------------------------------------------------------------
// Exported-function lookup helpers
// -----------------------------------------------------------------------------

/// Resolve a callable `Func` from an `Instance` by name, supporting both
/// top-level exports and nested-in-interface exports.
///
/// * If `name` contains a `#`, split on the FIRST `#` — the left half is
///   the exported interface name (e.g. `"tegmentum:counter/counter-api@0.1.0"`)
///   and the right half is the item name within that interface (e.g.
///   `"[constructor]counter"` or `"[method]counter.get"`).
///   Wasmtime's `Instance::get_func` doesn't descend into exported instances
///   on its own; we chain two `get_export_index` calls to reach the func.
/// * Otherwise, look up `name` directly at the top level — the existing
///   plain-function path.
///
/// Returns `None` if `name` resolves to something that isn't a lifted
/// component function (e.g. a nested instance, a type export, or unknown).
fn resolve_func(
    instance: &wasmtime::component::Instance,
    store: &mut Store<HostState>,
    name: &str,
) -> Option<wasmtime::component::Func> {
    if let Some(hash_at) = name.find('#') {
        let iface_name = &name[..hash_at];
        let item_name = &name[hash_at + 1..];
        let iface_idx = instance.get_export_index(&mut *store, None, iface_name)?;
        let func_idx = instance.get_export_index(&mut *store, Some(&iface_idx), item_name)?;
        instance.get_func(&mut *store, &func_idx)
    } else {
        instance.get_func(&mut *store, name)
    }
}

// -----------------------------------------------------------------------------
// JSON Val marshalling
// -----------------------------------------------------------------------------
// Design mirrors wasmtime4j-native's `concurrent_call_json::JsonVal`. Keeps
// tag names short (`t` / `v`) to trim payload size — the Java side has a
// matching Jackson-free hand-rolled parser/writer so JVM adds no runtime dep.
//
// `Bytes` is a fast-path variant that base64-encodes a `list<u8>` payload —
// avoids the O(n) JsonVal-per-byte round-trip for large binary blobs. On the
// return path we detect `Val::List` whose elements are all `Val::U8` and
// emit the `Bytes` variant automatically.

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(tag = "t", content = "v")]
enum JsonVal {
    Bool(bool),
    S8(i8),
    S16(i16),
    S32(i32),
    S64(i64),
    U8(u8),
    U16(u16),
    U32(u32),
    U64(u64),
    F32(f32),
    F64(f64),
    Char(char),
    String(String),
    /// `list<u8>` fast path — base64-encoded bytes. Reduces both wire size
    /// and per-element boxing on both sides.
    Bytes(String),
    List(Vec<JsonVal>),
    Option(Option<Box<JsonVal>>),
    /// Result-shaped value. `is_ok` picks the branch; the corresponding side
    /// carries the payload (or `None` for `result<_,_>` with a unit branch).
    Result {
        is_ok: bool,
        #[serde(skip_serializing_if = "Option::is_none", default)]
        ok: Option<Box<JsonVal>>,
        #[serde(skip_serializing_if = "Option::is_none", default)]
        err: Option<Box<JsonVal>>,
    },
    Record(Vec<(String, JsonVal)>),
    Tuple(Vec<JsonVal>),
    Variant {
        discriminant: String,
        #[serde(skip_serializing_if = "Option::is_none", default)]
        value: Option<Box<JsonVal>>,
    },
    Enum(String),
    Flags(Vec<String>),
    /// Distinct from `Record`: keys can be arbitrary `Val`s, not just
    /// field-name strings. Wire form is a flat array of `[key_json,
    /// value_json]` two-tuples — identical layout to what wasmtime's
    /// `Val::Map(Vec<(Val, Val)>)` implies. The Java side uses a
    /// `WitMap` explicit wrapper so callers opt in intentionally rather
    /// than have every `Map<?,?>` guessed one way or another.
    Map(Vec<(JsonVal, JsonVal)>),
    /// Component-model `own<T>` / `borrow<T>` handle. The `ResourceAny` lives
    /// Rust-side in `InstanceHandle::resources`; Java only ever sees
    /// `table_id` (opaque slot number), `type_name` (opaque
    /// `ResourceType`-derived identity, useful only for equality), and
    /// `owned` (drives take-vs-peek on the way back in — see
    /// `ResourceRegistry` docs). This is the wasmos-provider's `Val::Resource`
    /// story; no other JsonVal shape crosses a "handle" over the wire.
    Resource {
        table_id: u64,
        type_name: String,
        owned: bool,
    },
    /// Component-model `future<T>` handle. Same parking-lot pattern as
    /// `Resource`: the `FutureAny` lives Rust-side in
    /// `InstanceHandle::futures`; Java only ever sees `table_id` (opaque
    /// slot number) and `type_name` (opaque Debug rendering of the
    /// `FutureAny`, useful only for equality-adjacent introspection).
    ///
    /// There is no `owned` bit here — wasmtime 47's `FutureAny: Clone` and
    /// the read/write ends are already separated at the type level (this is
    /// the read end); "borrow" as a distinct concept doesn't apply. The
    /// Java carrier consumes the slot on pass-in by default (parity with
    /// how a future handle typically flows exactly once through a guest
    /// import); explicit disposal is via `futureClose`.
    ///
    /// See the `FutureRegistry` docs for the wasmtime API gap that
    /// currently blocks a dynamic host-side *await* on this handle.
    Future {
        table_id: u64,
        type_name: String,
    },
    /// Component-model `stream<T>` handle. Same parking-lot pattern as
    /// `Future`: the `StreamAny` lives Rust-side in
    /// `InstanceHandle::streams`; Java only ever sees `table_id` (opaque
    /// slot number) and `type_name` (opaque Debug rendering of the
    /// `StreamAny`, useful only for equality-adjacent introspection).
    ///
    /// Same lifecycle contract as `Future`: the decode arm always takes
    /// (a stream handle's read end flows through a guest import exactly
    /// once). Explicit disposal is via `streamClose`. Host-side dynamic
    /// reading of the stream's items has the identical wasmtime API gap
    /// as `Future` — `StreamAny::try_into_stream_reader::<T>` is
    /// compile-time typed.
    Stream {
        table_id: u64,
        type_name: String,
    },
    /// Component-model `error-context` handle. Parked in
    /// `InstanceHandle::error_contexts` and surfaced to Java as an opaque
    /// slot id plus the wasmtime-derived numeric `rep` (parsed out of
    /// `ErrorContextAny`'s Debug rendering — the underlying `u32` is
    /// `pub(crate)` so Debug is the only public window). The `rep` value
    /// is useful for equality-adjacent identification; it has no other
    /// user-facing meaning today because wasmtime 47's `ErrorContextAny`
    /// is a placeholder (see wasmtime's `FIXME(#11161)`).
    ///
    /// There is no wasmtime-defined dispose on `ErrorContextAny`, so
    /// `errorContextClose` on the Java side is a pure Rust-side registry
    /// eviction — it drops the parked handle so the slot id becomes
    /// unknown for later pass-in attempts.
    ErrorContext {
        table_id: u64,
        rep: u32,
    },
}

/// Base64 alphabet — hand-rolled to avoid pulling in the `base64` crate for a
/// couple of `encode` / `decode` calls. Standard alphabet with `=` padding.
const B64_ALPHABET: &[u8; 64] =
    b"ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";

fn b64_encode(bytes: &[u8]) -> String {
    let mut out = String::with_capacity((bytes.len() + 2) / 3 * 4);
    let mut i = 0;
    while i + 3 <= bytes.len() {
        let b0 = bytes[i];
        let b1 = bytes[i + 1];
        let b2 = bytes[i + 2];
        out.push(B64_ALPHABET[(b0 >> 2) as usize] as char);
        out.push(B64_ALPHABET[(((b0 & 0x03) << 4) | (b1 >> 4)) as usize] as char);
        out.push(B64_ALPHABET[(((b1 & 0x0f) << 2) | (b2 >> 6)) as usize] as char);
        out.push(B64_ALPHABET[(b2 & 0x3f) as usize] as char);
        i += 3;
    }
    let rem = bytes.len() - i;
    if rem == 1 {
        let b0 = bytes[i];
        out.push(B64_ALPHABET[(b0 >> 2) as usize] as char);
        out.push(B64_ALPHABET[((b0 & 0x03) << 4) as usize] as char);
        out.push('=');
        out.push('=');
    } else if rem == 2 {
        let b0 = bytes[i];
        let b1 = bytes[i + 1];
        out.push(B64_ALPHABET[(b0 >> 2) as usize] as char);
        out.push(B64_ALPHABET[(((b0 & 0x03) << 4) | (b1 >> 4)) as usize] as char);
        out.push(B64_ALPHABET[((b1 & 0x0f) << 2) as usize] as char);
        out.push('=');
    }
    out
}

fn b64_decode(s: &str) -> anyhow::Result<Vec<u8>> {
    // Build a reverse-lookup table lazily. Cheap; done once per decode.
    let mut rev = [255u8; 256];
    for (i, &c) in B64_ALPHABET.iter().enumerate() {
        rev[c as usize] = i as u8;
    }
    let bytes = s.as_bytes();
    if bytes.len() % 4 != 0 {
        anyhow::bail!("base64: length not a multiple of 4");
    }
    let mut out = Vec::with_capacity(bytes.len() / 4 * 3);
    let mut i = 0;
    while i < bytes.len() {
        let c0 = bytes[i];
        let c1 = bytes[i + 1];
        let c2 = bytes[i + 2];
        let c3 = bytes[i + 3];
        let v0 = rev[c0 as usize];
        let v1 = rev[c1 as usize];
        if v0 == 255 || v1 == 255 {
            anyhow::bail!("base64: invalid char at offset {i}");
        }
        out.push((v0 << 2) | (v1 >> 4));
        if c2 != b'=' {
            let v2 = rev[c2 as usize];
            if v2 == 255 {
                anyhow::bail!("base64: invalid char at offset {}", i + 2);
            }
            out.push(((v1 & 0x0f) << 4) | (v2 >> 2));
            if c3 != b'=' {
                let v3 = rev[c3 as usize];
                if v3 == 255 {
                    anyhow::bail!("base64: invalid char at offset {}", i + 3);
                }
                out.push(((v2 & 0x03) << 6) | v3);
            }
        }
        i += 4;
    }
    Ok(out)
}

impl JsonVal {
    /// Decode a JsonVal into a wasmtime `Val`, using `resources`,
    /// `futures`, `streams`, and `error_contexts` to resolve any parked
    /// slot ids back into `ResourceAny` / `FutureAny` / `StreamAny` /
    /// `ErrorContextAny` handles. All registries are passed as `&mut`
    /// because the pass-in arms consume (take) entries — Resource by way
    /// of the caller-supplied `owned` bit, Future/Stream unconditionally
    /// (a future/stream read end typically flows through a guest import
    /// exactly once), ErrorContext unconditionally (Clone would let us
    /// peek but no known WIT shape wants to hold the same handle twice).
    fn to_val(
        &self,
        resources: &mut ResourceRegistry,
        futures: &mut FutureRegistry,
        streams: &mut StreamRegistry,
        error_contexts: &mut ErrorContextRegistry,
    ) -> anyhow::Result<Val> {
        Ok(match self {
            JsonVal::Bool(v) => Val::Bool(*v),
            JsonVal::S8(v) => Val::S8(*v),
            JsonVal::S16(v) => Val::S16(*v),
            JsonVal::S32(v) => Val::S32(*v),
            JsonVal::S64(v) => Val::S64(*v),
            JsonVal::U8(v) => Val::U8(*v),
            JsonVal::U16(v) => Val::U16(*v),
            JsonVal::U32(v) => Val::U32(*v),
            JsonVal::U64(v) => Val::U64(*v),
            JsonVal::F32(v) => Val::Float32(*v),
            JsonVal::F64(v) => Val::Float64(*v),
            JsonVal::Char(v) => Val::Char(*v),
            JsonVal::String(v) => Val::String(v.clone()),
            JsonVal::Bytes(b64) => {
                let bytes = b64_decode(b64)?;
                Val::List(bytes.into_iter().map(Val::U8).collect())
            }
            JsonVal::List(items) => Val::List(
                items
                    .iter()
                    .map(|v| v.to_val(resources, futures, streams, error_contexts))
                    .collect::<anyhow::Result<Vec<_>>>()?,
            ),
            JsonVal::Option(opt) => Val::Option(
                opt.as_ref()
                    .map(|v| v.to_val(resources, futures, streams, error_contexts).map(Box::new))
                    .transpose()?,
            ),
            JsonVal::Result { is_ok, ok, err } => {
                if *is_ok {
                    Val::Result(Ok(ok
                        .as_ref()
                        .map(|v| v.to_val(resources, futures, streams, error_contexts).map(Box::new))
                        .transpose()?))
                } else {
                    Val::Result(Err(err
                        .as_ref()
                        .map(|v| v.to_val(resources, futures, streams, error_contexts).map(Box::new))
                        .transpose()?))
                }
            }
            JsonVal::Record(fields) => Val::Record(
                fields
                    .iter()
                    .map(|(name, jv)| jv.to_val(resources, futures, streams, error_contexts).map(|v| (name.clone(), v)))
                    .collect::<anyhow::Result<Vec<_>>>()?,
            ),
            JsonVal::Tuple(items) => Val::Tuple(
                items
                    .iter()
                    .map(|v| v.to_val(resources, futures, streams, error_contexts))
                    .collect::<anyhow::Result<Vec<_>>>()?,
            ),
            JsonVal::Variant { discriminant, value } => Val::Variant(
                discriminant.clone(),
                value
                    .as_ref()
                    .map(|v| v.to_val(resources, futures, streams, error_contexts).map(Box::new))
                    .transpose()?,
            ),
            JsonVal::Enum(v) => Val::Enum(v.clone()),
            JsonVal::Flags(v) => Val::Flags(v.clone()),
            JsonVal::Map(pairs) => Val::Map(
                pairs
                    .iter()
                    .map(|(k, v)| Ok((k.to_val(resources, futures, streams, error_contexts)?,
                                       v.to_val(resources, futures, streams, error_contexts)?)))
                    .collect::<anyhow::Result<Vec<_>>>()?,
            ),
            JsonVal::Resource { table_id, owned, .. } => {
                // Java-side `owned` bit drives the take-vs-peek choice — the
                // WIT signature the guest expects is what the caller is
                // targeting, and it dictates whether we transfer ownership
                // (own<T>) or lend a copy (borrow<T>). `type_name` is not
                // consulted here; it's Java-side introspection only.
                let any = if *owned {
                    resources.take(*table_id).ok_or_else(|| {
                        anyhow::anyhow!(
                            "wasmos-provider: unknown resource id {} (already consumed \
                             as own<T> or never parked)",
                            table_id
                        )
                    })?
                } else {
                    resources.peek(*table_id).ok_or_else(|| {
                        anyhow::anyhow!(
                            "wasmos-provider: unknown resource id {} (never parked or \
                             already consumed by prior own<T> transfer)",
                            table_id
                        )
                    })?
                };
                Val::Resource(any)
            }
            JsonVal::Future { table_id, .. } => {
                // Futures unconditionally take: a `future<T>` handle
                // typically flows through a guest import exactly once, and
                // the read end (which is what we're holding) is single-shot
                // by nature. Peek-style repeated pass-in isn't a shape the
                // component model surfaces, so we don't try to model it.
                // Callers that need to keep the handle after passing it in
                // shouldn't; there's no "borrow<future<T>>" in the wire
                // model.
                let any = futures.take(*table_id).ok_or_else(|| {
                    anyhow::anyhow!(
                        "wasmos-provider: unknown future id {} (already consumed or \
                         never parked)",
                        table_id
                    )
                })?;
                Val::Future(any)
            }
            JsonVal::Stream { table_id, .. } => {
                // Streams unconditionally take, same rationale as Future:
                // the read end of a `stream<T>` is single-shot at the
                // wire-model level.
                let any = streams.take(*table_id).ok_or_else(|| {
                    anyhow::anyhow!(
                        "wasmos-provider: unknown stream id {} (already consumed or \
                         never parked)",
                        table_id
                    )
                })?;
                Val::Stream(any)
            }
            JsonVal::ErrorContext { table_id, .. } => {
                // ErrorContext unconditionally takes on pass-in. There is no
                // borrow shape for `error-context` in the WIT wire model —
                // an error-context handle is terminal by convention. If a
                // caller needs to re-park after peeking they can round-trip
                // via `from_val` on the returned Val, but pass-in through
                // JSON always consumes to keep parity with Future/Stream.
                //
                // The registry stores the whole `Val::ErrorContext(any)`
                // (see ErrorContextRegistry docs for why); pass-in hands
                // that Val straight back to wasmtime.
                error_contexts.take(*table_id).ok_or_else(|| {
                    anyhow::anyhow!(
                        "wasmos-provider: unknown error-context id {} (already consumed \
                         or never parked)",
                        table_id
                    )
                })?
            }
        })
    }

    /// Encode a wasmtime `Val` into a JsonVal, parking any `Resource(any)` /
    /// `Future(any)` / `Stream(any)` / `ErrorContext(any)` values in their
    /// respective registries and emitting a slot id in the JSON.
    fn from_val(
        val: &Val,
        resources: &mut ResourceRegistry,
        futures: &mut FutureRegistry,
        streams: &mut StreamRegistry,
        error_contexts: &mut ErrorContextRegistry,
    ) -> anyhow::Result<Self> {
        Ok(match val {
            Val::Bool(v) => JsonVal::Bool(*v),
            Val::S8(v) => JsonVal::S8(*v),
            Val::S16(v) => JsonVal::S16(*v),
            Val::S32(v) => JsonVal::S32(*v),
            Val::S64(v) => JsonVal::S64(*v),
            Val::U8(v) => JsonVal::U8(*v),
            Val::U16(v) => JsonVal::U16(*v),
            Val::U32(v) => JsonVal::U32(*v),
            Val::U64(v) => JsonVal::U64(*v),
            Val::Float32(v) => JsonVal::F32(*v),
            Val::Float64(v) => JsonVal::F64(*v),
            Val::Char(v) => JsonVal::Char(*v),
            Val::String(v) => JsonVal::String(v.clone()),
            Val::List(items) => {
                // Fast-path: if all items are U8, encode as `Bytes(base64)`.
                if !items.is_empty() && items.iter().all(|v| matches!(v, Val::U8(_))) {
                    let raw: Vec<u8> = items
                        .iter()
                        .map(|v| match v {
                            Val::U8(b) => *b,
                            _ => unreachable!("guarded by matches! above"),
                        })
                        .collect();
                    JsonVal::Bytes(b64_encode(&raw))
                } else {
                    JsonVal::List(
                        items
                            .iter()
                            .map(|v| JsonVal::from_val(v, resources, futures, streams, error_contexts))
                            .collect::<anyhow::Result<Vec<_>>>()?,
                    )
                }
            }
            Val::Option(opt) => JsonVal::Option(
                opt.as_ref()
                    .map(|v| JsonVal::from_val(v, resources, futures, streams, error_contexts).map(Box::new))
                    .transpose()?,
            ),
            Val::Result(res) => match res {
                Ok(ok_val) => JsonVal::Result {
                    is_ok: true,
                    ok: ok_val
                        .as_ref()
                        .map(|v| JsonVal::from_val(v, resources, futures, streams, error_contexts).map(Box::new))
                        .transpose()?,
                    err: None,
                },
                Err(err_val) => JsonVal::Result {
                    is_ok: false,
                    ok: None,
                    err: err_val
                        .as_ref()
                        .map(|v| JsonVal::from_val(v, resources, futures, streams, error_contexts).map(Box::new))
                        .transpose()?,
                },
            },
            Val::Record(fields) => JsonVal::Record(
                fields
                    .iter()
                    .map(|(name, v)| {
                        JsonVal::from_val(v, resources, futures, streams, error_contexts).map(|jv| (name.clone(), jv))
                    })
                    .collect::<anyhow::Result<Vec<_>>>()?,
            ),
            Val::Tuple(items) => JsonVal::Tuple(
                items
                    .iter()
                    .map(|v| JsonVal::from_val(v, resources, futures, streams, error_contexts))
                    .collect::<anyhow::Result<Vec<_>>>()?,
            ),
            Val::Variant(discriminant, payload) => JsonVal::Variant {
                discriminant: discriminant.clone(),
                value: payload
                    .as_ref()
                    .map(|v| JsonVal::from_val(v, resources, futures, streams, error_contexts).map(Box::new))
                    .transpose()?,
            },
            Val::Enum(discriminant) => JsonVal::Enum(discriminant.clone()),
            Val::Flags(v) => JsonVal::Flags(v.clone()),
            Val::Map(pairs) => JsonVal::Map(
                pairs
                    .iter()
                    .map(|(k, v)| {
                        Ok((
                            JsonVal::from_val(k, resources, futures, streams, error_contexts)?,
                            JsonVal::from_val(v, resources, futures, streams, error_contexts)?,
                        ))
                    })
                    .collect::<anyhow::Result<Vec<_>>>()?,
            ),
            Val::Resource(any) => {
                // Park the ResourceAny; hand Java a stable u64 slot id and
                // the ResourceType's Debug rendering as opaque type identity.
                // The Debug format for a guest ResourceType includes the
                // component's internal resource index; that's stable within
                // an instance's lifetime, which is exactly the window a
                // WitResource is valid for.
                let owned = any.owned();
                let type_name = format!("{:?}", any.ty());
                let table_id = resources.park(*any);
                JsonVal::Resource {
                    table_id,
                    type_name,
                    owned,
                }
            }
            Val::Future(any) => {
                // Park the FutureAny; hand Java an opaque u64 slot id + the
                // FutureAny's Debug rendering as a type-identity hint (the
                // payload type is buried in a private `PayloadType<FutureType>`
                // field, so Debug is the widest window wasmtime 47's public
                // API gives us). See the module-level "wasmtime API gap"
                // note on why Java cannot yet host-side *await* this handle.
                let type_name = format!("{:?}", any);
                // `FutureAny: Clone` — safe to clone into the registry
                // rather than consuming the borrow. Java has to explicitly
                // dispose via `futureClose`; the registry keeps the parked
                // copy alive until then (or until the instance handle
                // itself is dropped, which drops the whole registry).
                let table_id = futures.park(any.clone());
                JsonVal::Future {
                    table_id,
                    type_name,
                }
            }
            Val::Stream(any) => {
                // Same pattern as Val::Future. `StreamAny: Clone` in
                // wasmtime 47, so we clone into the registry and keep the
                // borrow intact. Debug format is the only public window on
                // the payload type — same "opaque hint" role as Future.
                let type_name = format!("{:?}", any);
                let table_id = streams.park(any.clone());
                JsonVal::Stream {
                    table_id,
                    type_name,
                }
            }
            Val::ErrorContext(any) => {
                // Park the ErrorContext-shaped Val. Its Debug form is
                // `ErrorContextAny(N)` where N is the pub(crate) u32 rep;
                // parse the number out so Java gets a plain numeric hint
                // instead of the debug string wrapper. If the format ever
                // changes (unlikely — it's a plain tuple struct) fall back
                // to 0. The rep is only meant for equality-adjacent
                // introspection; wasmtime's `FIXME(#11161)` documents that
                // the type has no other operations at 47.0.2.
                //
                // We store the whole `Val` in the registry (not the
                // `ErrorContextAny`) because `ErrorContextAny` isn't
                // re-exported at the `wasmtime::component` module boundary
                // and can't be named as a HashMap value type from outside
                // the wasmtime crate. Cloning the enclosing Val preserves
                // the underlying handle.
                let dbg = format!("{:?}", any);
                let rep = dbg
                    .strip_prefix("ErrorContextAny(")
                    .and_then(|s| s.strip_suffix(')'))
                    .and_then(|s| s.parse::<u32>().ok())
                    .unwrap_or(0);
                let table_id = error_contexts.park(val.clone());
                JsonVal::ErrorContext { table_id, rep }
            }
        })
    }
}

// -----------------------------------------------------------------------------
// Engine lifecycle
// -----------------------------------------------------------------------------

/// Create a wasmos-friendly wasmtime engine. `wasm_component_model = true` is
/// mandatory to load composed components; `epoch_interruption = true` is
/// mandatory so per-instance epoch deadlines are cheap to opt into via
/// `componentInstantiateWithConfig` (the flag is a no-op for callers who
/// don't set a deadline).
#[no_mangle]
pub extern "system" fn Java_ai_tegmentum_webassembly4j_provider_wasmos_jni_WasmosNative_engineCreate(
    mut env: JNIEnv,
    _class: JClass,
) -> jlong {
    let result: anyhow::Result<jlong> = (|| {
        let mut config = Config::new();
        // NB: wasmtime 47 removed `Config::async_support` — async is now
        // available on any engine that turned on `component-model` (which
        // it does by default). Explicit call intentionally omitted; leaving
        // this comment as a breadcrumb for any future wasmtime downgrade.
        config.wasm_component_model(true);
        config.epoch_interruption(true);
        // Enable fuel bookkeeping globally so per-instance fuel limits are
        // cheap to opt into via `componentInstantiateWithConfig`. No-op
        // performance hit for callers that never set fuel — wasmtime only
        // decrements on backedges when the store has a nonzero fuel budget.
        config.consume_fuel(true);
        let engine = Engine::new(&config)?;
        let runtime = tokio::runtime::Builder::new_multi_thread()
            .enable_all()
            .build()?;
        let handle = Box::new(EngineHandle { engine, runtime });
        Ok(Box::into_raw(handle) as jlong)
    })();
    or_throw(&mut env, result, 0)
}

/// Drop the engine and its Tokio runtime. Safe to call once with a
/// nonzero handle produced by `engineCreate`; no-op on zero.
///
/// # Safety
/// The handle MUST originate from `engineCreate`. Passing anything else is UB.
#[no_mangle]
pub unsafe extern "system" fn Java_ai_tegmentum_webassembly4j_provider_wasmos_jni_WasmosNative_engineClose(
    _env: JNIEnv,
    _class: JClass,
    handle: jlong,
) {
    if handle == 0 {
        return;
    }
    let _ = Box::from_raw(handle as *mut EngineHandle);
}

/// Increment the engine's epoch counter — call from a Java timer thread when
/// an instance was instantiated with `epochDeadline`. Callers that never set
/// a deadline can ignore this.
#[no_mangle]
pub extern "system" fn Java_ai_tegmentum_webassembly4j_provider_wasmos_jni_WasmosNative_engineIncrementEpoch(
    mut env: JNIEnv,
    _class: JClass,
    handle: jlong,
) {
    if handle == 0 {
        throw(&mut env, "wasmos-provider: engine handle is null");
        return;
    }
    let engine = unsafe { &(*(handle as *mut EngineHandle)).engine };
    engine.increment_epoch();
}

// -----------------------------------------------------------------------------
// Component lifecycle
// -----------------------------------------------------------------------------

/// Compile a Component from raw bytes. Returns a handle usable by
/// `componentInstantiate`.
#[no_mangle]
pub extern "system" fn Java_ai_tegmentum_webassembly4j_provider_wasmos_jni_WasmosNative_componentLoad(
    mut env: JNIEnv,
    _class: JClass,
    engine_handle: jlong,
    bytes: JByteArray,
) -> jlong {
    // Convert the byte array up-front so the fallible-body closure below
    // doesn't need to hold `&mut env`.
    let raw_result: anyhow::Result<Vec<u8>> = env
        .convert_byte_array(&bytes)
        .map_err(|e| anyhow::anyhow!("convert_byte_array: {e}"));
    let raw = match raw_result {
        Ok(v) => v,
        Err(e) => {
            throw(&mut env, &format!("wasmos-provider: {:#}", e));
            return 0;
        }
    };

    let result: anyhow::Result<jlong> = (|| {
        if engine_handle == 0 {
            anyhow::bail!("engine handle is null");
        }
        let engine_ptr = engine_handle as *mut EngineHandle;
        // SAFETY: We never move the Box; we only borrow through the raw pointer.
        // Java is responsible for keeping the engine alive as long as any component
        // handle derived from it is live (documented on WasmosEngineAdapter).
        let engine = unsafe { &(*engine_ptr).engine };
        let component = Component::from_binary(engine, &raw)?;
        let handle = Box::new(ComponentHandle {
            engine_ptr,
            component,
        });
        Ok(Box::into_raw(handle) as jlong)
    })();
    or_throw(&mut env, result, 0)
}

/// Drop a component handle. See `engineClose` safety note.
#[no_mangle]
pub unsafe extern "system" fn Java_ai_tegmentum_webassembly4j_provider_wasmos_jni_WasmosNative_componentClose(
    _env: JNIEnv,
    _class: JClass,
    handle: jlong,
) {
    if handle == 0 {
        return;
    }
    let _ = Box::from_raw(handle as *mut ComponentHandle);
}

/// Serialize a compiled Component into its cached AOT byte representation.
/// The returned blob can later be handed to `componentDeserialize` on an
/// engine with a compatible wasmtime config to skip the compile step —
/// the intended use case is warm restarts / hot-reload where the same
/// component gets loaded repeatedly across process launches.
///
/// Byte compatibility is tied to the wasmtime version, engine config, and
/// target ISA; a deserialize on a different setup will fail cleanly rather
/// than silently produce wrong code.
#[no_mangle]
pub extern "system" fn Java_ai_tegmentum_webassembly4j_provider_wasmos_jni_WasmosNative_componentSerialize(
    mut env: JNIEnv,
    _class: JClass,
    component_handle: jlong,
) -> jbyteArray {
    let result: anyhow::Result<Vec<u8>> = (|| {
        if component_handle == 0 {
            anyhow::bail!("component handle is null");
        }
        let ch = component_handle as *mut ComponentHandle;
        let component = unsafe { &(*ch).component };
        component
            .serialize()
            .map_err(|e| anyhow::anyhow!("Component::serialize: {e}"))
    })();
    match result {
        Ok(bytes) => match env.byte_array_from_slice(&bytes) {
            Ok(a) => a.into_raw(),
            Err(e) => {
                throw(&mut env, &format!("wasmos-provider: byte_array_from_slice: {e}"));
                std::ptr::null_mut()
            }
        },
        Err(e) => {
            throw(&mut env, &format!("wasmos-provider: {:#}", e));
            std::ptr::null_mut()
        }
    }
}

/// Deserialize a component previously produced by `componentSerialize`.
/// Returns a fresh ComponentHandle bound to the caller's engine.
///
/// # Safety
///
/// `Component::deserialize` is `unsafe` in wasmtime because it trusts the
/// input bytes to be a well-formed AOT image that the current engine
/// produced (a hostile / corrupted image can cause UB when its
/// pre-computed function pointers are called). The trust boundary belongs
/// to the JVM caller: they're responsible for only feeding this bytes
/// they got from a prior `componentSerialize` call on this or a
/// compatible engine. We don't try to validate here — the wasmtime API
/// puts the safety contract in the caller's hands and we relay it.
#[no_mangle]
pub extern "system" fn Java_ai_tegmentum_webassembly4j_provider_wasmos_jni_WasmosNative_componentDeserialize(
    mut env: JNIEnv,
    _class: JClass,
    engine_handle: jlong,
    bytes: JByteArray,
) -> jlong {
    let raw_result: anyhow::Result<Vec<u8>> = env
        .convert_byte_array(&bytes)
        .map_err(|e| anyhow::anyhow!("convert_byte_array: {e}"));
    let raw = match raw_result {
        Ok(v) => v,
        Err(e) => {
            throw(&mut env, &format!("wasmos-provider: {:#}", e));
            return 0;
        }
    };

    let result: anyhow::Result<jlong> = (|| {
        if engine_handle == 0 {
            anyhow::bail!("engine handle is null");
        }
        let engine_ptr = engine_handle as *mut EngineHandle;
        let engine = unsafe { &(*engine_ptr).engine };
        // SAFETY: The JVM caller warranted the input bytes came from a
        // prior componentSerialize call against a compatible engine. Any
        // failure will surface as an anyhow error caught below.
        let component = unsafe {
            Component::deserialize(engine, &raw)
                .map_err(|e| anyhow::anyhow!("Component::deserialize: {e}"))?
        };
        let handle = Box::new(ComponentHandle {
            engine_ptr,
            component,
        });
        Ok(Box::into_raw(handle) as jlong)
    })();
    or_throw(&mut env, result, 0)
}

// -----------------------------------------------------------------------------
// Instance lifecycle + invoke
// -----------------------------------------------------------------------------

/// Instantiate a component with the full wasmos-runtime host surface
/// (`add_all_to_linker`) plus `caps::wasi_p2` for the composed
/// adapter-wasmtime's wasi imports.
///
/// A fresh `Store<HostState>` is created per instantiation with a default
/// `HostState::new()` — empty preview-2 WasiCtx, no memory limits, no epoch
/// deadline. For a configured WASI ctx / memory-limits / epoch-deadline path,
/// use `componentInstantiateWithConfig`.
#[no_mangle]
pub extern "system" fn Java_ai_tegmentum_webassembly4j_provider_wasmos_jni_WasmosNative_componentInstantiate(
    mut env: JNIEnv,
    _class: JClass,
    component_handle: jlong,
) -> jlong {
    let result: anyhow::Result<jlong> = (|| {
        instantiate_with(component_handle, HostState::new(), LimitsBag::none())
    })();
    or_throw(&mut env, result, 0)
}

/// Same as `componentInstantiate` but with an optional configured WASI ctx
/// (built from the argsArr / envKeysArr / envValsArr / preopen arrays) plus
/// a `LimitsBag` struct of outer-store limits. Any `-1` numeric arg means
/// "unset".
///
/// Bit-flags in `flags`: 0x01=inheritStdin, 0x02=inheritStdout,
/// 0x04=inheritStderr, 0x08=hasWasiContext (if 0, all wasi args are ignored
/// and default HostState::new() is used).
///
/// Limit args (all `-1` = unset):
/// * `max_memory_bytes` — per-linear-memory upper bound in bytes
/// * `epoch_deadline` — trap when epoch counter passes this value
/// * `fuel_limit` — set the store's initial fuel budget (requires
///   `Config::consume_fuel(true)`, which the engine enables globally)
/// * `max_table_elements` — per-table element cap
/// * `max_instances` — max component-model instances per store
/// * `max_tables` — max tables per store
/// * `max_memories` — max linear memories per store
#[no_mangle]
pub extern "system" fn Java_ai_tegmentum_webassembly4j_provider_wasmos_jni_WasmosNative_componentInstantiateWithConfig(
    mut env: JNIEnv,
    _class: JClass,
    component_handle: jlong,
    flags: jint,
    args_arr: JObjectArray,
    env_keys_arr: JObjectArray,
    env_vals_arr: JObjectArray,
    preopen_hosts_arr: JObjectArray,
    preopen_guests_arr: JObjectArray,
    preopen_writable_arr: JByteArray,
    max_memory_bytes: jlong,
    epoch_deadline: jlong,
    fuel_limit: jlong,
    max_table_elements: jlong,
    max_instances: jlong,
    max_tables: jlong,
    max_memories: jlong,
) -> jlong {
    let has_wasi = (flags & 0x08) != 0;

    // Pull all jni-scoped inputs up front so the fallible body doesn't hold
    // multiple simultaneous borrows of &mut env.
    let host_state_result: anyhow::Result<HostState> = (|| {
        if !has_wasi {
            return Ok(HostState::new());
        }
        let inherit_stdin = (flags & 0x01) != 0;
        let inherit_stdout = (flags & 0x02) != 0;
        let inherit_stderr = (flags & 0x04) != 0;
        let args = if args_arr.is_null() {
            Vec::new()
        } else {
            jstringarray_to_vec(&mut env, &args_arr)?
        };
        let env_keys = if env_keys_arr.is_null() {
            Vec::new()
        } else {
            jstringarray_to_vec(&mut env, &env_keys_arr)?
        };
        let env_vals = if env_vals_arr.is_null() {
            Vec::new()
        } else {
            jstringarray_to_vec(&mut env, &env_vals_arr)?
        };
        if env_keys.len() != env_vals.len() {
            anyhow::bail!(
                "envKeys / envVals length mismatch: {} vs {}",
                env_keys.len(),
                env_vals.len()
            );
        }
        let preopen_hosts = if preopen_hosts_arr.is_null() {
            Vec::new()
        } else {
            jstringarray_to_vec(&mut env, &preopen_hosts_arr)?
        };
        let preopen_guests = if preopen_guests_arr.is_null() {
            Vec::new()
        } else {
            jstringarray_to_vec(&mut env, &preopen_guests_arr)?
        };
        let preopen_writable: Vec<u8> = if preopen_writable_arr.is_null() {
            Vec::new()
        } else {
            env.convert_byte_array(&preopen_writable_arr)
                .map_err(|e| anyhow::anyhow!("convert_byte_array(preopen_writable): {e}"))?
        };
        if preopen_hosts.len() != preopen_guests.len()
            || preopen_hosts.len() != preopen_writable.len()
        {
            anyhow::bail!(
                "preopen array length mismatch: hosts={} guests={} writable={}",
                preopen_hosts.len(),
                preopen_guests.len(),
                preopen_writable.len()
            );
        }

        let mut b = WasiCtxBuilder::new();
        if inherit_stdin {
            b.inherit_stdin();
        }
        if inherit_stdout {
            b.inherit_stdout();
        }
        if inherit_stderr {
            b.inherit_stderr();
        }
        for arg in &args {
            b.arg(arg);
        }
        for (k, v) in env_keys.iter().zip(env_vals.iter()) {
            b.env(k, v);
        }
        for i in 0..preopen_hosts.len() {
            let host_path = &preopen_hosts[i];
            let guest_path = &preopen_guests[i];
            let writable = preopen_writable[i] != 0;
            let (dp, fp) = if writable {
                (DirPerms::all(), FilePerms::all())
            } else {
                (DirPerms::READ, FilePerms::READ)
            };
            b.preopened_dir(host_path, guest_path, dp, fp)
                .map_err(|e| anyhow::anyhow!("preopened_dir({host_path} -> {guest_path}): {e}"))?;
        }
        Ok(HostState::with_wasi(b.build()))
    })();
    let host_state = match host_state_result {
        Ok(v) => v,
        Err(e) => {
            throw(&mut env, &format!("wasmos-provider: {:#}", e));
            return 0;
        }
    };

    let limits = LimitsBag {
        max_memory_bytes: neg_to_none(max_memory_bytes).map(|v| v as usize),
        epoch_deadline: neg_to_none(epoch_deadline).map(|v| v as u64),
        fuel_limit: neg_to_none(fuel_limit).map(|v| v as u64),
        max_table_elements: neg_to_none(max_table_elements).map(|v| v as usize),
        max_instances: neg_to_none(max_instances).map(|v| v as usize),
        max_tables: neg_to_none(max_tables).map(|v| v as usize),
        max_memories: neg_to_none(max_memories).map(|v| v as usize),
    };

    let result: anyhow::Result<jlong> = (|| {
        instantiate_with(component_handle, host_state, limits)
    })();
    or_throw(&mut env, result, 0)
}

/// `-1` (or any negative) → None; otherwise Some(v). Centralised so the
/// "-1 means unset" convention is threaded through one place, not every
/// call site.
fn neg_to_none(v: jlong) -> Option<jlong> {
    if v < 0 {
        None
    } else {
        Some(v)
    }
}

/// Outer-store limits bundle. Every field is optional — unset means "use
/// wasmtime's default" for that dimension. Fields map 1:1 onto
/// `StoreLimitsBuilder` setters (except `fuel_limit`, which goes through
/// `Store::set_fuel`, and `epoch_deadline`, which goes through
/// `Store::set_epoch_deadline` + `epoch_deadline_trap`).
struct LimitsBag {
    max_memory_bytes: Option<usize>,
    epoch_deadline: Option<u64>,
    fuel_limit: Option<u64>,
    max_table_elements: Option<usize>,
    max_instances: Option<usize>,
    max_tables: Option<usize>,
    max_memories: Option<usize>,
}

impl LimitsBag {
    fn none() -> Self {
        Self {
            max_memory_bytes: None,
            epoch_deadline: None,
            fuel_limit: None,
            max_table_elements: None,
            max_instances: None,
            max_tables: None,
            max_memories: None,
        }
    }

    /// True if any of the StoreLimits fields (memory / tables / memories /
    /// instances / table-elements) is set — used to decide whether to
    /// install `Store::limiter`. `fuel_limit` and `epoch_deadline` go
    /// through separate Store APIs so they don't count.
    fn wants_limiter(&self) -> bool {
        self.max_memory_bytes.is_some()
            || self.max_table_elements.is_some()
            || self.max_instances.is_some()
            || self.max_tables.is_some()
            || self.max_memories.is_some()
    }
}

/// Shared instantiate path. `host_state` supplies any WASI context;
/// `limits` bundles the optional outer-store limits (memory / fuel /
/// epoch / instances / tables / memories / table-elements).
fn instantiate_with(
    component_handle: jlong,
    mut host_state: HostState,
    limits: LimitsBag,
) -> anyhow::Result<jlong> {
    if component_handle == 0 {
        anyhow::bail!("component handle is null");
    }
    let ch = component_handle as *mut ComponentHandle;
    let engine_ptr = unsafe { (*ch).engine_ptr };
    let engine = unsafe { &(*engine_ptr).engine };
    let runtime = unsafe { &(*engine_ptr).runtime };
    let component_ref = unsafe { &(*ch).component };

    // Configure outer_limits BEFORE building the store — the limiter closure
    // borrows the field mutably. Only build a fresh StoreLimits when at
    // least one dimension was requested; otherwise leave HostState's
    // default (wide-open, matches pre-limits behaviour).
    if limits.wants_limiter() {
        let mut b = wasmtime::StoreLimitsBuilder::new();
        if let Some(v) = limits.max_memory_bytes {
            b = b.memory_size(v);
        }
        if let Some(v) = limits.max_table_elements {
            b = b.table_elements(v);
        }
        if let Some(v) = limits.max_instances {
            b = b.instances(v);
        }
        if let Some(v) = limits.max_tables {
            b = b.tables(v);
        }
        if let Some(v) = limits.max_memories {
            b = b.memories(v);
        }
        host_state.outer_limits = b.build();
    }

    let mut linker: Linker<HostState> = Linker::new(engine);
    wasmos_runtime::add_all_to_linker(&mut linker, |s| s)
        .map_err(|e| anyhow::anyhow!("add_all_to_linker: {e}"))?;
    caps::wasi_p2(&mut linker).map_err(|e| anyhow::anyhow!("caps::wasi_p2: {e}"))?;

    let mut store = Store::new(engine, host_state);
    if limits.wants_limiter() {
        store.limiter(|state| &mut state.outer_limits);
    }
    // The engine has epoch_interruption(true) globally so per-instance
    // deadlines are cheap to opt into. Without this, the store's default
    // deadline of 0 traps immediately on the first backedge check — even
    // for callers that never asked for epoch interruption. Setting to
    // u64::MAX effectively disables the trap for unopted-in callers.
    if let Some(deadline) = limits.epoch_deadline {
        // Trap when the deadline is exceeded — the simplest sane default;
        // yield-based deadlines can be added later behind a separate flag.
        store.epoch_deadline_trap();
        store.set_epoch_deadline(deadline);
    } else {
        store.set_epoch_deadline(u64::MAX);
    }
    // Fuel: engine has `consume_fuel(true)` globally so `set_fuel` is a
    // cheap opt-in per store. Wasmtime starts fuel-enabled stores at 0
    // fuel — which would trap the very first backedge for callers who
    // never asked for a fuel budget. Setting to u64::MAX by default
    // makes the "no fuel limit" case work identically to a fuel-disabled
    // engine from the caller's perspective; the small per-backedge
    // instrumentation cost is the price for lazy opt-in.
    let fuel = limits.fuel_limit.unwrap_or(u64::MAX);
    store
        .set_fuel(fuel)
        .map_err(|e| anyhow::anyhow!("set_fuel({fuel}): {e}"))?;

    let instance =
        runtime.block_on(async { linker.instantiate_async(&mut store, component_ref).await })?;

    let handle = Box::new(InstanceHandle {
        engine_ptr,
        store: Mutex::new(store),
        instance,
        resources: Mutex::new(ResourceRegistry::default()),
        futures: Mutex::new(FutureRegistry::default()),
        streams: Mutex::new(StreamRegistry::default()),
        error_contexts: Mutex::new(ErrorContextRegistry::default()),
    });
    Ok(Box::into_raw(handle) as jlong)
}

/// Drop an instance handle (which drops its Store<HostState>).
#[no_mangle]
pub unsafe extern "system" fn Java_ai_tegmentum_webassembly4j_provider_wasmos_jni_WasmosNative_instanceClose(
    _env: JNIEnv,
    _class: JClass,
    handle: jlong,
) {
    if handle == 0 {
        return;
    }
    let _ = Box::from_raw(handle as *mut InstanceHandle);
}

// -----------------------------------------------------------------------------
// Future handle lifecycle
// -----------------------------------------------------------------------------
// See the `FutureRegistry` docs for the parking-lot design; these two entry
// points are the Java-visible surface for disposing of a parked `FutureAny`
// and (attempting to) await its value. Await is a wasmtime-47 public-API gap
// (see below); close is fully functional.

/// Evict a parked `FutureAny` from the instance's future registry and call
/// `FutureAny::close(store)` on it so the write end sees the read end as
/// dropped. Idempotent-adjacent: passing an unknown id is an error (matches
/// the Resource contract), passing a null instance handle throws.
///
/// Safe to call on a WitFuture that was already consumed by a guest pass-in
/// only in the sense that the entry will have been removed — the error path
/// will surface as "unknown future id". Java-side callers should keep a
/// close call in a `finally` block when they own a WitFuture but never pass
/// it back to a guest.
#[no_mangle]
pub extern "system" fn Java_ai_tegmentum_webassembly4j_provider_wasmos_jni_WasmosNative_futureClose(
    mut env: JNIEnv,
    _class: JClass,
    instance_handle: jlong,
    future_id: jlong,
) {
    let result: anyhow::Result<()> = (|| {
        if instance_handle == 0 {
            anyhow::bail!("instance handle is null");
        }
        let ih = instance_handle as *mut InstanceHandle;
        let store_mutex = unsafe { &(*ih).store };
        let futures_mutex = unsafe { &(*ih).futures };
        let mut store = store_mutex.lock().unwrap();
        let mut futures = futures_mutex.lock().unwrap();
        let mut any = futures.take(future_id as u64).ok_or_else(|| {
            anyhow::anyhow!(
                "wasmos-provider: unknown future id {} (already closed, transferred \
                 to guest, or never parked)",
                future_id
            )
        })?;
        // FutureAny::close signals the writer that the read end is gone.
        // Swallow a "future already closed" error only implicitly (via the
        // take path above); a live registry hit that fails to close is a
        // real error worth surfacing.
        any.close(store.as_context_mut())
            .map_err(|e| anyhow::anyhow!("FutureAny::close: {e}"))?;
        Ok(())
    })();
    or_throw(&mut env, result, ());
}

/// Attempt to await a parked `FutureAny` and hand back the resolved value
/// as a JSON blob (same schema as `instanceInvokeJson` returns).
///
/// # wasmtime 47 API gap
///
/// This method currently ALWAYS throws `WebAssemblyException` with a clear
/// "wasmtime API gap" message. The `FutureAny` public API in wasmtime 47
/// only exposes:
///
/// * `try_into_future_reader::<T>()` — requires `T: ComponentType` at
///   COMPILE time. Not usable for our runtime-typed marshalling layer.
/// * `try_from_future_reader::<T>(store, reader)` — same static-T constraint,
///   only useful on the way into a guest.
/// * `close(&mut self, store)` — plain disposal, no value extraction.
///
/// The Rust-side lift path (`FutureReader<T>::pipe`) also needs
/// `T: Lift + 'static` compile-time. There is no `FutureAny::poll_dynamic` /
/// `FutureAny::await_val` shape in the public surface today. When one
/// lands upstream (see wasmtime issues around dynamically-typed
/// component-model concurrency), this method's body swaps in the real
/// implementation without touching the JNI signature.
///
/// The Java caller receives a `CompletableFuture` that completes
/// exceptionally — the async layer handles that shape naturally.
#[no_mangle]
pub extern "system" fn Java_ai_tegmentum_webassembly4j_provider_wasmos_jni_WasmosNative_futureAwait(
    mut env: JNIEnv,
    _class: JClass,
    instance_handle: jlong,
    future_id: jlong,
) -> jstring {
    let result: anyhow::Result<String> = (|| {
        if instance_handle == 0 {
            anyhow::bail!("instance handle is null");
        }
        let ih = instance_handle as *mut InstanceHandle;
        let futures_mutex = unsafe { &(*ih).futures };
        let futures = futures_mutex.lock().unwrap();
        if !futures.entries.contains_key(&(future_id as u64)) {
            anyhow::bail!(
                "wasmos-provider: unknown future id {} (already awaited/closed, \
                 transferred to guest, or never parked)",
                future_id
            );
        }
        // Registry hit — the handle is real. But we can't resolve it: the
        // wasmtime 47 public API has no dynamic-payload-type await surface.
        // See this function's doc comment for the API gap details.
        anyhow::bail!(
            "wasmos-provider: FutureAny host-side await is not supported by wasmtime 47's \
             public API (FutureAny::try_into_future_reader<T> requires a compile-time T, \
             and FutureConsumer::Item is an associated type — there is no type-erased \
             await path). The WitFuture can still be closed via WasmosAsyncExtension.closeFuture \
             or passed back into a guest import that consumes it. This is tracked as slice-1's \
             known limitation; a real await lands when wasmtime exposes a dynamic API."
        )
    })();
    match result {
        Ok(s) => match env.new_string(s) {
            Ok(js) => js.into_raw(),
            Err(e) => {
                throw(&mut env, &format!("wasmos-provider: new_string: {e}"));
                std::ptr::null_mut()
            }
        },
        Err(e) => {
            throw(&mut env, &format!("wasmos-provider: {:#}", e));
            std::ptr::null_mut()
        }
    }
}

// -----------------------------------------------------------------------------
// Stream handle lifecycle
// -----------------------------------------------------------------------------
// Structural mirror of the Future entries above. Same wasmtime 47 API gap —
// there is no dynamic-payload-type read surface on `StreamAny` (only the
// compile-time-typed `try_into_stream_reader::<T>`), so `streamRead` is
// intentionally a stub-that-throws matching `futureAwait`. Close and pass-in
// are fully wired.

/// Evict a parked `StreamAny` from the instance's stream registry and call
/// `StreamAny::close(store)` on it so the write end sees the read end as
/// dropped. Symmetric with `futureClose`.
#[no_mangle]
pub extern "system" fn Java_ai_tegmentum_webassembly4j_provider_wasmos_jni_WasmosNative_streamClose(
    mut env: JNIEnv,
    _class: JClass,
    instance_handle: jlong,
    stream_id: jlong,
) {
    let result: anyhow::Result<()> = (|| {
        if instance_handle == 0 {
            anyhow::bail!("instance handle is null");
        }
        let ih = instance_handle as *mut InstanceHandle;
        let store_mutex = unsafe { &(*ih).store };
        let streams_mutex = unsafe { &(*ih).streams };
        let mut store = store_mutex.lock().unwrap();
        let mut streams = streams_mutex.lock().unwrap();
        let mut any = streams.take(stream_id as u64).ok_or_else(|| {
            anyhow::anyhow!(
                "wasmos-provider: unknown stream id {} (already closed, transferred \
                 to guest, or never parked)",
                stream_id
            )
        })?;
        any.close(store.as_context_mut())
            .map_err(|e| anyhow::anyhow!("StreamAny::close: {e}"))?;
        Ok(())
    })();
    or_throw(&mut env, result, ());
}

/// Attempt to read from a parked `StreamAny`. Currently ALWAYS throws
/// with a clear "wasmtime API gap" message — the same reason `futureAwait`
/// does. Kept as a stable JNI entry so the Java surface is future-proof:
/// when wasmtime exposes a dynamic-payload-type read surface for
/// `StreamAny`, only the Rust body swaps in.
///
/// # wasmtime 47 API gap
///
/// `StreamAny`'s public API in wasmtime 47 only exposes
/// `try_into_stream_reader::<T>()` (requires a compile-time `T`) and
/// `close(store)`. There is no `try_read_dynamic` / `poll_next_dynamic`
/// surface, so runtime-typed marshalling cannot lift stream items to
/// `Val`s here.
#[no_mangle]
pub extern "system" fn Java_ai_tegmentum_webassembly4j_provider_wasmos_jni_WasmosNative_streamRead(
    mut env: JNIEnv,
    _class: JClass,
    instance_handle: jlong,
    stream_id: jlong,
) -> jstring {
    let result: anyhow::Result<String> = (|| {
        if instance_handle == 0 {
            anyhow::bail!("instance handle is null");
        }
        let ih = instance_handle as *mut InstanceHandle;
        let streams_mutex = unsafe { &(*ih).streams };
        let streams = streams_mutex.lock().unwrap();
        if !streams.entries.contains_key(&(stream_id as u64)) {
            anyhow::bail!(
                "wasmos-provider: unknown stream id {} (already read/closed, \
                 transferred to guest, or never parked)",
                stream_id
            );
        }
        // Registry hit — same API-gap story as `futureAwait`.
        anyhow::bail!(
            "wasmos-provider: StreamAny host-side read is not supported by wasmtime 47's \
             public API (StreamAny::try_into_stream_reader<T> requires a compile-time T, \
             and there is no type-erased next/poll surface). The WitStream can still be \
             closed via WasmosAsyncExtension.closeStream or passed back into a guest \
             import that consumes it. This is tracked as slice-2's known limitation; a \
             real read lands when wasmtime exposes a dynamic API."
        )
    })();
    match result {
        Ok(s) => match env.new_string(s) {
            Ok(js) => js.into_raw(),
            Err(e) => {
                throw(&mut env, &format!("wasmos-provider: new_string: {e}"));
                std::ptr::null_mut()
            }
        },
        Err(e) => {
            throw(&mut env, &format!("wasmos-provider: {:#}", e));
            std::ptr::null_mut()
        }
    }
}

// -----------------------------------------------------------------------------
// ErrorContext handle lifecycle
// -----------------------------------------------------------------------------
// wasmtime 47's `ErrorContextAny` is a placeholder (see `FIXME(#11161)` on the
// wasmtime side) — it has no publicly-defined `close`, `drop`, or any accessor
// beyond `Debug`. All we can do is evict from our registry.

/// Evict a parked `ErrorContextAny` from the instance's registry. Pure
/// Rust-side eviction — wasmtime has no dispose surface on
/// `ErrorContextAny`, so a Java caller who is done with a WitErrorContext
/// simply drops the slot to keep the registry from growing.
#[no_mangle]
pub extern "system" fn Java_ai_tegmentum_webassembly4j_provider_wasmos_jni_WasmosNative_errorContextClose(
    mut env: JNIEnv,
    _class: JClass,
    instance_handle: jlong,
    error_context_id: jlong,
) {
    let result: anyhow::Result<()> = (|| {
        if instance_handle == 0 {
            anyhow::bail!("instance handle is null");
        }
        let ih = instance_handle as *mut InstanceHandle;
        let ec_mutex = unsafe { &(*ih).error_contexts };
        let mut ec = ec_mutex.lock().unwrap();
        ec.take(error_context_id as u64).ok_or_else(|| {
            anyhow::anyhow!(
                "wasmos-provider: unknown error-context id {} (already closed or \
                 never parked)",
                error_context_id
            )
        })?;
        Ok(())
    })();
    or_throw(&mut env, result, ());
}

/// Invoke an exported function with signature `func() -> i32`. Kept as a
/// nullary fast path for the demo (`guest-demo-portable`'s `run` export) —
/// skips the JSON round-trip that `instanceInvokeJson` incurs.
///
/// Returns the i32 result; on any error, throws `WebAssemblyException` and
/// returns 0.
#[no_mangle]
pub extern "system" fn Java_ai_tegmentum_webassembly4j_provider_wasmos_jni_WasmosNative_instanceInvokeReturningI32(
    mut env: JNIEnv,
    _class: JClass,
    instance_handle: jlong,
    name: JString,
) -> jint {
    let fn_name = match jstring_to_string(&mut env, &name) {
        Ok(v) => v,
        Err(e) => {
            throw(&mut env, &format!("wasmos-provider: {:#}", e));
            return 0;
        }
    };

    let result: anyhow::Result<jint> = (|| {
        if instance_handle == 0 {
            anyhow::bail!("instance handle is null");
        }
        let ih = instance_handle as *mut InstanceHandle;
        let engine_ptr = unsafe { (*ih).engine_ptr };
        let runtime = unsafe { &(*engine_ptr).runtime };

        let store_mutex = unsafe { &(*ih).store };
        let instance = unsafe { &(*ih).instance };
        let mut store = store_mutex.lock().unwrap();

        let typed = instance
            .get_typed_func::<(), (i32,)>(&mut *store, &fn_name)
            .map_err(|e| anyhow::anyhow!("get_typed_func('{}'): {}", fn_name, e))?;

        let (out,) = runtime.block_on(async { typed.call_async(&mut *store, ()).await })?;

        Ok(out)
    })();
    or_throw(&mut env, result, 0)
}

/// General invoke path — hand over a JSON array of typed argument `JsonVal`s,
/// receive a JSON array of typed result `JsonVal`s. See the `JsonVal`
/// definition above for the schema; the Java-side `WasmosMarshalling`
/// mirrors it.
///
/// For a `func() -> ()` return, the Rust side returns `"[]"`. On error,
/// throws `WebAssemblyException` and returns null.
#[no_mangle]
pub extern "system" fn Java_ai_tegmentum_webassembly4j_provider_wasmos_jni_WasmosNative_instanceInvokeJson(
    mut env: JNIEnv,
    _class: JClass,
    instance_handle: jlong,
    name: JString,
    args_json: JString,
) -> jstring {
    let fn_name = match jstring_to_string(&mut env, &name) {
        Ok(v) => v,
        Err(e) => {
            throw(&mut env, &format!("wasmos-provider: {:#}", e));
            return std::ptr::null_mut();
        }
    };
    let args_raw = match jstring_to_string(&mut env, &args_json) {
        Ok(v) => v,
        Err(e) => {
            throw(&mut env, &format!("wasmos-provider: {:#}", e));
            return std::ptr::null_mut();
        }
    };

    let result: anyhow::Result<String> = (|| {
        if instance_handle == 0 {
            anyhow::bail!("instance handle is null");
        }
        let json_args: Vec<JsonVal> = serde_json::from_str(&args_raw)
            .map_err(|e| anyhow::anyhow!("deserialize args JSON: {e}"))?;

        let ih = instance_handle as *mut InstanceHandle;
        let engine_ptr = unsafe { (*ih).engine_ptr };
        let runtime = unsafe { &(*engine_ptr).runtime };
        let store_mutex = unsafe { &(*ih).store };
        let instance = unsafe { &(*ih).instance };
        let resources_mutex = unsafe { &(*ih).resources };
        let futures_mutex = unsafe { &(*ih).futures };
        let streams_mutex = unsafe { &(*ih).streams };
        let error_contexts_mutex = unsafe { &(*ih).error_contexts };
        let mut store = store_mutex.lock().unwrap();
        let mut resources = resources_mutex.lock().unwrap();
        let mut futures = futures_mutex.lock().unwrap();
        let mut streams = streams_mutex.lock().unwrap();
        let mut error_contexts = error_contexts_mutex.lock().unwrap();

        // Decode args AFTER acquiring the registry locks — the Resource /
        // Future / Stream / ErrorContext arms call take/peek against those
        // registries.
        let mut params: Vec<Val> = Vec::with_capacity(json_args.len());
        for jv in &json_args {
            params.push(jv.to_val(
                &mut resources,
                &mut futures,
                &mut streams,
                &mut error_contexts,
            )?);
        }

        let func = resolve_func(instance, &mut store, &fn_name)
            .ok_or_else(|| anyhow::anyhow!("no exported function '{}'", fn_name))?;

        // Preallocate results — wasmtime `Func::call_async` needs a slice
        // sized to the return arity. Grab that from the func's declared type.
        let ty = func.ty(&*store);
        let result_arity = ty.results().len();
        let mut results: Vec<Val> = vec![Val::Bool(false); result_arity];

        runtime.block_on(async {
            func.call_async(&mut *store, &params, &mut results).await
        })?;
        // NB: post_return_async is a no-op in wasmtime 47 (per the crate's
        // #[deprecated] annotation) — omitted intentionally.

        // Encoding the return path parks any Resource(any) / Future(any) /
        // Stream(any) / ErrorContext(any) values in their registries — Java
        // sees the fresh slot ids in the JSON blob.
        let json_results: Vec<JsonVal> = results
            .iter()
            .map(|v| JsonVal::from_val(
                v,
                &mut resources,
                &mut futures,
                &mut streams,
                &mut error_contexts,
            ))
            .collect::<anyhow::Result<Vec<_>>>()?;
        Ok(serde_json::to_string(&json_results)
            .map_err(|e| anyhow::anyhow!("serialize results JSON: {e}"))?)
    })();

    match result {
        Ok(s) => match env.new_string(s) {
            Ok(js) => js.into_raw(),
            Err(e) => {
                throw(&mut env, &format!("wasmos-provider: new_string: {e}"));
                std::ptr::null_mut()
            }
        },
        Err(e) => {
            throw(&mut env, &format!("wasmos-provider: {:#}", e));
            std::ptr::null_mut()
        }
    }
}

/// Byte-array-returning variant of `instanceInvokeJson`. Callers who know
/// the exported function returns exactly one `list<u8>` can skip the JSON
/// detour on the return path — we hand back a raw jbyteArray of the
/// element bytes. Args still go through JSON (rare enough that we don't
/// bother with a fast-path array-in surface).
///
/// If the return arity isn't 1, or the sole result isn't a `list<u8>`,
/// throws `WebAssemblyException`. Returns null on error.
#[no_mangle]
pub extern "system" fn Java_ai_tegmentum_webassembly4j_provider_wasmos_jni_WasmosNative_instanceInvokeJsonReturningBytes(
    mut env: JNIEnv,
    _class: JClass,
    instance_handle: jlong,
    name: JString,
    args_json: JString,
) -> jbyteArray {
    let fn_name = match jstring_to_string(&mut env, &name) {
        Ok(v) => v,
        Err(e) => {
            throw(&mut env, &format!("wasmos-provider: {:#}", e));
            return std::ptr::null_mut();
        }
    };
    let args_raw = match jstring_to_string(&mut env, &args_json) {
        Ok(v) => v,
        Err(e) => {
            throw(&mut env, &format!("wasmos-provider: {:#}", e));
            return std::ptr::null_mut();
        }
    };

    let result: anyhow::Result<Vec<u8>> = (|| {
        if instance_handle == 0 {
            anyhow::bail!("instance handle is null");
        }
        let json_args: Vec<JsonVal> = serde_json::from_str(&args_raw)
            .map_err(|e| anyhow::anyhow!("deserialize args JSON: {e}"))?;

        let ih = instance_handle as *mut InstanceHandle;
        let engine_ptr = unsafe { (*ih).engine_ptr };
        let runtime = unsafe { &(*engine_ptr).runtime };
        let store_mutex = unsafe { &(*ih).store };
        let instance = unsafe { &(*ih).instance };
        let resources_mutex = unsafe { &(*ih).resources };
        let futures_mutex = unsafe { &(*ih).futures };
        let streams_mutex = unsafe { &(*ih).streams };
        let error_contexts_mutex = unsafe { &(*ih).error_contexts };
        let mut store = store_mutex.lock().unwrap();
        let mut resources = resources_mutex.lock().unwrap();
        let mut futures = futures_mutex.lock().unwrap();
        let mut streams = streams_mutex.lock().unwrap();
        let mut error_contexts = error_contexts_mutex.lock().unwrap();

        // Resolve any Resource / Future / Stream / ErrorContext args after
        // grabbing the registry locks.
        let mut params: Vec<Val> = Vec::with_capacity(json_args.len());
        for jv in &json_args {
            params.push(jv.to_val(
                &mut resources,
                &mut futures,
                &mut streams,
                &mut error_contexts,
            )?);
        }
        // Result path here is byte-array-only — no need to hold the registry
        // borrows after params decode, but keeping them live is cheap and
        // future-proofs any list<u8>-adjacent variants that might carry
        // resource / future / stream / error-context handles alongside the
        // bytes.
        let _ = &mut *resources;
        let _ = &mut *futures;
        let _ = &mut *streams;
        let _ = &mut *error_contexts;

        let func = resolve_func(instance, &mut store, &fn_name)
            .ok_or_else(|| anyhow::anyhow!("no exported function '{}'", fn_name))?;
        let ty = func.ty(&*store);
        let result_arity = ty.results().len();
        if result_arity != 1 {
            anyhow::bail!(
                "invokeBytes expects exactly one return value, got {}",
                result_arity
            );
        }
        let mut results: Vec<Val> = vec![Val::Bool(false); 1];
        runtime.block_on(async {
            func.call_async(&mut *store, &params, &mut results).await
        })?;
        // post_return_async is a no-op in wasmtime 47 (deprecated).

        match &results[0] {
            Val::List(items) => {
                let mut out = Vec::with_capacity(items.len());
                for v in items {
                    match v {
                        Val::U8(b) => out.push(*b),
                        other => anyhow::bail!(
                            "invokeBytes expects list<u8>, got list element {:?}",
                            other
                        ),
                    }
                }
                Ok(out)
            }
            Val::String(s) => Ok(s.as_bytes().to_vec()),
            other => anyhow::bail!(
                "invokeBytes expects list<u8> or string result, got {:?}",
                other
            ),
        }
    })();

    match result {
        Ok(bytes) => match env.byte_array_from_slice(&bytes) {
            Ok(a) => a.into_raw(),
            Err(e) => {
                throw(&mut env, &format!("wasmos-provider: byte_array_from_slice: {e}"));
                std::ptr::null_mut()
            }
        },
        Err(e) => {
            throw(&mut env, &format!("wasmos-provider: {:#}", e));
            std::ptr::null_mut()
        }
    }
}

/// Return list of exported function names, as a null-separated UTF-8 blob in a
/// jbyteArray. Java splits on 0x00. Keeping it a byte blob avoids per-call
/// jstring allocation loops for what's an occasional metadata read.
#[no_mangle]
pub extern "system" fn Java_ai_tegmentum_webassembly4j_provider_wasmos_jni_WasmosNative_componentExportedFunctionNames(
    mut env: JNIEnv,
    _class: JClass,
    component_handle: jlong,
) -> jbyteArray {
    let names_result: anyhow::Result<Vec<String>> = (|| {
        if component_handle == 0 {
            anyhow::bail!("component handle is null");
        }
        let ch = component_handle as *mut ComponentHandle;
        let engine_ptr = unsafe { (*ch).engine_ptr };
        let engine = unsafe { &(*engine_ptr).engine };
        let component = unsafe { &(*ch).component };
        let component_type = component.component_type();
        let mut names: Vec<String> = Vec::new();
        for (name, item) in component_type.exports(engine) {
            if let wasmtime::component::types::ComponentItem::ComponentFunc(_) = item.ty {
                names.push(name.to_string());
            }
        }
        Ok(names)
    })();
    let names = match names_result {
        Ok(v) => v,
        Err(e) => {
            throw(&mut env, &format!("wasmos-provider: {:#}", e));
            return std::ptr::null_mut();
        }
    };
    let joined = names.join("\0");
    match env.byte_array_from_slice(joined.as_bytes()) {
        Ok(a) => a.into_raw(),
        Err(e) => {
            throw(&mut env, &format!("wasmos-provider: byte_array_from_slice: {e}"));
            std::ptr::null_mut()
        }
    }
}

/// Introspect an instance's function signature — returns "argCount:resultCount"
/// as an ASCII byte blob. Used by the Java-side marshalling helper when it
/// needs to size a param list without a prior WIT descriptor. Both counts are
/// jint-sized; negative values would indicate an error but we always throw
/// instead. Returns null on error; jboolean sentinel isn't in scope.
#[no_mangle]
pub extern "system" fn Java_ai_tegmentum_webassembly4j_provider_wasmos_jni_WasmosNative_instanceFunctionArity(
    mut env: JNIEnv,
    _class: JClass,
    instance_handle: jlong,
    name: JString,
) -> jbyteArray {
    let fn_name = match jstring_to_string(&mut env, &name) {
        Ok(v) => v,
        Err(e) => {
            throw(&mut env, &format!("wasmos-provider: {:#}", e));
            return std::ptr::null_mut();
        }
    };
    let result: anyhow::Result<(usize, usize)> = (|| {
        if instance_handle == 0 {
            anyhow::bail!("instance handle is null");
        }
        let ih = instance_handle as *mut InstanceHandle;
        let store_mutex = unsafe { &(*ih).store };
        let instance = unsafe { &(*ih).instance };
        let mut store = store_mutex.lock().unwrap();
        let func = resolve_func(instance, &mut store, &fn_name)
            .ok_or_else(|| anyhow::anyhow!("no exported function '{}'", fn_name))?;
        let ty = func.ty(&*store);
        let arity = (ty.params().len(), ty.results().len());
        Ok(arity)
    })();
    let (args, rets) = match result {
        Ok(v) => v,
        Err(e) => {
            throw(&mut env, &format!("wasmos-provider: {:#}", e));
            return std::ptr::null_mut();
        }
    };
    let s = format!("{args}:{rets}");
    match env.byte_array_from_slice(s.as_bytes()) {
        Ok(a) => a.into_raw(),
        Err(e) => {
            throw(&mut env, &format!("wasmos-provider: byte_array_from_slice: {e}"));
            std::ptr::null_mut()
        }
    }
}

/// Report whether an instance exports a given function name. Cheaper than
/// pulling the whole export list when a Java caller only wants a hasFunction
/// probe. Returns JNI_TRUE / JNI_FALSE. Throws on invalid handle.
#[no_mangle]
pub extern "system" fn Java_ai_tegmentum_webassembly4j_provider_wasmos_jni_WasmosNative_instanceHasFunction(
    mut env: JNIEnv,
    _class: JClass,
    instance_handle: jlong,
    name: JString,
) -> jboolean {
    let fn_name = match jstring_to_string(&mut env, &name) {
        Ok(v) => v,
        Err(e) => {
            throw(&mut env, &format!("wasmos-provider: {:#}", e));
            return 0;
        }
    };
    if instance_handle == 0 {
        throw(&mut env, "wasmos-provider: instance handle is null");
        return 0;
    }
    let ih = instance_handle as *mut InstanceHandle;
    let store_mutex = unsafe { &(*ih).store };
    let instance = unsafe { &(*ih).instance };
    let mut store = store_mutex.lock().unwrap();
    if resolve_func(instance, &mut store, &fn_name).is_some() {
        1
    } else {
        0
    }
}

// -----------------------------------------------------------------------------
// Tests — Rust-side smoke tests for the JsonVal marshalling helpers. Confirm
// the on-the-wire schema is stable and round-trips cleanly for every variant
// the Java side is expected to send.
// -----------------------------------------------------------------------------

#[cfg(test)]
mod tests {
    use super::*;

    fn roundtrip(v: Val) {
        let mut reg = ResourceRegistry::default();
        let mut futs = FutureRegistry::default();
        let mut strs = StreamRegistry::default();
        let mut ecs = ErrorContextRegistry::default();
        let jv = JsonVal::from_val(&v, &mut reg, &mut futs, &mut strs, &mut ecs)
            .expect("from_val");
        let json = serde_json::to_string(&jv).expect("serialize");
        let back: JsonVal = serde_json::from_str(&json).expect("deserialize");
        let v2 = back
            .to_val(&mut reg, &mut futs, &mut strs, &mut ecs)
            .expect("to_val");
        // Val doesn't implement PartialEq; compare Debug reprs.
        assert_eq!(format!("{:?}", v), format!("{:?}", v2));
    }

    #[test]
    fn primitives_roundtrip() {
        roundtrip(Val::Bool(true));
        roundtrip(Val::S8(-1));
        roundtrip(Val::U8(200));
        roundtrip(Val::S16(-1234));
        roundtrip(Val::U16(60000));
        roundtrip(Val::S32(-42));
        roundtrip(Val::U32(4_000_000_000));
        roundtrip(Val::S64(-1_000_000_000_000));
        roundtrip(Val::U64(u64::MAX));
        roundtrip(Val::Float32(1.5));
        roundtrip(Val::Float64(-2.25));
        roundtrip(Val::Char('Z'));
        roundtrip(Val::String("hello".to_string()));
    }

    #[test]
    fn list_and_bytes_roundtrip() {
        // Empty list stays empty list (not Bytes fast path).
        roundtrip(Val::List(vec![]));
        // All-U8 list picks up the base64 fast path — check the wire form is
        // Bytes and it decodes back to Val::List(U8).
        let bytes = Val::List(vec![Val::U8(0), Val::U8(1), Val::U8(2), Val::U8(255)]);
        let mut reg = ResourceRegistry::default();
        let mut futs = FutureRegistry::default();
        let mut strs = StreamRegistry::default();
        let mut ecs = ErrorContextRegistry::default();
        let jv = JsonVal::from_val(&bytes, &mut reg, &mut futs, &mut strs, &mut ecs).unwrap();
        assert!(matches!(jv, JsonVal::Bytes(_)));
        roundtrip(bytes);
        // Non-U8 list stays a List.
        roundtrip(Val::List(vec![Val::S32(1), Val::S32(2), Val::S32(3)]));
    }

    #[test]
    fn option_and_result_roundtrip() {
        roundtrip(Val::Option(None));
        roundtrip(Val::Option(Some(Box::new(Val::S32(7)))));
        roundtrip(Val::Result(Ok(Some(Box::new(Val::String("ok".into()))))));
        roundtrip(Val::Result(Err(Some(Box::new(Val::S32(-1))))));
        roundtrip(Val::Result(Ok(None)));
        roundtrip(Val::Result(Err(None)));
    }

    #[test]
    fn record_and_tuple_roundtrip() {
        roundtrip(Val::Tuple(vec![Val::S32(1), Val::String("x".into())]));
        roundtrip(Val::Record(vec![
            ("name".into(), Val::String("alice".into())),
            ("age".into(), Val::U32(30)),
        ]));
    }

    #[test]
    fn variant_and_enum_and_flags_roundtrip() {
        roundtrip(Val::Variant("some".into(), Some(Box::new(Val::S32(1)))));
        roundtrip(Val::Variant("none".into(), None));
        roundtrip(Val::Enum("red".into()));
        roundtrip(Val::Flags(vec!["read".into(), "write".into()]));
    }

    #[test]
    fn map_roundtrip() {
        // Map<i32, string> — keys are non-string so this MUST land as
        // Val::Map, not Val::Record.
        roundtrip(Val::Map(vec![
            (Val::S32(1), Val::String("one".into())),
            (Val::S32(2), Val::String("two".into())),
        ]));
        // Empty map.
        roundtrip(Val::Map(vec![]));
        // Map<string, list<u8>> — checks that inner Bytes fast path
        // survives when nested inside a Map value slot.
        roundtrip(Val::Map(vec![
            (
                Val::String("a".into()),
                Val::List(vec![Val::U8(1), Val::U8(2), Val::U8(3)]),
            ),
            (
                Val::String("b".into()),
                Val::List(vec![Val::U8(255), Val::U8(0)]),
            ),
        ]));
    }

    #[test]
    fn resource_json_wire_format_is_stable() {
        // We can't easily fabricate a ResourceAny here (its constructor is
        // pub(crate)), but the JsonVal::Resource wire schema is what the
        // Java side depends on. Round-trip via serde to lock in field
        // names — a rename would silently break Java parsing otherwise.
        let jv = JsonVal::Resource {
            table_id: 42,
            type_name: "TestType".into(),
            owned: true,
        };
        let json = serde_json::to_string(&jv).unwrap();
        assert!(
            json.contains("\"Resource\"") && json.contains("\"table_id\":42")
                && json.contains("\"type_name\":\"TestType\"")
                && json.contains("\"owned\":true"),
            "wire format regression: {json}"
        );
        let back: JsonVal = serde_json::from_str(&json).unwrap();
        match back {
            JsonVal::Resource {
                table_id,
                type_name,
                owned,
            } => {
                assert_eq!(table_id, 42);
                assert_eq!(type_name, "TestType");
                assert!(owned);
            }
            other => panic!("expected Resource, got {other:?}"),
        }
    }

    #[test]
    fn resource_registry_starts_empty() {
        // Baseline invariants for the ResourceRegistry — full park/take
        // exercise happens through the JNI path once a live ResourceAny is
        // available (needs a real component-model resource). We can't
        // fabricate a ResourceAny here because its constructor is
        // pub(crate); a zeroed ResourceAny would be UB (invalid
        // HostResourceIndex). The full lifecycle is covered end-to-end via
        // Java-side round-trip tests plus the E2E-with-component path.
        let reg = ResourceRegistry::default();
        assert_eq!(reg.next_id, 0);
        assert!(reg.entries.is_empty());
        assert!(reg.peek(0).is_none());
    }

    #[test]
    fn resource_to_val_errors_on_unknown_id() {
        // The Resource decode arm must fail cleanly when Java sends a
        // slot id that isn't parked. Two failure modes: owned=true takes
        // (miss on second use); owned=false peeks (miss when never parked).
        let mut reg = ResourceRegistry::default();
        let mut futs = FutureRegistry::default();
        let mut strs = StreamRegistry::default();
        let mut ecs = ErrorContextRegistry::default();
        let jv_take = JsonVal::Resource {
            table_id: 12345,
            type_name: "T".into(),
            owned: true,
        };
        let err = jv_take
            .to_val(&mut reg, &mut futs, &mut strs, &mut ecs)
            .unwrap_err()
            .to_string();
        assert!(
            err.contains("12345") && err.contains("unknown resource id"),
            "unexpected error: {err}"
        );
        let jv_peek = JsonVal::Resource {
            table_id: 999,
            type_name: "T".into(),
            owned: false,
        };
        let err = jv_peek
            .to_val(&mut reg, &mut futs, &mut strs, &mut ecs)
            .unwrap_err()
            .to_string();
        assert!(
            err.contains("999") && err.contains("unknown resource id"),
            "unexpected error: {err}"
        );
    }

    #[test]
    fn future_json_wire_format_is_stable() {
        // JsonVal::Future wire schema — the Java side keys on the exact
        // field names, so a rename here would silently break parsing.
        let jv = JsonVal::Future {
            table_id: 17,
            type_name: "FutureAny { .. }".into(),
        };
        let json = serde_json::to_string(&jv).unwrap();
        assert!(
            json.contains("\"Future\"")
                && json.contains("\"table_id\":17")
                && json.contains("\"type_name\":\"FutureAny { .. }\""),
            "wire format regression: {json}"
        );
        let back: JsonVal = serde_json::from_str(&json).unwrap();
        match back {
            JsonVal::Future { table_id, type_name } => {
                assert_eq!(table_id, 17);
                assert_eq!(type_name, "FutureAny { .. }");
            }
            other => panic!("expected Future, got {other:?}"),
        }
    }

    #[test]
    fn future_registry_starts_empty() {
        // Same coverage shape as `resource_registry_starts_empty`.
        // Fabricating a live FutureAny requires a real component-model
        // future value on a live store, so the full park/take exercise
        // lives on the Java-side integration path (once a fixture with a
        // `future<T>` export exists).
        let reg = FutureRegistry::default();
        assert_eq!(reg.next_id, 0);
        assert!(reg.entries.is_empty());
        assert!(reg.peek(0).is_none());
    }

    #[test]
    fn future_to_val_errors_on_unknown_id() {
        // The Future decode arm always takes (there's no borrow shape for
        // futures — see the JsonVal::Future doc comment) so a single failure
        // mode is enough here.
        let mut reg = ResourceRegistry::default();
        let mut futs = FutureRegistry::default();
        let mut strs = StreamRegistry::default();
        let mut ecs = ErrorContextRegistry::default();
        let jv = JsonVal::Future {
            table_id: 54321,
            type_name: "T".into(),
        };
        let err = jv
            .to_val(&mut reg, &mut futs, &mut strs, &mut ecs)
            .unwrap_err()
            .to_string();
        assert!(
            err.contains("54321") && err.contains("unknown future id"),
            "unexpected error: {err}"
        );
    }

    #[test]
    fn stream_json_wire_format_is_stable() {
        // Mirrors `future_json_wire_format_is_stable` — the Java side keys on
        // the exact field names, so a rename here would silently break parsing.
        let jv = JsonVal::Stream {
            table_id: 21,
            type_name: "StreamAny { .. }".into(),
        };
        let json = serde_json::to_string(&jv).unwrap();
        assert!(
            json.contains("\"Stream\"")
                && json.contains("\"table_id\":21")
                && json.contains("\"type_name\":\"StreamAny { .. }\""),
            "wire format regression: {json}"
        );
        let back: JsonVal = serde_json::from_str(&json).unwrap();
        match back {
            JsonVal::Stream { table_id, type_name } => {
                assert_eq!(table_id, 21);
                assert_eq!(type_name, "StreamAny { .. }");
            }
            other => panic!("expected Stream, got {other:?}"),
        }
    }

    #[test]
    fn stream_registry_starts_empty() {
        // Same coverage shape as `future_registry_starts_empty`; a full
        // park/take exercise needs a live StreamAny value, which requires a
        // real component-model stream on a live store.
        let reg = StreamRegistry::default();
        assert_eq!(reg.next_id, 0);
        assert!(reg.entries.is_empty());
        assert!(reg.peek(0).is_none());
    }

    #[test]
    fn stream_to_val_errors_on_unknown_id() {
        // Stream decode always takes — mirrors the Future contract.
        let mut reg = ResourceRegistry::default();
        let mut futs = FutureRegistry::default();
        let mut strs = StreamRegistry::default();
        let mut ecs = ErrorContextRegistry::default();
        let jv = JsonVal::Stream {
            table_id: 7777,
            type_name: "T".into(),
        };
        let err = jv
            .to_val(&mut reg, &mut futs, &mut strs, &mut ecs)
            .unwrap_err()
            .to_string();
        assert!(
            err.contains("7777") && err.contains("unknown stream id"),
            "unexpected error: {err}"
        );
    }

    #[test]
    fn error_context_json_wire_format_is_stable() {
        // Wire schema lock — the Java side keys on the exact field names
        // ("table_id", "rep"). A rename would silently break parsing.
        let jv = JsonVal::ErrorContext {
            table_id: 3,
            rep: 42,
        };
        let json = serde_json::to_string(&jv).unwrap();
        assert!(
            json.contains("\"ErrorContext\"")
                && json.contains("\"table_id\":3")
                && json.contains("\"rep\":42"),
            "wire format regression: {json}"
        );
        let back: JsonVal = serde_json::from_str(&json).unwrap();
        match back {
            JsonVal::ErrorContext { table_id, rep } => {
                assert_eq!(table_id, 3);
                assert_eq!(rep, 42);
            }
            other => panic!("expected ErrorContext, got {other:?}"),
        }
    }

    #[test]
    fn error_context_registry_starts_empty() {
        let reg = ErrorContextRegistry::default();
        assert_eq!(reg.next_id, 0);
        assert!(reg.entries.is_empty());
        assert!(reg.peek(0).is_none());
    }

    #[test]
    fn error_context_to_val_errors_on_unknown_id() {
        let mut reg = ResourceRegistry::default();
        let mut futs = FutureRegistry::default();
        let mut strs = StreamRegistry::default();
        let mut ecs = ErrorContextRegistry::default();
        let jv = JsonVal::ErrorContext {
            table_id: 88888,
            rep: 0,
        };
        let err = jv
            .to_val(&mut reg, &mut futs, &mut strs, &mut ecs)
            .unwrap_err()
            .to_string();
        assert!(
            err.contains("88888") && err.contains("unknown error-context id"),
            "unexpected error: {err}"
        );
    }

    #[test]
    fn base64_edge_cases() {
        assert_eq!(b64_encode(&[]), "");
        assert_eq!(b64_encode(&[0]), "AA==");
        assert_eq!(b64_encode(&[0, 1]), "AAE=");
        assert_eq!(b64_encode(&[0, 1, 2]), "AAEC");
        assert_eq!(b64_decode("AAECAw==").unwrap(), vec![0, 1, 2, 3]);
        // Reject non-multiple-of-4 lengths.
        assert!(b64_decode("AAA").is_err());
    }
}
