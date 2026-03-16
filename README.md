# WebAssembly4J

A unified Java API for executing WebAssembly across multiple runtimes.

Write your code once against a stable API and swap runtimes without changing application code. WebAssembly4J supports [Wasmtime](https://wasmtime.dev/), [WAMR](https://bytecodealliance.github.io/wamr.dev/), [GraalWasm](https://www.graalvm.org/latest/reference-manual/wasm/), and [Chicory](https://github.com/nicholasgasior/chicory) out of the box.

## Quick Start

Add the API and a provider to your project:

```xml
<dependency>
    <groupId>ai.tegmentum.webassembly4j</groupId>
    <artifactId>webassembly4j-runtime</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
<dependency>
    <groupId>ai.tegmentum.webassembly4j</groupId>
    <artifactId>wasmtime4j-provider</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

Run a WebAssembly function in three lines:

```java
byte[] wasm = Files.readAllBytes(Path.of("add.wasm"));
int result = WasmRuntime.call(wasm, "add", Integer.class, 2, 3);
// result == 5
```

## Core API

The low-level API gives you full control over the engine lifecycle:

```java
try (Engine engine = WebAssembly.newEngine()) {
    Module module = engine.loadModule(Path.of("math.wasm"));
    Instance instance = module.instantiate();

    Function add = instance.function("add").orElseThrow();
    Object result = add.invoke(2, 3);
}
```

### Typed Functions

Avoid boxing overhead with typed function wrappers:

```java
Function fn = instance.function("add").orElseThrow();
TypedFunction.I32_I32_I32 add = fn.typed(TypedFunction.I32_I32_I32.class);
int result = add.call(2, 3);
```

### Module Introspection

Inspect a module's exports and imports before instantiation:

```java
Module module = engine.loadModule(wasmBytes);
for (ExportDescriptor export : module.exports()) {
    System.out.println(export.name() + " : " + export.type());
}
```

### Host Functions

Provide host functions to a WebAssembly module via `LinkingContext`:

```java
LinkingContext ctx = LinkingContext.builder()
    .define("env", "log", new ValueType[]{ValueType.I32}, new ValueType[]{},
        args -> { System.out.println("wasm says: " + args[0]); return null; })
    .build();
Instance instance = module.instantiate(ctx);
```

## High-Level Runtime

`WasmRuntime` provides a static facade for common operations:

```java
// One-shot call
int result = WasmRuntime.call(wasmBytes, "factorial", Integer.class, 10);

// Interface proxy -- bind a Java interface to WASM exports
try (Calculator calc = WasmRuntime.load(Calculator.class, wasmBytes)) {
    int sum = calc.add(2, 3);
}

// Pre-compile for reuse
WasmModule compiled = WasmRuntime.compile(wasmBytes);
try (WasmInstance inst = compiled.instantiate()) {
    inst.call("run");
}
```

## Modules

| Module | Description | Java |
|--------|-------------|------|
| `webassembly4j-api` | Stable user-facing API (Multi-Release JAR: 8/11/22) | 8+ |
| `webassembly4j-spi` | Provider contracts and discovery | 11+ |
| `webassembly4j-runtime` | High-level runtime with proxy binding and marshalling | 11+ |
| `webassembly4j-bindgen` | WIT binding generator (Maven plugin + CLI) | 11+ |
| `webassembly4j-testing` | JUnit 5 multi-engine test support | 11+ |
| `webassembly4j-pool` | Thread-safe instance pooling | 11+ |
| `webassembly4j-spring` | Spring Boot auto-configuration | 17+ |
| `webassembly4j-benchmarks` | JMH benchmarks across all engines | 17+ |

## Providers

| Provider | Engine | Java | Priority |
|----------|--------|------|----------|
| `wasmtime4j-provider` | Wasmtime (via wasmtime4j) | 11+ | 200 |
| `graalwasm4j-provider` | GraalWasm (Polyglot API) | 17+ | 150 |
| `wamr4j-provider` | WAMR (via wamr4j) | 17+ | 100 |
| `chicory4j-provider` | Chicory (pure Java) | 11+ | 50 |

Providers are discovered automatically via `ServiceLoader`. When multiple providers are on the classpath, the one with the highest priority is selected. To select a specific provider, use the builder:

```java
Engine engine = WebAssembly.builder()
    .provider("chicory")  // "wasmtime", "wamr", "graalwasm", or "chicory"
    .build();
```

## WasmGC Object Bridge

For languages that compile to WasmGC (Kotlin/Wasm, Dart, OCaml, Java via J2Wasm), the GC bridge provides automatic marshalling between Java objects and WebAssembly GC struct instances -- no linear memory management needed.

### Defining Mappable Types

Annotate a Java class or record with `@GcMapped`:

```java
@GcMapped
public record Point(double x, double y) {}

@GcMapped
public record Line(Point start, Point end) {}
```

Supported field types: `int`, `long`, `float`, `double`, `boolean`, and nested `@GcMapped` types.

### Marshalling Objects

```java
GcExtension gc = instance.extension(GcExtension.class)
    .orElseThrow(() -> new UnsupportedOperationException("GC not supported"));
GcMarshaller marshaller = GcMarshaller.forExtension(gc);

// Java -> GC struct
Point p = new Point(3.0, 4.0);
GcStructInstance struct = marshaller.marshal(p);

// GC struct -> Java
Point result = marshaller.unmarshal(struct, Point.class);
```

Nested `@GcMapped` fields are recursively marshalled as GC struct references. Null references are preserved in both directions.

### GC Interface Proxy

`GcProxyFactory` creates interface proxies that automatically marshal `@GcMapped` parameters and return values through GC structs:

```java
interface Geometry extends AutoCloseable {
    @WasmExport("rotate_point")
    Point rotate(Point p, double angle);

    @WasmExport("distance")
    double distance(Point a, Point b);
}

GcExtension gc = instance.extension(GcExtension.class).orElseThrow();
Geometry geom = GcProxyFactory.create(
    Geometry.class, engine, module, instance, gc);

Point rotated = geom.rotate(new Point(1, 0), Math.PI / 2);
double dist = geom.distance(new Point(0, 0), new Point(3, 4));
```

Primitive parameters pass through directly. This is the GC counterpart to `ProxyFactory` which marshals through linear memory.

### Low-Level GC API

For fine-grained control, use the GC extension directly:

```java
GcStructType pointType = GcStructType.builder("Point")
    .addField("x", GcFieldType.f64(), true)
    .addField("y", GcFieldType.f64(), true)
    .build();

GcStructInstance point = gc.createStruct(pointType,
    GcValue.f64(3.0), GcValue.f64(4.0));

double x = point.getField(0).asF64();
point.setField(0, GcValue.f64(5.0));
```

## Testing

The `webassembly4j-testing` module provides JUnit 5 support for running tests against every available engine:

```java
@WasmTest
@WasmModule("math.wasm")
void addFunction(Instance instance) {
    Function add = instance.function("add").orElseThrow();
    assertEquals(5, add.invoke(2, 3));
}
```

Each test method runs once per discovered engine. The extension handles engine and instance lifecycle automatically.

## Instance Pooling

For high-throughput scenarios, pool and reuse instances:

```java
PoolConfig config = PoolConfig.builder()
    .minSize(2)
    .maxSize(16)
    .build();

try (WasmInstancePool pool = WasmInstancePool.create(wasmBytes, config)) {
    try (PooledInstance inst = pool.borrow()) {
        inst.function("handle").orElseThrow().invoke(request);
    } // instance returned to pool on close
}
```

## Spring Boot Integration

Add `webassembly4j-spring` and a provider to your Spring Boot application:

```xml
<dependency>
    <groupId>ai.tegmentum.webassembly4j</groupId>
    <artifactId>webassembly4j-spring</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

An `Engine` bean is auto-configured and available for injection. Configuration properties:

```properties
webassembly4j.enabled=true
webassembly4j.engine=wasmtime
```

Health indicator and actuator endpoint (`/actuator/wasm`) are registered when Spring Boot Actuator is present.

## Bindgen

Generate Java bindings from WIT (WebAssembly Interface Types) definitions:

```xml
<plugin>
    <groupId>ai.tegmentum.webassembly4j</groupId>
    <artifactId>webassembly4j-bindgen</artifactId>
    <version>1.0.0-SNAPSHOT</version>
    <executions>
        <execution>
            <goals>
                <goal>generate</goal>
            </goals>
        </execution>
    </executions>
</plugin>
```

Supports both modern (Java 17+ records) and legacy (Java 8+ POJOs) code styles.

## Building

```bash
./mvnw clean install
```

The API module targets Java 8+ as a Multi-Release JAR (8/11/22). Core modules require Java 11+. The full build including all providers requires JDK 22+ (for the MRJAR Java 22 overlay compilation).

## License

[Apache License 2.0](LICENSE)
