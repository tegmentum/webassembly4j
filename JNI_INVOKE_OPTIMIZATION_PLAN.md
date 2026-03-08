# JNI Function Invocation Optimization Plan

## Problem

JNI-based function calls suffer a severe throughput drop when functions take parameters or return values. Benchmark data from webassembly4j (Java 26, aarch64, `-f 1 -wi 0 -i 1`):

| Benchmark | Wasmtime JNI | Wasmtime Panama | WAMR JNI | WAMR Panama |
|-----------|-------------|-----------------|----------|-------------|
| Void (no args, no return) | 1.68M ops/s | 3.10M ops/s | 1.57M ops/s | 1.42M ops/s |
| Simple Add (2 i32 args, 1 i32 return) | 256K ops/s | 1.95M ops/s | 353K ops/s | 1.27M ops/s |
| **Void → Add drop** | **6.6x** | **1.6x** | **4.5x** | **1.1x** |

Panama barely degrades with parameters; JNI drops 4-7x. The actual WASM execution (`i32.add`) is negligible — the bottleneck is entirely JNI marshalling overhead.

## Root Cause

Each JNI boundary crossing (Java ↔ native) has significant fixed overhead (~100-300ns). A single `add(int, int) -> int` call requires multiple crossings just for parameter and result marshalling.

### Current JNI Crossing Count per `add(int, int) -> int`

**wasmtime4j** (~9-10 crossings):
| Step | JNI Calls | Location |
|------|-----------|----------|
| Get array length | 1 | `function.rs:143-184` |
| Get array element (×2 params) | 2 | `function.rs:143-184` |
| `find_class("java/lang/Integer")` | 1 | `function.rs:143-184` |
| `is_instance_of` (×2 params) | 2 | `function.rs:143-184` |
| `call_method("intValue")` (×2 params) | 2 | `function.rs:143-184` |
| `find_class("WasmValue")` for results | 1 | `linker.rs:202-237` |
| `new_object_array` | 1 | `linker.rs:202-237` |
| `call_static_method("i32")` | 1 | `linker.rs:202-237` |
| `set_object_array_element` | 1 | `linker.rs:202-237` |

**wamr4j** (~6 crossings):
| Step | JNI Calls | Location |
|------|-----------|----------|
| `get_array_length` | 1 | `jni_bindings.rs:2980` |
| `get_object_array_element` (×2 params) | 2 | `jni_bindings.rs:2992` |
| `call_method("intValue")` (×2 params) | 2 | `jni_bindings.rs:3088-3092` |
| `new_object("java/lang/Integer")` for result | 1 | `jni_bindings.rs:3064-3068` |

**Panama** (0 crossings): Parameters and results are written/read directly to/from `MemorySegment` buffers using layout operations. No boxing, no JNI method calls, no object allocation.

### Additional per-call allocations

- wasmtime4j: `Object[]` (Java), `Vec<Val>` params + `Vec<Val>` results (Rust), `WasmValue[]` + `WasmValue` objects (Java return)
- wamr4j: `Vec<WasmValue>` params, `Vec<WasmValT>` params copy, `Vec<WasmValT>` results, `Vec<WasmValue>` results copy (all Rust)

## Proposed Fix: Typed Primitive Fast Paths

Add JNI native methods that accept and return primitives directly, bypassing `Object[]` marshalling entirely.

### Phase 1: Add Typed Native Methods

#### wasmtime4j

**File**: `wasmtime4j-jni/src/main/java/ai/tegmentum/wasmtime4j/jni/JniFunction.java`

Add native methods alongside existing `nativeCallMultiValue`:

```java
// Fast paths for common signatures — zero boxing overhead
private static native int nativeCallII_I(long functionPtr, long storeHandle, int arg0, int arg1);
private static native int nativeCallI_I(long functionPtr, long storeHandle, int arg0);
private static native long nativeCallII_J(long functionPtr, long storeHandle, int arg0, int arg1);
private static native long nativeCallJ_J(long functionPtr, long storeHandle, long arg0);
private static native void nativeCallI_V(long functionPtr, long storeHandle, int arg0);
private static native void nativeCallII_V(long functionPtr, long storeHandle, int arg0, int arg1);
private static native void nativeCall_V(long functionPtr, long storeHandle);
private static native int nativeCall_I(long functionPtr, long storeHandle);
```

**File**: `wasmtime4j-native/src/jni/function.rs`

Implement each as a thin native function. Example for `nativeCallII_I`:

```rust
#[no_mangle]
pub extern "system" fn Java_ai_tegmentum_wasmtime4j_jni_JniFunction_nativeCallII_1I(
    mut env: JNIEnv,
    _class: JClass,
    function_ptr: jlong,
    store_handle: jlong,
    arg0: jint,
    arg1: jint,
) -> jint {
    let func_handle = unsafe { &*(function_ptr as *const FunctionHandle) };
    let func = func_handle.get_func();
    let store = unsafe { crate::store::core::get_store_mut(store_handle as *mut c_void).unwrap() };
    let mut store_lock = store.try_lock_store().unwrap();

    let params = [Val::I32(arg0), Val::I32(arg1)];
    let mut results = [Val::I32(0)];

    func.call(&mut *store_lock, &params, &mut results).unwrap();

    match results[0] {
        Val::I32(v) => v,
        _ => 0,
    }
}
```

This has **1 JNI crossing** (the call itself) and **zero allocations** — params and results are stack-allocated arrays.

#### wamr4j

**File**: `wamr4j-jni/src/main/java/ai/tegmentum/wamr4j/jni/impl/JniWebAssemblyFunction.java`

```java
private static native int nativeInvokeII_I(long functionHandle, int arg0, int arg1);
private static native int nativeInvokeI_I(long functionHandle, int arg0);
private static native long nativeInvokeJ_J(long functionHandle, long arg0);
private static native void nativeInvoke_V(long functionHandle);
private static native int nativeInvoke_I(long functionHandle);
// ... etc
```

**File**: `wamr4j-native/src/jni_bindings.rs`

```rust
#[no_mangle]
pub extern "system" fn Java_ai_tegmentum_wamr4j_jni_impl_JniWebAssemblyFunction_nativeInvokeII_1I(
    _env: JNIEnv,
    _class: JClass,
    function_handle: jlong,
    arg0: jint,
    arg1: jint,
) -> jint {
    let function_ref = unsafe { &*(function_handle as *const WamrFunction) };

    let mut args = [
        WasmValT::i32(arg0),
        WasmValT::i32(arg1),
    ];
    let mut results = [WasmValT::zeroed_i32()];

    unsafe {
        wasm_runtime_call_wasm_a(
            function_ref.exec_env,
            function_ref.handle,
            1, results.as_mut_ptr(),
            2, args.as_mut_ptr(),
        );
    }

    results[0].as_i32()
}
```

Again, **1 JNI crossing**, **zero heap allocations**.

### Phase 2: Java-Side Dispatch

Add a `call` method that selects the fast path based on cached function signature.

#### wasmtime4j

**File**: `wasmtime4j-jni/src/main/java/ai/tegmentum/wasmtime4j/jni/JniFunction.java`

```java
@Override
public WasmValue[] call(WasmValue... params) {
    FunctionType type = getFunctionType();
    List<WasmValueType> paramTypes = type.getParams();
    List<WasmValueType> resultTypes = type.getResults();

    // Fast path: match common signatures
    if (paramTypes.size() == 2
            && paramTypes.get(0) == WasmValueType.I32
            && paramTypes.get(1) == WasmValueType.I32
            && resultTypes.size() == 1
            && resultTypes.get(0) == WasmValueType.I32) {
        int result = nativeCallII_I(getNativeHandle(), store.getNativeHandle(),
                params[0].asInt(), params[1].asInt());
        return new WasmValue[] { WasmValue.i32(result) };
    }

    // Fall back to generic path
    return callGeneric(params);
}
```

Cache the signature match in a field at function lookup time to avoid per-call type checks:

```java
private enum FastPath { II_I, I_I, J_J, VOID, GENERIC }
private final FastPath fastPath; // set in constructor based on FunctionType
```

#### wamr4j

**File**: `wamr4j-jni/src/main/java/ai/tegmentum/wamr4j/jni/impl/JniWebAssemblyFunction.java`

Same pattern — cache the fast path enum at construction, dispatch in `invoke()`.

### Phase 3: Coverage

The following fast-path signatures cover the vast majority of real WASM function calls:

| Signature | Pattern | Example |
|-----------|---------|---------|
| `() -> void` | No-op, setup functions | `_start()`, `_initialize()` |
| `() -> i32` | Status, allocation | `malloc(0)`, `errno()` |
| `(i32) -> void` | Free, set | `free(ptr)` |
| `(i32) -> i32` | Unary ops, lookup | `strlen(ptr)` |
| `(i32, i32) -> i32` | Binary ops, most common | `add(a, b)`, `strcmp(a, b)` |
| `(i32, i32) -> void` | Write ops | `store(addr, val)` |
| `(i32, i32, i32) -> i32` | Memory ops | `memcpy(dst, src, len)` |
| `(i64) -> i64` | 64-bit unary | Hash functions |
| `(i64, i64) -> i64` | 64-bit binary | 64-bit arithmetic |
| `(f64, f64) -> f64` | FP binary | Math functions |

Anything not matching falls through to the existing generic `Object[]` path — no regressions.

## Expected Impact

| Metric | Before (JNI) | After (JNI fast path) | Panama (reference) |
|--------|-------------|----------------------|-------------------|
| JNI crossings per call | 6-10 | 1 | 0 |
| Heap allocations per call | 3-5 | 1 (`WasmValue[]` return) | 0 |
| Estimated add(int,int) throughput | 256-353K | 1-2M+ | 1.3-2.0M |

The fast path should bring JNI throughput close to Panama for lightweight calls, since the dominant cost (marshalling) is eliminated.

## Files to Modify

### wasmtime4j
- `wasmtime4j-jni/src/main/java/ai/tegmentum/wasmtime4j/jni/JniFunction.java` — Add native method declarations + dispatch logic
- `wasmtime4j-native/src/jni/function.rs` — Implement typed native methods

### wamr4j
- `wamr4j-jni/src/main/java/ai/tegmentum/wamr4j/jni/impl/JniWebAssemblyFunction.java` — Add native method declarations + dispatch logic
- `wamr4j-native/src/jni_bindings.rs` — Implement typed native methods

### Testing
- Existing test suites should pass without modification (fast paths are transparent)
- Add micro-benchmarks comparing generic vs fast-path invocation
- Verify with webassembly4j benchmarks that the void→add drop narrows to <2x
