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

## Verification (r.1-4)

Command (JavaToWasmRoundTripTest + WasmtimeCallableResourceTest excluded —
the former recompiles a 7.8 MB WasmGC calculator per test and stalls in
JniEngine.nativeCompileModule for 8+ min per iteration; the latter needs
a Rust-toolchain-built counter_component.wasm which is skipped when
`-Dskip.rust.build=true`; neither test touches Table code paths):

```
mvn -B -pl webassembly4j-api,wasmtime4j-provider -am \
    -Dskip.rust.build=true \
    -Dtest='!JavaToWasmRoundTripTest,!WasmtimeCallableResourceTest' \
    -DfailIfNoTests=false test
```

Result: BUILD SUCCESS.

- webassembly4j-api: 162 tests, 0 failures
- webassembly4j-spi: 18 tests, 0 failures (transitive dep)
- wasmtime4j-provider: 78 tests, 0 failures
  - WasmtimeTableAdapterTest: 9 tests, 0 failures (all new)

Log: `/tmp/charter-a-mvn-test.log`.

## Native-convention discoveries (fed back to charter)

- `ai.tegmentum.wasmtime4j.WasmTable#getMaxSize()` returns `-1` when the
  table is unbounded (Javadoc-documented at
  `~/git/wasmtime4j/wasmtime4j/src/main/java/ai/tegmentum/wasmtime4j/WasmTable.java:79`
  and mirrored in the JNI implementation
  `~/git/wasmtime4j/wasmtime4j-jni/src/main/java/ai/tegmentum/wasmtime4j/jni/JniTable.java:118`).
  Adapter treats any negative return as unbounded.
- `JniTable#get`/`JniTable#set` do NOT throw `IndexOutOfBoundsException`
  for out-of-bounds indices; they wrap the underlying
  `WasmRuntimeException` in a bare `RuntimeException` (see
  `JniTable.java:241-246,266-277`). The adapter therefore bounds-checks
  the index against `getSize()` before delegation and raises
  `IndexOutOfBoundsException` itself to honour the api-layer contract.
- `JniTable#get`/`JniTable#set` convert a negative index into
  `IllegalArgumentException` via `Validation.requireNonNegative`. Same
  precheck normalizes this to `IndexOutOfBoundsException`.

## Follow-ups (candidate Charter A r.2)

- Release cut. Charter suggests bumping webassembly4j to 2.5.2 (patch)
  since this is additive-with-defaults; or 2.6.0 if bundled with
  Charter B. Version bump + CHANGELOG update explicitly deferred per
  operator invariants.
- `wamr4j-provider` / `graalwasm4j-provider` / `wasm3-provider` /
  `endive4j-provider` remain compile-clean via the UOE defaults but
  do not yet implement the new operations. If a downstream consumer
  needs Table dynamism on those providers, opening focused adapter
  arcs is straightforward — the wasmtime adapter is 30 lines.
- The api-layer `Function` interface does not expose `unwrap`. The
  new test compensates by reaching the native `WasmFunction` through
  the native `Instance` (also unwrap). If future work adds
  `Function.unwrap`, `WasmtimeFunctionAdapter` should also implement it.
