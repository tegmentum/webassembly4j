# F-Webassembly4j-Table-Interface-Completion — Working Notes

Charter: `~/git/fijivm/doctrine/specs/f-webassembly4j-table-interface-completion-charter-2026-07-26.md`

Date opened: 2026-07-26
Branch: `f-table-interface-completion`

## Scope (r.1)

Add 4 spec-standard operations to `ai.tegmentum.webassembly4j.api.Table` and
implement them in `wasmtime4j-provider`. Other provider adapters are left
unchanged — the new interface methods carry `throw UnsupportedOperationException`
defaults so downstream `wamr4j-provider`, `graalwasm4j-provider`, `wasm3-provider`,
and `endive4j-provider` continue to compile.

| Method | Spec op | Native site (`ai.tegmentum.wasmtime4j.WasmTable`) |
|---|---|---|
| `OptionalInt maxSize()` | `table.type().max` | `int getMaxSize()` — returns `-1` when unlimited |
| `Object get(int)` | `table.get` | `Object get(int)` |
| `void set(int, Object)` | `table.set` | `void set(int, Object)` |
| `int grow(int, Object)` | `table.grow` | `int grow(int, Object)` |

## Native convention discoveries

`ai.tegmentum.wasmtime4j.WasmTable.getMaxSize()` Javadoc (verified in
`~/git/wasmtime4j/wasmtime4j/src/main/java/ai/tegmentum/wasmtime4j/WasmTable.java:79`):

> "Gets the maximum size of the table. @return the maximum number of
> elements, or -1 if unlimited"

The adapter therefore treats `max < 0` as unbounded.

## Explicitly out of scope for r.1

`fill`, `copy`, `init`, `dropElementSegment`, `growAsync`, `elementType()`,
`getSize64/get64/set64/…` — deferred per charter.

## Slice plan

- r.1-1: extend `Table` interface (defaults throw UOE)
- r.1-2: implement in `WasmtimeTableAdapter`
- r.1-3: add `WasmtimeTableAdapterTest`
- r.1-4: `mvn -B -pl webassembly4j-api,wasmtime4j-provider -am test`
- r.1-θ: close commit

## Guardrails followed

- No pom.xml version bump
- No CHANGELOG.md edit (deferred to release cut)
- No touch to other provider modules, spi, runtime, testing, pool, spring,
  benchmarks, bindgen, component-builder, embedder-generated
- No touch to `LinkingContext.java`, `DefaultLinkingContext.java`,
  `WasmtimeModuleAdapter.java` (Charter B territory)
- No push
