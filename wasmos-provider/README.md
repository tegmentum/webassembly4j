# wasmos-provider

`webassembly4j` provider backed by the Rust
[`wasmos-runtime`](https://github.com/tegmentum/wasmos) library. Wraps
wasmos-runtime's `add_all_to_linker` + `caps::wasi_p2` surface via JNI
so Java consumers can host wasmos-shaped WASM components (typically
control-plane guests composed with an adapter) through
webassembly4j's normal `Engine` / `Component` / `ComponentInstance` API.

Selected via `EngineSelection.byId("wasmos")`; provider priority 90.

## What works

- Load a wasmos-composed component from bytes
- Instantiate with default caps or a `LinkingContext.wasiContext()`
  ("wasi_p2" cap granted through wasmos-runtime)
- Honor `ComponentConfig` fields: `maxMemoryBytes`, `epochDeadline`,
  `fuelLimit`, `maxTableElements`, `maxInstances`, `maxTables`,
  `maxMemories`
- Invoke exported functions — sync path (`ComponentInstance.invoke`)
  and async path (`WasmosAsyncExtension.invokeAsync` returning
  `CompletableFuture<Object>`) with best-effort cancellation
- Two-way marshalling for primitives, `String`, `List`, `Map`,
  `Set` (flags), `Optional`, tuples, `byte[]` (list<u8> fast path),
  and the typed carriers `WitResource`, `WitFuture`, `WitStream`,
  `WitErrorContext`
- Component serialize / deserialize for hot-reload
- Nested-interface function lookup (functions inside exported
  interfaces resolve via `<interface>#<function>` syntax)

## What is intentionally not supported

- Java-provided core-wasm host functions (`LinkingContext.hostFunctions`)
  — wasmos is WIT-first; use `wasmtime4j-provider` if you need to inject
  Java host functions into a core-wasm module
- `Engine.loadModule` (core-wasm loading) — wasmos-provider is
  component-only
- Host-side reading of `Val::Future` / `Val::Stream` — wasmtime 47's
  typed reader API doesn't support dynamic-payload polling. Parking,
  closing, and passing these values back into guests works; awaiting or
  iterating them host-side throws a documented "wasmtime API gap".
  When wasmtime upstream ships the dynamic reader, the stub JNI
  entries (`futureAwait`, `streamRead`) can be filled in without a
  Java surface change.
- `wasi:nn`, `wasi:http`, per-endpoint network egress — needs
  wasmtime-wasi upstream API surface

## Build setup — REQUIRES sibling repository checkouts

`wasmos-runtime` (this module's Rust dep) is declared via a RELATIVE
path (`../../../wasmos` from `wasmos-provider/native/Cargo.toml`),
which itself declares path deps on other tegmentum repositories that
must be co-checked-out at fixed relative paths. **Building this crate
from a fresh clone of `webassembly4j` alone WILL fail.** You need
four sibling checkouts:

```
~/git/webassembly4j        (this repo)
~/git/wasmos               (wasmos-provider/native/Cargo.toml points here via ../../../wasmos)
~/git/wasm-cm              (wasmos's sibling path dep, ../wasm-cm/...)
~/git/wasm-continuity      (wasmos's sibling path dep, ../wasm-continuity/...)
```

Once all four are checked out at the same parent (any parent — the
paths are all relative), `cd wasmos-provider && mvn install` from the
webassembly4j root works.

### Why not git-dep?

`wasmos-runtime = { git = "..." }` fails at build time because cargo
fetches wasmos from GitHub but then cannot resolve wasmos's own
`../wasm-cm/...` and `../wasm-continuity/...` path deps in the fetched
tree.

### Why not crates.io?

Neither `wasmos-runtime`, `wasm-cm`, nor `wasm-continuity` publishes
versioned crates yet. Publishing wasmos-provider to Maven Central is
blocked on this.

### Paths to unblock external / CI builds

1. wasmos + wasm-cm + wasm-continuity publish versioned crates.
2. wasmos restructures to git-dep (or workspace-internal) its
   siblings — no more `path = "../<sibling>/..."`.
3. Fork wasmos into a git-dep-clean tree.

The `wasmos-provider/native/Cargo.toml` comment tracks the same
information for cargo-only readers.

## Layout

```
wasmos-provider/
├── pom.xml                                        # exec-maven-plugin runs cargo
├── native/
│   ├── Cargo.toml                                 # path dep to ~/git/wasmos
│   └── src/lib.rs                                 # JNI extern "C" shims
└── src/main/java/ai/tegmentum/webassembly4j/provider/wasmos/
    ├── WasmosProvider.java                        # EngineProvider SPI impl
    ├── WasmosEngineAdapter.java
    ├── WasmosComponentAdapter.java
    ├── WasmosComponentInstanceAdapter.java
    ├── WasmosMarshalling.java                     # WIT-shape carriers + JSON codec
    ├── ext/
    │   ├── WasmosAsyncExtension.java              # invokeAsync + futures/streams
    │   └── WitErrorContextException.java
    └── jni/
        ├── WasmosNative.java                      # native method decls
        └── WasmosNativeLoader.java                # extracts dylib from jar
```

## Testing

`mvn test -pl wasmos-provider` runs the Java suite (62 tests) plus
the Rust unit tests (19). Includes an end-to-end test that loads
`~/git/wasmos/tests/e2e-fixtures/composed_wasmtime.wasm` — assumeTrue-
skipped if the fixture path doesn't exist. The wasmos-composed component
returns 42 from its `run` export.
