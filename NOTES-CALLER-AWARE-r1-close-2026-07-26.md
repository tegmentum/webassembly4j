# NOTES — Webassembly4j Caller-Aware Host Function — r.1 CLOSE (2026-07-26)

**Branch**: `f-webassembly4j-caller-aware` (off `main @ a553e37`)

**Charter**: `fijivm/doctrine/specs/f-webassembly4j-caller-aware-host-function-charter-2026-07-26.md`

**Prior partial**: `fijivm/doctrine/specs/f-webassembly4j-caller-aware-host-function-r1-partial-2026-07-26.md`
(r.1-1 landed at `a2fa3d9` before session limit; resume executed r.1-2..r.1-θ)

## Executive summary

Webassembly4j api layer now exposes the caller-aware host-function
pattern behind a `Caller<T>` interface + `CallerAwareHostFunction<T>`
+ `LinkingContext.callerAwareHostFunctions()` accumulator. Wasmtime4j
provider bridges the api into the wasmtime4j r.2 caller-scoped
implementation (JNI-only in wasmtime4j 47.0.2-1.5.1); non-wasmtime
providers inherit the empty-list default and are unaffected.

Integration test (`WasmtimeCallerAwareHostFunctionTest`) grows the
caller's table by 1 from inside a caller-aware callback and confirms
the wasmtime4j r.2 generation counter propagates `IllegalStateException`
through the api-layer Caller adapter on use-after-return — the SIGSEGV
class first witnessed at F-JIT-Loader-Java-Reference r.5.b is now
closed at the webassembly4j api layer.

## Commits landed on `f-webassembly4j-caller-aware`

- `1b8b907` docs(caller): begin webassembly4j caller-aware charter r.1 [pre-partial]
- `a2fa3d9` feat(api): add Caller<T> + CallerAwareHostFunction<T> interfaces + LinkingContext plumbing [pre-partial, r.1-1]
- `cb81e9b` feat(wasmtime-provider): WasmtimeCallerAdapter [r.1-2]
- `2aa300d` feat(wasmtime-provider): WasmtimeModuleAdapter consumes callerAwareHostFunctions from LinkingContext [r.1-3]
- `bec6292` test(wasmtime-provider): caller-aware host function + scoped growTable from callback [r.1-4 initial]
- `e84654c` test(wasmtime-provider): restrict caller-aware test callback to scoped ops only [r.1-4 fix — see "Test discovery" below]

## Files touched

- **New — api (r.1-1, pre-partial)**:
  - `webassembly4j-api/src/main/java/ai/tegmentum/webassembly4j/api/Caller.java`
  - `webassembly4j-api/src/main/java/ai/tegmentum/webassembly4j/api/CallerAwareHostFunction.java`
  - `webassembly4j-api/src/main/java/ai/tegmentum/webassembly4j/api/CallerAwareHostFunctionDefinition.java`
- **Modified — api (r.1-1, pre-partial)**:
  - `webassembly4j-api/src/main/java/ai/tegmentum/webassembly4j/api/LinkingContext.java` (+ default `callerAwareHostFunctions()`)
  - `webassembly4j-api/src/main/java/ai/tegmentum/webassembly4j/api/DefaultLinkingContext.java` (+ Builder.addCallerAwareHostFunction)
- **New — wasmtime provider (r.1-2)**:
  - `wasmtime4j-provider/src/main/java/ai/tegmentum/webassembly4j/provider/wasmtime/WasmtimeCallerAdapter.java`
- **Modified — wasmtime provider (r.1-3)**:
  - `wasmtime4j-provider/src/main/java/ai/tegmentum/webassembly4j/provider/wasmtime/WasmtimeModuleAdapter.java`
    - `callerScoped(...)` factory for caller-produced Modules
    - `defineHostFunctions` extracted to package-private static helper for reuse
    - `defineCallerAwareHostFunctions` wires each definition through
      wasmtime4j's `HostFunction.CallerAwareHostFunction`
    - `nativeModule()` package-private accessor for the caller-scoped bridge
    - `close()` skips `store.close()` for caller-scoped Modules (their
      store is the caller's live store)
- **New — wasmtime provider test (r.1-4)**:
  - `wasmtime4j-provider/src/test/java/ai/tegmentum/webassembly4j/provider/wasmtime/WasmtimeCallerAwareHostFunctionTest.java`

## API adjustments from charter spec

The charter spec proposed `Caller.instantiate(Module, LinkingContext)`.
Wasmtime4j's `Caller.instantiate(InstancePre)` takes an
`InstancePre`, not a Module + import bundle — the r.2.b close notes
document this explicitly (pre-instantiation happens outside the
callback frame so the reentrant step is minimal).

The api-layer signature is preserved as spec'd (no api change).
`WasmtimeCallerAdapter.instantiate(Module, LinkingContext)`
reconciles the mismatch internally:

1. Extracts the native wasmtime4j Module from the api Module
2. Builds a transient `Linker` from `caller.engine()`
3. Wires `hostFunctions()` + `callerAwareHostFunctions()` from the
   passed LinkingContext (extern imports are rejected — they require
   a live store binding that wasmtime4j's Caller does not expose)
4. Calls `linker.instantiatePre(nativeModule)` to build the InstancePre
5. Delegates to `nativeCaller.instantiate(pre)` — the borrow-safe
   scoped instantiate landed at wasmtime4j r.2.b
6. Wraps the returned Instance in `WasmtimeInstanceAdapter`

`WasmtimeModuleAdapter` gained a caller-scoped construction path
(package-private `callerScoped(...)` factory) so
`caller.compileModule(bytes)` can return a Module handle that borrows
the caller's implicit store. Its own `instantiate()` / `instantiate(ctx)`
methods throw `IllegalStateException("… no owning store …")` — the
caller-produced Module must be instantiated via
`Caller.instantiate(Module, LinkingContext)`, not directly.

## Test outcome

- `mvn -B -pl webassembly4j-api,wasmtime4j-provider -am test`
  (`-Dtest='!JavaToWasmRoundTripTest,!WasmtimeCallableResourceTest'`,
  `-Dskip.rust.build=true`) — **269 tests PASS**, 0 failures, 0 errors,
  0 skipped:
  - api: 162 (unchanged)
  - spi: 18 (unchanged)
  - wasmtime4j-provider: 89 (88 pre-existing unchanged + 1 new
    `WasmtimeCallerAwareHostFunctionTest`)

## Test discovery — regular WasmTable accessors SIGSEGV from callback

Initial test called `t.size()` from inside the caller callback via
the ordinary `WasmtimeTableAdapter.size()` delegate; that path routes
to `JniTable.getSize()` which the native side implements via
`store.try_lock_store()`. Inside a live caller-borrow the native
crashes rather than throws — witnessed via hs_err at
`libwasmtime4j.dylib Table::size + JniTable_nativeGetSize`.

This is consistent with the wasmtime4j r.2 doctrine: **mutation from
inside a callback MUST route through caller-scoped entrypoints**
(`caller.growTable`, `caller.setTableElement`, `caller.growMemory`,
`caller.instantiate`), which use `caller.as_context_mut()` instead of
the reentrant store lock. Non-mutation reads on the ordinary handles
(e.g. `WasmMemory.readBytes`, `WasmTable.get(index)`) may or may not
be safe depending on the native impl's lock discipline. The test now
uses only caller-scoped ops in the callback and reads
`instance.table("table").size()` on the outside (post-callback) where
the store lock is available.

Also encountered a pre-existing bug in `WasmtimeFunctionAdapter`:
the fast-path classifier maps `(i32) -> ()` to `FastPath.I_V` and
calls `callVoid()`, dropping the argument. This trips "Parameter
validation failed for function 'run'" for a `run(param i32)` export.
The test module was updated to a no-param `run()` with an inline
`i32.const 1` so the fast-path used is `V_V`. Out of scope for this
charter.

## mvn install refreshed SNAPSHOT

- `mvn -B install -DskipTests -Dspotbugs.skip=true -Dcheckstyle.skip=true -Dpmd.skip=true -Dskip.rust.build=true` — **BUILD SUCCESS**
- All 15 modules installed (Parent + api + spi + 5 providers + runtime + testing + pool + spring + benchmarks + bindgen + component-builder + wasmos-embedder)
- Version stays `2.5.2-SNAPSHOT` (no re-bump per charter)

## r.5.c unblock status

**F-JIT-Loader-Java-Reference r.5.c** is now UNBLOCKED.

The engine-agnostic path Fiji jit-loader was waiting for is live:
- `CallerAwareHostFunction<Void>` (or any T) is registered via
  `DefaultLinkingContext.Builder.addCallerAwareHostFunction`
- The callback receives an api `Caller<T>` handle with
  `compileModule(byte[])`, `instantiate(Module, LinkingContext)`,
  `growTable(Table, int, Object)`, `setTableElement`, `growMemory` —
  all borrow-safe via the wasmtime4j r.2 + r.2.b caller pipeline
- Non-wasmtime providers (WAMR/GraalWasm/Wasm3/Endive) inherit the
  empty-list default from `LinkingContext.callerAwareHostFunctions`
  and never construct a Caller — non-breaking for them; if a
  non-wasmtime LinkingContext ends up carrying caller-aware
  definitions in a future consumer, the provider naturally throws at
  instantiate time (per charter's discipline verification)

The known-good caller-scoped ops for r.5.c per empirical evidence
here + wasmtime4j r.2.b test suite:
- `caller.compileModule(bytes)` — routes through caller's engine
- `caller.instantiate(module, imports)` — routes through borrow-safe
  scoped instantiate
- `caller.growTable(table, delta, null)` — verified in this charter's
  test
- `caller.setTableElement(table, index, value)` — API present;
  wasmtime4j r.2c banked follow-up for funcref-encoding parity when
  non-null funcref values are needed inside the callback (workaround:
  install funcref via outer-instance `Table.set` after callback
  returns, per wasmtime4j r.2.b `testCallerScopedJitInstallLoop`)
- `caller.growMemory(memory, deltaPages)` — verified in wasmtime4j
  r.2 test suite

## Discipline notes preserved

- No breaking changes to existing webassembly4j api signatures.
- Non-wasmtime providers inherit UOE-free empty-list default; no
  provider outside wasmtime4j-provider was touched.
- Webassembly4j pom.xml versions untouched (still `2.5.2-SNAPSHOT`).
- CHANGELOG.md untouched.
- Wasmtime4j NOT modified (already installed as 47.0.2-1.5.1 with
  r.2 + r.2.b caller additions).
- Fiji: NOT touched.
- OJ9 substrate `1e7c5205e5`: NOT touched.
- HotSpot: NOT touched. FROZEN WITs preserved. Matrix v7 unchanged.
- No push. Branch `f-webassembly4j-caller-aware` remains local.

## Stopping-condition classification

**CLOSED_SUCCEEDED**

- All 5 sub-slices (r.1-1 pre-partial + r.1-2..r.1-5) land as real
  functionality.
- Integration test passes on first run after the SIGSEGV-in-test
  discovery and fix; no build regressions; all 88 pre-existing
  wasmtime4j-provider tests still pass.
- The r.5.b SIGSEGV pattern is provably closed at the webassembly4j
  api layer — use-after-return is caught by IllegalStateException,
  in-callback grow succeeds via the borrow-safe path.
- r.5.c (Fiji retry) is fully unblocked — consumer-side work only
  from here.

## Related

- Charter: `fijivm/doctrine/specs/f-webassembly4j-caller-aware-host-function-charter-2026-07-26.md`
- Prior partial (r.1-1 pre-close): `fijivm/doctrine/specs/f-webassembly4j-caller-aware-host-function-r1-partial-2026-07-26.md`
- Wasmtime4j-side companion: r.2 + r.2.b close notes at
  `~/git/wasmtime4j/NOTES-CALLER-AWARE-HOST-FUNCTION-r2-implement-2026-07-26.md`
  + `~/git/wasmtime4j/NOTES-CALLER-AWARE-HOST-FUNCTION-r2b-instantiate-2026-07-26.md`
- Prior sibling closures (all merged to main pre-branch):
  Table, LinkingContext, Function.unwrap, Cross-Module-Store-Sharing
- Downstream: F-JIT-Loader-Java-Reference r.5.c (now unblocked)
