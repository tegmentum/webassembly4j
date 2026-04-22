# Changelog

## 1.2.2

### Dependency Upgrades

- wasmtime4j: 43.0.1-1.1.1 to 44.0.0-1.1.2

## 1.2.1

### Dependency Upgrades

- wasmtime4j: 43.0.0-1.1.1 to 43.0.1-1.1.1

## 1.2.0

### New Features

- **Component model support** in `webassembly4j-runtime`: `WasmRuntime.load()` detects component binaries and routes them to either a native component API (Path A, wasmtime) or a pure-Java component lowerer (Path B) that extracts the embedded core module for non-native providers (Chicory, WAMR, GraalWasm).
- **Pure Java component lowerer**: `ComponentLowerer` parses the WebAssembly component binary format in-process (LEB128 section walking), eliminating any shell-out to `wasm-tools` for the lowering step.
- **Chicory runtime compilation**: `ChicoryConfig` exposes an `ExecutionMode.COMPILE` option that AOT-compiles WASM modules to JVM bytecode at load time, delivering large throughput improvements over the interpreter (invokeSimpleAdd: ~116M ops/s vs ~12.6M ops/s interpreted on the benchmark suite).
- **`CHICORY_COMPILED` benchmark variant**: new JMH parameter covering the Chicory compiler across all benchmark classes, bringing the matrix to nine engine variants.
- **`ComponentInvocationHandler` / `ComponentProxyFactory`**: reflection proxy path for invoking component exports without Canonical ABI marshalling (native component API handles WIT type conversion).
- **`WasmBindingProvider` component hooks**: `supportsComponentBinding()` and `createFromComponent()` default methods let generated bindings opt in to the component path.

### Dependency Upgrades

- wasmtime4j: 43.0.0-1.1.0 to 43.0.0-1.1.1

### Bug Fixes

- `ChicoryProvider.supports(EngineConfig)` now correctly accepts `ChicoryConfig` instead of rejecting all engine configs, which was blocking the `CHICORY_COMPILED` benchmark variant from being selected via the SPI.

## 1.1.0

### New Features

- **Component Builder module** (`webassembly4j-component-builder`): Java-to-WASM component toolchain with Maven plugin and CLI support. Annotations (`@WitComponent`, `@WitExport`, `@WitImport`, `@WitRecord`, etc.), WIT scanner and emitter, GlueCodeGenerator, NativeImageCompiler, and WasmToolsLinker for the full compile pipeline.
- **Chicory GC extension**: `GcExtension` implementation for the Chicory provider using `WasmStruct`, `WasmArray`, and `WasmI31Ref`.
- **Chicory WASI support**: Wire `WasiPreview1` from chicory-wasi, mapping `WasiContext` args/env/dirs/stdio to `WasiOptions`.
- **Chicory threads and reference types**: Report `supportsThreads()` and `supportsReferenceTypes()` capabilities (available since Chicory 1.5.0+).
- **Wasmtime config**: Add `wasmExceptions` and `wasmFunctionReferences` options to `WasmtimeConfig` for loading WasmGC modules.
- **Explicit provider selection**: `WebAssembly.builder().provider("chicory")` to select a specific provider by ID.
- **GraalVM auto-download**: The component-builder Maven plugin auto-downloads the tegmentum/graal fork distribution on first use, cached in `~/.webassembly4j/graalvm/`.
- **Java-to-WASM round-trip test**: End-to-end test compiling Java with `@WasmExport` to WasmGC via GraalVM, then loading and executing with wasmtime provider.

### Dependency Upgrades

- Chicory: 1.0.0 to 1.7.3
- wasmtime4j: 42.0.1-1.0.0 to 43.0.0-1.1.0
- wamr4j: 1.0.0-SNAPSHOT to 2.4.4-1.0.2

### Bug Fixes

- Correct provider priority values in README (Wasmtime is 200, not 100)
- Move native JNI test dependencies to profile for release compatibility
- Use `x86_64` in platform name for macOS Intel GraalVM downloads

## 1.0.0

Initial release.

### Modules

- `webassembly4j-api` -- Stable user-facing API (Multi-Release JAR: 8/11/22)
- `webassembly4j-spi` -- Provider contracts and ServiceLoader discovery
- `webassembly4j-runtime` -- High-level runtime with proxy binding, marshalling, and `WasmRuntime` facade
- `webassembly4j-bindgen` -- WIT binding generator (Maven plugin + CLI)
- `webassembly4j-testing` -- JUnit 5 multi-engine test support
- `webassembly4j-pool` -- Thread-safe instance pooling
- `webassembly4j-spring` -- Spring Boot auto-configuration
- `webassembly4j-benchmarks` -- JMH benchmarks across all engines

### Providers

- `wasmtime4j-provider` -- Wasmtime via wasmtime4j (priority 200)
- `graalwasm4j-provider` -- GraalWasm via Polyglot API (priority 150)
- `wamr4j-provider` -- WAMR via wamr4j (priority 100)
- `chicory4j-provider` -- Chicory pure-Java runtime (priority 50)

### Features

- Unified API for core module loading, instantiation, function invocation, memory, globals, and tables
- Host function linking via `LinkingContext`
- WasmGC object bridge with `@GcMapped` annotation and `GcProxyFactory`
- Typed function wrappers to avoid boxing overhead
- Module introspection (exports/imports)
- `WasmRuntime` static facade: `load()`, `call()`, `compile()`, `builder()`
- `ProxyFactory` for binding Java interfaces to WASM exports
- Canonical ABI marshalling (strings, bytes, records)
- `WasmBindingProvider` SPI for generated bindings

### Performance

- Single-pass provider selection and coalesced memory reads
- Fast-path host function dispatch for 0-2 parameter callbacks
- Fast-path dispatch in `ChicoryFunctionAdapter` covering 11 common signatures
- Cached `ServiceLoader` discovery for bootstrap and provider registry
- Per-call array reuse and binding provider caching
- Eliminated per-call allocations in marshalling hot path
