# Caller-Aware-Host-Function — working notes (r.1 in progress)

**Charter**: `f-webassembly4j-caller-aware-host-function-charter-2026-07-26`
**Branch**: `f-webassembly4j-caller-aware` (branched from `main` at `a553e37`)
**Type**: feature — engine-agnostic exposure of the wasmtime4j r.2 / r.2.b `Caller<T>` pattern
**Version**: 2.5.2-SNAPSHOT (unchanged; accumulates before release cut)

## Charter scope

Propagate the `Caller<T>` scoped-mutation pattern UP from wasmtime4j to the
webassembly4j api layer so any provider can offer caller-aware host functions
without forcing consumers to reach into wasmtime4j directly.

Downstream unblock: F-JIT-Loader-Java-Reference r.5.c (Fiji jit-loader
switches from `HostFunction` to `CallerAwareHostFunction<Void>` for
compile+instantiate+growTable inside the callback).

## Wasmtime4j surface reference (r.2 + r.2.b at wasmtime4j 47.0.2-1.5.1)

- `ai.tegmentum.wasmtime4j.func.Caller<T>` — 10 scoped methods
  (data, getMemory, getTable, getFunction, getGlobal, compileModule,
   growTable, setTableElement, growMemory, instantiate).
- `ai.tegmentum.wasmtime4j.func.HostFunction.multiValueWithCaller(impl)` /
  `singleValueWithCaller(impl)` / `voidFunctionWithCaller(impl)` — factories
  that wrap a caller-aware Java lambda into a `CallerAwareHostFunction<T>`
  which the JNI backend recognises and injects the live caller into.
- Delivery route: JNI dispatch pushes a `JniCaller<T>` onto
  `JniHostFunction.CALLER_CONTEXT` ThreadLocal, and
  `CallerAwareHostFunction.getCurrentCaller` retrieves it via ServiceLoader
  (`CallerContextProvider`). Consumer of the wrapped `HostFunction` never
  needs to hand-plumb the caller.

## Plan (single slice)

### r.1-1 — webassembly4j-api additions
- New: `Caller.java` (10 methods matching wasmtime4j subset)
- New: `CallerAwareHostFunction.java` (@FunctionalInterface, `Object[] execute(Caller<T>, Object...)`)
- New: `CallerAwareHostFunctionDefinition.java` (parallel to `HostFunctionDefinition`)
- Edit: `LinkingContext.java` — add `callerAwareHostFunctions()` default
- Edit: `DefaultLinkingContext.java` — field, accessor, builder overloads

### r.1-2 — wasmtime4j-provider bridge
- New: `WasmtimeCallerAdapter.java` — wraps native `Caller<T>` as api `Caller<T>`
- Delegates each method to underlying `WasmMemory/Table/Global/Function` adapters

### r.1-3 — WasmtimeModuleAdapter.instantiate extension
- Iterate `linkingContext.callerAwareHostFunctions()`
- For each, register via `linker.defineHostFunction(module, name, funcType,
  HostFunction.multiValueWithCaller((nativeCaller, wasmArgs) -> { wrap caller,
  convert args, invoke, convert results }))`

### r.1-4 — test
- WAT: exports memory + table + `run()` that calls `env.trigger_grow(1)`
- `CallerAwareHostFunction<Void>` grows the caller-visible table via `caller.growTable`
- Assert grow observable + use-after-return throws `IllegalStateException`

## Status

- 2026-07-26 r.1-0 STARTED: branch created, notes stub committed.
