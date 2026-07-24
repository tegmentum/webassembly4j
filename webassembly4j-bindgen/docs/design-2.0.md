# webassembly4j-bindgen 2.0 — SPI-dispatching resource bodies

- Status: **Landed**
- Date: 2026-07-23
- Depends on: bindgen 1.x (resource shape emission, world hoisting)
- Consumes: [ADR-005](../../wasm-cm/docs/decisions/ADR-005-webassembly4j-spi.md),
  [ADR-006](../../wasm-cm/docs/decisions/ADR-006-wasmos-embedder-wit-surface.md)

## Problem

bindgen 1.x emitted the SHAPE half of resource-typed Java bindings —
class declarations, `long handle` fields, `AutoCloseable`, method
signatures, `create(...)` factories for WIT constructors — but every
method body was `throw new UnsupportedOperationException(...)`.

Under ADR-006 the intent is that every embedder writes exactly one
`wasmos_start` native call and consumes generated bindings for
everything else. With bindings whose bodies throw, an embedder has to
hand-write a bridge from the generated surface to a real runtime
(`WasmosEmbedderConnector` under wasi-p2-rs: 392 LOC). That bridge
subclassed the generated resources, cached its own handles, mapped
error codes by hand, and routed callbacks — everything the ADR says
should NOT exist per-embedder.

## Decision

Add an opt-in **runtime-provider SPI** mode to the generator. When
enabled, resource method bodies dispatch through a generated SPI
interface instead of throwing. Each embedder writes one implementation
of that SPI (once) and installs it into a generated registry at
startup; the hand-written per-method bridge disappears.

### Configuration

- New `BindgenConfig` field: `runtimeProviderName` (String, nullable).
  When null: legacy behavior (bodies throw). When set to `"Foo"`: SPI
  mode emits `Foo` + `FooRegistry` and rewrites resource bodies to
  dispatch through `FooRegistry.runtime()`.
- New CLI option: `--runtime-provider NAME`.
- Deterministic: `Foo` and `FooRegistry` are emitted with a fixed
  method ordering derived from WIT declaration order. Same input WIT +
  same `runtimeProviderName` → byte-identical output.

### Generated surface

For every resource `X` with methods `m1`, `m2`, ... and the intrinsic
`close`, the SPI interface gets:

- If `X` has a WIT constructor: `X <xLower>Create(<ctor params>)` —
  returns the fully-constructed resource; the impl chooses how to mint
  the handle.
- For each instance method `m(P) -> R`: `R <xLower><M>(long handle, P)`.
- For each static method `m(P) -> R`: `R <xLower><M>(P)`.
- Intrinsic `close`: `void <xLower>Close(long handle)`.

Where `<xLower>` is the resource name in camelCase and `<M>` is the
method name in PascalCase — the concatenation is unambiguous and
avoids collisions across resources that share a method name.

The generated body for an instance method becomes:

```java
public WitResult<List<Byte>, Error> callExport(String name, List<Byte> args) {
  return EmbedderRuntimeRegistry.runtime()
      .runtimeInstanceCallExport(this.handle, name, args);
}
```

The static-factory body for a WIT constructor:

```java
public static HostProvider create(String interfaceName, int numFuncs) {
  return EmbedderRuntimeRegistry.runtime()
      .hostProviderCreate(interfaceName, numFuncs);
}
```

Note that constructor factories return the resource **type** (not a
`long`), so the SPI impl is the sole locus that knows how to wrap a
runtime-issued handle into the generated class. This keeps the
`long handle`-holding constructor `protected` — no external caller
should ever construct a resource with an unbounded raw handle.

### Registry

`<Name>Registry` is a tiny final class with:

- `install(<Name> provider)` — install the SPI impl; overwrite is
  allowed for test setups.
- `runtime()` — return the installed impl or throw `IllegalStateException`
  with an actionable message.
- `uninstall()` — for test cleanup.

The registry uses a `volatile` field, not a full `ThreadLocal` or per-
`RuntimeInstance` provider. Rationale: an embedder installs one
provider for the lifetime of a JVM; testing multiple providers
side-by-side would require the registry to grow (out of scope for
2.0). If that need lands, extending the registry with a thread-local
override is additive.

### Callback binding

`EmbedderCallbacks` stays as it was — an interface the embedder
implements. The connector's constructor threads the callback impl into
the SPI provider impl; no generator support is needed. This keeps the
generator free of any "how to wire the imported interface" strategy
choice, which is deliberately embedder-specific (e.g. Endive uses
`LinkingContext`; wasmtime4j uses `Linker`; graalwasm4j uses
`ImportObject`).

## Consequences

**On the generator:**

- One new config field, one new CLI flag.
- One new file (`RuntimeProviderCodeGenerator`) that emits the two SPI
  classes.
- One targeted change to `ModernCodeGenerator.generateResource` /
  `generateResourceMethod` — bodies now branch on
  `config.getRuntimeProviderName()`. Legacy mode still emits the
  throwing bodies unchanged.
- No changes to type mapping, WIT parsing, world hoisting, or
  determinism guarantees.

**On the wasmos-embedder-generated module:**

- Regenerated with `--runtime-provider EmbedderRuntime`. New files:
  `EmbedderRuntime.java`, `EmbedderRuntimeRegistry.java`.
- `RuntimeInstance` / `HostProvider` bodies now dispatch (no throws).

**On WasmosEmbedderConnector (wasi-p2-rs):**

- Drops from 392 LOC to ~180 LOC. The old subclasses
  (`RuntimeInstanceHandle`, `HostProviderHandle`) delete outright — no
  override needed once dispatch flows through the registry. The
  connector becomes a thin bootstrap wrapper: load the guest bytes,
  install the SPI impl, expose convenience `byte[]` variants of the
  `List<Byte>`-taking generated calls.

**What 2.0 does not do (deferred):**

- The `introspect` / `verify-world` SPI methods dispatch through the
  registry, but the wasm-cm runtime-guest does not yet expose an
  export that would service them from an instance handle. The provider
  impl throws `UnsupportedOperationException` for those two, with a
  precise message. Bindgen has done its job; the shortfall is at the
  runtime-guest surface, tracked as a follow-on in ADR-005/ADR-006.
- Own/borrow lifecycle beyond `close()` — resources today are
  reference-counted at the runtime-guest level; the generated Java
  surface doesn't distinguish `own<T>` from `borrow<T>` in method
  signatures (they compile to the same generated Java type). A future
  bump can add the distinction if a WIT world exercises it.
- Multiple providers per JVM — the registry is single-slot volatile.
  Additive change if needed.

## Alternatives considered

**Emit resource bodies that delegate to a `Function`/lambda field.** A
per-instance dispatcher instead of a static registry. Rejected: it
forces the SPI impl to construct every resource with a delegate,
undoing the "just wrap a handle" shape of the generated ctor.

**Generate a full-fat abstract class the embedder subclasses.** In
this model the resource IS abstract; the concrete class the SPI impl
extends. Rejected: the sandwich test's public API is `RuntimeInstance`
(the generated class). If it were abstract, every call site would need
the concrete subclass in its imports — a subtler leak than "call the
registry once at startup."

**Emit dispatch bodies unconditionally (no config flag).** Rejected:
consumers who want the SHAPE half only (introspection tools,
type-only tests) would be forced to write a dummy provider.
Backward compatibility with 1.x consumers matters — the throwing
bodies are documented, load-bearing behavior for the shape tests that
already exist.
