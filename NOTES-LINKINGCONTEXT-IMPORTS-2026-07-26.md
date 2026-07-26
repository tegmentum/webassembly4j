# LinkingContext-Imports-Wiring-Fix — working notes (r.1 in progress)

**Charter**: `f-webassembly4j-linkingcontext-imports-wiring-fix-charter-2026-07-26`
**Branch**: `f-linkingcontext-imports-wiring-fix` (branched from `main` at `fd07215`)
**Type**: bug fix — closes semantic hole in existing public API

## Problem

- `DefaultLinkingContext.Builder.addImport(String, Object)` is a public method
- `DefaultLinkingContext.imports()` returns the resulting map
- `LinkingContext` interface never exposes it
- Providers cannot reach the map → the call has no effect
- Also: single-string key can't represent two-part `(moduleName, name)` Wasm import keys

## Approach — Option B.2 (typed variants)

1. Add sealed `ExternImportDefinition` in api layer with variants
   `MemoryImport / TableImport / GlobalImport / FunctionImport`
2. Add `LinkingContext.externImports()` default method
3. Add typed builder methods on `DefaultLinkingContext.Builder`
4. Deprecate untyped `addImport(String, Object)` (kept for backwards compat)
5. Wire `WasmtimeModuleAdapter.instantiate(LinkingContext)` to consume them

## Empirical unwrap availability audit

- `Memory.unwrap(Class<T>)`   — YES
- `Table.unwrap(Class<T>)`    — YES
- `Global.unwrap(Class<T>)`   — YES
- `Function.unwrap(Class<T>)` — NO (interface lacks it)

`WasmtimeMemoryAdapter`, `WasmtimeTableAdapter`, `WasmtimeGlobalAdapter` all
implement `unwrap` and hand back the native `WasmMemory` / `WasmTable` /
`WasmGlobal` handles that `Linker.defineMemory/defineTable/defineGlobal`
require.

## Scope decision (r.1)

- MemoryImport / TableImport / GlobalImport — fully wired
- FunctionImport — sealed-type variant lands; wasmtime provider throws
  `UnsupportedOperationException` at instantiate. Consuming requires adding
  `Function.unwrap(Class<T>)` on the api interface + adapter impl — deferred
  to a follow-up charter.

## Charter A coordination

Charter A (table interface completion) is on branch `f-table-interface-completion`
and touches `Table.java` + `WasmtimeTableAdapter.java`. Charter B (this) is
branched from `main` (identical HEAD to Charter A start) and touches disjoint
files. No file overlap.
