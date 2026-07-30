# counter-component.wasm — rebuild recipe

Compiled artifact lives at `../counter-component.wasm`. Rebuild only when
the WIT surface (`wit/counter.wit`) changes, or when a wasmtime bump
breaks binary compatibility of the current fixture (unlikely: the
component is a plain resource-exporting component with no host imports).

## Sources checked in

- `wit/counter.wit` — the WIT interface. A single `counter-api` interface
  exports a `counter` resource with constructor + two methods.
- `src/lib.rs` — the Rust guest that implements the resource. Uses
  `#![no_std]` + `wee_alloc` so the built component has zero WASI
  imports and comes in under 7 KiB.
- `Cargo.toml` — cargo-component metadata pinning the WIT world +
  `wit-bindgen-rt` version.

## Rebuild recipe

Toolchain: cargo-component ≥ 0.21, rustup target `wasm32-unknown-unknown`,
`wasm-tools` on `PATH` (for validation only).

```
cd wasmos-provider/src/test/resources/counter-fixture-src
cargo component build --release --target wasm32-unknown-unknown
cp target/wasm32-unknown-unknown/release/counter_fixture.wasm \
   ../counter-component.wasm
wasm-tools validate ../counter-component.wasm
wasm-tools component wit ../counter-component.wasm    # sanity check
```

## Why `wasm32-unknown-unknown` instead of `wasm32-wasip1`

Building for `wasm32-wasip1` (cargo-component's default) drags in libstd
and pulls WASI-p2 imports into the component. That still works via
wasmos's wasi-p2 linker capability, but a WASI-free component keeps the
test's failure modes narrower — an instantiation error must be about the
resource surface, not a missing WASI import.

## Interface path used from Java

The E2E test invokes the resource methods by their fully-qualified
component export path:

- `tegmentum:counter/counter-api@0.1.0#[constructor]counter`
- `tegmentum:counter/counter-api@0.1.0#[method]counter.increment`
- `tegmentum:counter/counter-api@0.1.0#[method]counter.get`

The Rust side (`wasmos-provider/native/src/lib.rs::resolve_func`) splits
on the first `#` to walk `Instance::get_export_index` twice — nested
interface exports aren't visible via `Instance::get_func(name)` alone.
