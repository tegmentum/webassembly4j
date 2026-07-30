/*
 * Copyright 2026 Tegmentum AI
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package ai.tegmentum.webassembly4j.provider.wasmos;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import ai.tegmentum.webassembly4j.api.Component;
import ai.tegmentum.webassembly4j.api.ComponentInstance;
import ai.tegmentum.webassembly4j.api.Engine;
import ai.tegmentum.webassembly4j.provider.wasmos.ext.WasmosAsyncExtension;
import ai.tegmentum.webassembly4j.provider.wasmos.ext.WitErrorContextException;
import ai.tegmentum.webassembly4j.spi.EngineProvider;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ServiceLoader;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Slice-1 async coverage for wasmos-provider. The MVP promise is:
 * {@code instance.extension(WasmosAsyncExtension.class).invokeAsync(...)}
 * returns a {@link CompletableFuture} that behaves like a well-formed
 * async wrapper around the sync invoke path — the chain composes, errors
 * propagate, and cancel() at least severs the downstream promise.
 *
 * <p>These tests reuse the {@code composed_wasmtime.wasm} demo fixture as
 * the async surface's north-star acceptance case (mirroring the sync-side
 * {@link WasmosProviderDemoTest#composedRunReturns42}). If the fixture
 * isn't present the async tests skip via {@link org.junit.jupiter.api.Assumptions}
 * — the local wasmos checkout is a soft dependency, not a hard one.
 */
class WasmosAsyncInvokeTest {

    private static final Path COMPOSED_FIXTURE = Path.of(
            System.getProperty("user.home"),
            "git", "wasmos", "tests", "e2e-fixtures", "composed_wasmtime.wasm");

    private static EngineProvider wasmosProvider() {
        return ServiceLoader.load(EngineProvider.class)
                .stream()
                .map(ServiceLoader.Provider::get)
                .filter(p -> "wasmos".equals(p.descriptor().providerId()))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "wasmos provider not on classpath — see WasmosProviderDemoTest"));
    }

    @Test
    @DisplayName("extension(WasmosAsyncExtension.class) is present on wasmos component instance")
    void extensionIsPresent() {
        assumeTrue(Files.exists(COMPOSED_FIXTURE),
                "composed_wasmtime.wasm fixture not present at " + COMPOSED_FIXTURE);
        try (Engine engine = wasmosProvider().create(null);
             Component component = engine.loadComponent(Files.readAllBytes(COMPOSED_FIXTURE))) {
            final ComponentInstance instance = component.instantiate();
            try {
                assertTrue(instance.extension(WasmosAsyncExtension.class).isPresent(),
                        "wasmos component instance must surface WasmosAsyncExtension");
            } finally {
                if (instance instanceof WasmosComponentInstanceAdapter) {
                    ((WasmosComponentInstanceAdapter) instance).closeInternal();
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    @DisplayName("invokeAsync(\"run\").get() returns 42 through the demo fixture")
    void invokeAsyncRunReturns42() throws Exception {
        assumeTrue(Files.exists(COMPOSED_FIXTURE),
                "composed_wasmtime.wasm fixture not present at " + COMPOSED_FIXTURE);
        final byte[] bytes = Files.readAllBytes(COMPOSED_FIXTURE);

        try (Engine engine = wasmosProvider().create(null);
             Component component = engine.loadComponent(bytes)) {
            final ComponentInstance instance = component.instantiate();
            try {
                final WasmosAsyncExtension async = instance
                        .extension(WasmosAsyncExtension.class)
                        .orElseThrow(() -> new AssertionError("async extension missing"));

                final CompletableFuture<Object> future = async.invokeAsync("run");
                assertNotNull(future, "invokeAsync must return a non-null future");

                // 30s cap is generous — the sync path returns in ms; timing
                // out here would indicate the worker pool never dispatched.
                final Object result = future.get(30, TimeUnit.SECONDS);
                assertEquals(42, ((Number) result).intValue(),
                        "invokeAsync('run') must return 42, matching sync semantics");
            } finally {
                if (instance instanceof WasmosComponentInstanceAdapter) {
                    ((WasmosComponentInstanceAdapter) instance).closeInternal();
                }
            }
        }
    }

    @Test
    @DisplayName("thenApply chain composes: invokeAsync('run').thenApply(+1).get() returns 43")
    void thenApplyChainComposes() throws Exception {
        assumeTrue(Files.exists(COMPOSED_FIXTURE),
                "composed_wasmtime.wasm fixture not present at " + COMPOSED_FIXTURE);
        final byte[] bytes = Files.readAllBytes(COMPOSED_FIXTURE);

        try (Engine engine = wasmosProvider().create(null);
             Component component = engine.loadComponent(bytes)) {
            final ComponentInstance instance = component.instantiate();
            try {
                final WasmosAsyncExtension async = instance
                        .extension(WasmosAsyncExtension.class)
                        .orElseThrow(() -> new AssertionError("async extension missing"));

                final Integer result = async.invokeAsync("run")
                        .thenApply(x -> ((Number) x).intValue() + 1)
                        .get(30, TimeUnit.SECONDS);
                assertEquals(43, result.intValue(),
                        "thenApply on invokeAsync must propagate the (+1) transform");
            } finally {
                if (instance instanceof WasmosComponentInstanceAdapter) {
                    ((WasmosComponentInstanceAdapter) instance).closeInternal();
                }
            }
        }
    }

    @Test
    @DisplayName("invokeAsync on a missing export completes exceptionally with ExecutionException")
    void invokeAsyncMissingExportCompletesExceptionally() throws Exception {
        assumeTrue(Files.exists(COMPOSED_FIXTURE),
                "composed_wasmtime.wasm fixture not present at " + COMPOSED_FIXTURE);
        final byte[] bytes = Files.readAllBytes(COMPOSED_FIXTURE);

        try (Engine engine = wasmosProvider().create(null);
             Component component = engine.loadComponent(bytes)) {
            final ComponentInstance instance = component.instantiate();
            try {
                final WasmosAsyncExtension async = instance
                        .extension(WasmosAsyncExtension.class)
                        .orElseThrow(() -> new AssertionError("async extension missing"));

                final CompletableFuture<Object> future =
                        async.invokeAsync("this-export-does-not-exist");

                final ExecutionException ex = assertThrows(ExecutionException.class,
                        () -> future.get(30, TimeUnit.SECONDS));
                assertNotNull(ex.getCause(),
                        "ExecutionException must wrap the underlying invoke failure");
            } finally {
                if (instance instanceof WasmosComponentInstanceAdapter) {
                    ((WasmosComponentInstanceAdapter) instance).closeInternal();
                }
            }
        }
    }

    @Test
    @DisplayName("cancel(true) marks the Java future cancelled — WASM interrupt is best-effort")
    void cancelIsBestEffort() throws Exception {
        assumeTrue(Files.exists(COMPOSED_FIXTURE),
                "composed_wasmtime.wasm fixture not present at " + COMPOSED_FIXTURE);
        final byte[] bytes = Files.readAllBytes(COMPOSED_FIXTURE);

        try (Engine engine = wasmosProvider().create(null);
             Component component = engine.loadComponent(bytes)) {
            final ComponentInstance instance = component.instantiate();
            try {
                final WasmosAsyncExtension async = instance
                        .extension(WasmosAsyncExtension.class)
                        .orElseThrow(() -> new AssertionError("async extension missing"));

                // Kick off a call and immediately cancel it. On this fast
                // demo the worker may have already finished — either way,
                // the semantics we're pinning down are:
                //   * cancel(true) never throws
                //   * post-cancel, the future either reports cancelled OR
                //     already-completed (well-defined race; we accept both)
                //   * the promise chain sees CancellationException on
                //     downstream get() if cancel won the race
                final CompletableFuture<Object> future = async.invokeAsync("run");
                final boolean cancelResult = future.cancel(true);

                if (cancelResult) {
                    assertTrue(future.isCancelled(),
                            "cancel(true) that returned true must leave isCancelled() true");
                    assertThrows(CancellationException.class,
                            () -> future.get(5, TimeUnit.SECONDS),
                            "cancelled future must throw CancellationException on get()");
                } else {
                    // Lost the race — the future had already completed
                    // normally before cancel could take effect. Confirm the
                    // result is still the expected 42 so we know we're not
                    // silently masking a broken invoke path.
                    assertTrue(future.isDone(),
                            "cancel(true)==false must mean the future had already completed");
                    assertFalse(future.isCancelled(),
                            "cancel(true)==false must leave isCancelled() false");
                    assertEquals(42, ((Number) future.get()).intValue(),
                            "if cancel lost the race, the result must still be the sync 42");
                }
            } finally {
                if (instance instanceof WasmosComponentInstanceAdapter) {
                    ((WasmosComponentInstanceAdapter) instance).closeInternal();
                }
            }
        }
    }

    @Test
    @DisplayName("awaitFuture on unknown WitFuture id completes exceptionally with API-gap message")
    void awaitFutureUnknownIdSurfacesGap() throws Exception {
        // Slice-1 r.2 boundary test — the parked-slot lookup on the Rust
        // side happens BEFORE the "wasmtime API gap" bail-out, so hitting
        // an unknown id gets the standard "unknown future id" error path,
        // NOT the API-gap message. The API-gap message only surfaces once
        // the slot lookup succeeds. Both paths must complete the Java
        // future exceptionally (never throw synchronously from awaitFuture)
        // and both must wrap the JNI failure so downstream error handling
        // has a stable exception shape.
        assumeTrue(Files.exists(COMPOSED_FIXTURE),
                "composed_wasmtime.wasm fixture not present at " + COMPOSED_FIXTURE);
        final byte[] bytes = Files.readAllBytes(COMPOSED_FIXTURE);

        try (Engine engine = wasmosProvider().create(null);
             Component component = engine.loadComponent(bytes)) {
            final ComponentInstance instance = component.instantiate();
            try {
                final WasmosAsyncExtension async = instance
                        .extension(WasmosAsyncExtension.class)
                        .orElseThrow(() -> new AssertionError("async extension missing"));

                // No component in-tree returns a `future<T>`, so we can't
                // exercise a real parked handle end-to-end here — but the
                // "unknown id" path is what the API-gap swap-in will
                // preserve, so it's the right shape to lock down.
                final WasmosMarshalling.WitFuture bogus =
                        new WasmosMarshalling.WitFuture(999_999L, "no-such-future");

                final CompletableFuture<Object> future = async.awaitFuture(bogus);
                assertNotNull(future, "awaitFuture must never return null");
                final ExecutionException ex = assertThrows(ExecutionException.class,
                        () -> future.get(10, TimeUnit.SECONDS));
                assertNotNull(ex.getCause(),
                        "awaitFuture failure must wrap the JNI exception in ExecutionException");
                final String msg = ex.getCause().getMessage();
                assertTrue(msg.contains("999999") || msg.contains("unknown future id"),
                        "expected 'unknown future id' or the numeric id in the error; got: " + msg);
            } finally {
                if (instance instanceof WasmosComponentInstanceAdapter) {
                    ((WasmosComponentInstanceAdapter) instance).closeInternal();
                }
            }
        }
    }

    @Test
    @DisplayName("awaitFuture(null) completes exceptionally with IllegalArgumentException")
    void awaitFutureRejectsNullArg() throws Exception {
        // Type-safety guard on the Object-typed carrier: passing null (or a
        // non-WitFuture) must fail cleanly rather than NPE'ing inside the
        // JNI shim. Completes-exceptionally rather than sync-throws so
        // callers using thenApply/handle chains see a uniform failure shape.
        assumeTrue(Files.exists(COMPOSED_FIXTURE),
                "composed_wasmtime.wasm fixture not present at " + COMPOSED_FIXTURE);
        final byte[] bytes = Files.readAllBytes(COMPOSED_FIXTURE);

        try (Engine engine = wasmosProvider().create(null);
             Component component = engine.loadComponent(bytes)) {
            final ComponentInstance instance = component.instantiate();
            try {
                final WasmosAsyncExtension async = instance
                        .extension(WasmosAsyncExtension.class)
                        .orElseThrow(() -> new AssertionError("async extension missing"));

                final CompletableFuture<Object> future = async.awaitFuture(null);
                assertNotNull(future);
                final ExecutionException ex = assertThrows(ExecutionException.class,
                        () -> future.get(5, TimeUnit.SECONDS));
                assertTrue(ex.getCause() instanceof IllegalArgumentException,
                        "awaitFuture(null) should complete exceptionally with IAE; got "
                                + ex.getCause());

                // Same shape for a wrongly-typed argument.
                final CompletableFuture<Object> f2 = async.awaitFuture("not-a-witfuture");
                final ExecutionException ex2 = assertThrows(ExecutionException.class,
                        () -> f2.get(5, TimeUnit.SECONDS));
                assertTrue(ex2.getCause() instanceof IllegalArgumentException);
            } finally {
                if (instance instanceof WasmosComponentInstanceAdapter) {
                    ((WasmosComponentInstanceAdapter) instance).closeInternal();
                }
            }
        }
    }

    @Test
    @DisplayName("closeFuture on unknown WitFuture id throws ExecutionException wrapping the JNI failure")
    void closeFutureUnknownIdThrows() throws Exception {
        // Synchronous by design (no async pool detour) — closeFuture is a
        // registry eviction + wasmtime FutureAny::close(store), fast enough
        // that offloading to a worker adds latency without value.
        assumeTrue(Files.exists(COMPOSED_FIXTURE),
                "composed_wasmtime.wasm fixture not present at " + COMPOSED_FIXTURE);
        final byte[] bytes = Files.readAllBytes(COMPOSED_FIXTURE);

        try (Engine engine = wasmosProvider().create(null);
             Component component = engine.loadComponent(bytes)) {
            final ComponentInstance instance = component.instantiate();
            try {
                final WasmosAsyncExtension async = instance
                        .extension(WasmosAsyncExtension.class)
                        .orElseThrow(() -> new AssertionError("async extension missing"));

                // Null / wrong type — IllegalArgumentException, synchronous.
                assertThrows(IllegalArgumentException.class,
                        () -> async.closeFuture(null));
                assertThrows(IllegalArgumentException.class,
                        () -> async.closeFuture(new Object()));

                // Unknown id — the JNI throws WebAssemblyException; the
                // extension wraps it in ExecutionException to keep the
                // caller-visible exception type consistent with invoke
                // failures.
                final WasmosMarshalling.WitFuture bogus =
                        new WasmosMarshalling.WitFuture(424242L, "gone");
                final ai.tegmentum.webassembly4j.api.exception.ExecutionException ex =
                        assertThrows(
                                ai.tegmentum.webassembly4j.api.exception.ExecutionException.class,
                                () -> async.closeFuture(bogus));
                assertTrue(ex.getMessage().contains("424242")
                                || ex.getMessage().contains("unknown future id"),
                        "expected 'unknown future id' or the numeric id in the error; got: "
                                + ex.getMessage());
            } finally {
                if (instance instanceof WasmosComponentInstanceAdapter) {
                    ((WasmosComponentInstanceAdapter) instance).closeInternal();
                }
            }
        }
    }

    @Test
    @DisplayName("readStream on unknown WitStream id completes exceptionally with wrapping ExecutionException")
    void readStreamUnknownIdSurfacesGap() throws Exception {
        // Structural mirror of awaitFutureUnknownIdSurfacesGap. The parked
        // slot lookup happens BEFORE the wasmtime API-gap bail-out, so an
        // unknown id gets the "unknown stream id" error path. Both paths
        // must complete the Java future exceptionally.
        assumeTrue(Files.exists(COMPOSED_FIXTURE),
                "composed_wasmtime.wasm fixture not present at " + COMPOSED_FIXTURE);
        final byte[] bytes = Files.readAllBytes(COMPOSED_FIXTURE);

        try (Engine engine = wasmosProvider().create(null);
             Component component = engine.loadComponent(bytes)) {
            final ComponentInstance instance = component.instantiate();
            try {
                final WasmosAsyncExtension async = instance
                        .extension(WasmosAsyncExtension.class)
                        .orElseThrow(() -> new AssertionError("async extension missing"));

                final WasmosMarshalling.WitStream bogus =
                        new WasmosMarshalling.WitStream(777_777L, "no-such-stream");

                final CompletableFuture<Object> future = async.readStream(bogus);
                assertNotNull(future, "readStream must never return null");
                final ExecutionException ex = assertThrows(ExecutionException.class,
                        () -> future.get(10, TimeUnit.SECONDS));
                assertNotNull(ex.getCause(),
                        "readStream failure must wrap the JNI exception in ExecutionException");
                final String msg = ex.getCause().getMessage();
                assertTrue(msg.contains("777777") || msg.contains("unknown stream id"),
                        "expected 'unknown stream id' or the numeric id in the error; got: " + msg);
            } finally {
                if (instance instanceof WasmosComponentInstanceAdapter) {
                    ((WasmosComponentInstanceAdapter) instance).closeInternal();
                }
            }
        }
    }

    @Test
    @DisplayName("readStream(null) completes exceptionally with IllegalArgumentException")
    void readStreamRejectsNullArg() throws Exception {
        // Type-safety guard on the Object-typed carrier — passing null or
        // a non-WitStream must fail cleanly rather than NPE'ing inside the
        // JNI shim.
        assumeTrue(Files.exists(COMPOSED_FIXTURE),
                "composed_wasmtime.wasm fixture not present at " + COMPOSED_FIXTURE);
        final byte[] bytes = Files.readAllBytes(COMPOSED_FIXTURE);

        try (Engine engine = wasmosProvider().create(null);
             Component component = engine.loadComponent(bytes)) {
            final ComponentInstance instance = component.instantiate();
            try {
                final WasmosAsyncExtension async = instance
                        .extension(WasmosAsyncExtension.class)
                        .orElseThrow(() -> new AssertionError("async extension missing"));

                final CompletableFuture<Object> future = async.readStream(null);
                assertNotNull(future);
                final ExecutionException ex = assertThrows(ExecutionException.class,
                        () -> future.get(5, TimeUnit.SECONDS));
                assertTrue(ex.getCause() instanceof IllegalArgumentException);

                final CompletableFuture<Object> f2 = async.readStream("not-a-witstream");
                final ExecutionException ex2 = assertThrows(ExecutionException.class,
                        () -> f2.get(5, TimeUnit.SECONDS));
                assertTrue(ex2.getCause() instanceof IllegalArgumentException);
            } finally {
                if (instance instanceof WasmosComponentInstanceAdapter) {
                    ((WasmosComponentInstanceAdapter) instance).closeInternal();
                }
            }
        }
    }

    @Test
    @DisplayName("closeStream on unknown WitStream id throws ExecutionException wrapping the JNI failure")
    void closeStreamUnknownIdThrows() throws Exception {
        // Synchronous — mirrors closeFutureUnknownIdThrows.
        assumeTrue(Files.exists(COMPOSED_FIXTURE),
                "composed_wasmtime.wasm fixture not present at " + COMPOSED_FIXTURE);
        final byte[] bytes = Files.readAllBytes(COMPOSED_FIXTURE);

        try (Engine engine = wasmosProvider().create(null);
             Component component = engine.loadComponent(bytes)) {
            final ComponentInstance instance = component.instantiate();
            try {
                final WasmosAsyncExtension async = instance
                        .extension(WasmosAsyncExtension.class)
                        .orElseThrow(() -> new AssertionError("async extension missing"));

                assertThrows(IllegalArgumentException.class,
                        () -> async.closeStream(null));
                assertThrows(IllegalArgumentException.class,
                        () -> async.closeStream(new Object()));

                final WasmosMarshalling.WitStream bogus =
                        new WasmosMarshalling.WitStream(313131L, "gone");
                final ai.tegmentum.webassembly4j.api.exception.ExecutionException ex =
                        assertThrows(
                                ai.tegmentum.webassembly4j.api.exception.ExecutionException.class,
                                () -> async.closeStream(bogus));
                assertTrue(ex.getMessage().contains("313131")
                                || ex.getMessage().contains("unknown stream id"),
                        "expected 'unknown stream id' or the numeric id in the error; got: "
                                + ex.getMessage());
            } finally {
                if (instance instanceof WasmosComponentInstanceAdapter) {
                    ((WasmosComponentInstanceAdapter) instance).closeInternal();
                }
            }
        }
    }

    @Test
    @DisplayName("closeErrorContext on unknown WitErrorContext id throws ExecutionException wrapping the JNI failure")
    void closeErrorContextUnknownIdThrows() throws Exception {
        // Synchronous like the other close* methods. The Rust side has no
        // wasmtime dispose to call — it's pure registry eviction — but the
        // "unknown id" error surface is the same shape.
        assumeTrue(Files.exists(COMPOSED_FIXTURE),
                "composed_wasmtime.wasm fixture not present at " + COMPOSED_FIXTURE);
        final byte[] bytes = Files.readAllBytes(COMPOSED_FIXTURE);

        try (Engine engine = wasmosProvider().create(null);
             Component component = engine.loadComponent(bytes)) {
            final ComponentInstance instance = component.instantiate();
            try {
                final WasmosAsyncExtension async = instance
                        .extension(WasmosAsyncExtension.class)
                        .orElseThrow(() -> new AssertionError("async extension missing"));

                assertThrows(IllegalArgumentException.class,
                        () -> async.closeErrorContext(null));
                assertThrows(IllegalArgumentException.class,
                        () -> async.closeErrorContext(new Object()));

                final WasmosMarshalling.WitErrorContext bogus =
                        new WasmosMarshalling.WitErrorContext(212121L, 5L);
                final ai.tegmentum.webassembly4j.api.exception.ExecutionException ex =
                        assertThrows(
                                ai.tegmentum.webassembly4j.api.exception.ExecutionException.class,
                                () -> async.closeErrorContext(bogus));
                assertTrue(ex.getMessage().contains("212121")
                                || ex.getMessage().contains("unknown error-context id"),
                        "expected 'unknown error-context id' or the numeric id in the error; got: "
                                + ex.getMessage());
            } finally {
                if (instance instanceof WasmosComponentInstanceAdapter) {
                    ((WasmosComponentInstanceAdapter) instance).closeInternal();
                }
            }
        }
    }

    @Test
    @DisplayName("wrapErrorContext produces a WitErrorContextException that completes a CF exceptionally")
    void wrapErrorContextCompletesFutureExceptionally() throws Exception {
        // The integration story: a caller receives a WitErrorContext (via an
        // invoke result), transforms it into a WitErrorContextException in a
        // thenApply / handle chain, and downstream get() surfaces the
        // exception with the carrier still inspectable via .errorContext().
        // Uses a synthetic WitErrorContext so no fixture-returning-error-context
        // is required (none exists in-tree).
        assumeTrue(Files.exists(COMPOSED_FIXTURE),
                "composed_wasmtime.wasm fixture not present at " + COMPOSED_FIXTURE);
        final byte[] bytes = Files.readAllBytes(COMPOSED_FIXTURE);

        try (Engine engine = wasmosProvider().create(null);
             Component component = engine.loadComponent(bytes)) {
            final ComponentInstance instance = component.instantiate();
            try {
                final WasmosAsyncExtension async = instance
                        .extension(WasmosAsyncExtension.class)
                        .orElseThrow(() -> new AssertionError("async extension missing"));

                // Type-safety guards first — null / wrong type.
                assertThrows(IllegalArgumentException.class,
                        () -> async.wrapErrorContext(null));
                assertThrows(IllegalArgumentException.class,
                        () -> async.wrapErrorContext(new Object()));

                final WasmosMarshalling.WitErrorContext we =
                        new WasmosMarshalling.WitErrorContext(101L, 202L);
                final WitErrorContextException wrapped = async.wrapErrorContext(we);
                assertNotNull(wrapped, "wrapErrorContext must never return null");
                assertNotNull(wrapped.errorContext(),
                        "wrapped exception must carry the underlying handle");
                assertTrue(wrapped.getMessage().contains("101")
                                && wrapped.getMessage().contains("202"),
                        "wrapped exception message should include tableId + rep; got: "
                                + wrapped.getMessage());

                // The lift-through-CompletableFuture story.
                final CompletableFuture<Object> src = CompletableFuture.completedFuture(we);
                final CompletableFuture<Object> composed = src.thenApply(v -> {
                    throw async.wrapErrorContext(v);
                });
                final ExecutionException ex = assertThrows(ExecutionException.class,
                        () -> composed.get(5, TimeUnit.SECONDS));
                assertTrue(ex.getCause() instanceof WitErrorContextException,
                        "cause should be WitErrorContextException; got "
                                + (ex.getCause() == null ? "null" : ex.getCause().getClass()));
                final WitErrorContextException cause = (WitErrorContextException) ex.getCause();
                assertEquals(we, cause.errorContext(),
                        "wrapped handle should equal the source WitErrorContext");
            } finally {
                if (instance instanceof WasmosComponentInstanceAdapter) {
                    ((WasmosComponentInstanceAdapter) instance).closeInternal();
                }
            }
        }
    }

    @Test
    @DisplayName("engine close() shuts down the async pool cleanly")
    void engineCloseShutsDownAsyncPool() throws Exception {
        assumeTrue(Files.exists(COMPOSED_FIXTURE),
                "composed_wasmtime.wasm fixture not present at " + COMPOSED_FIXTURE);
        final byte[] bytes = Files.readAllBytes(COMPOSED_FIXTURE);

        final Engine engine = wasmosProvider().create(null);
        try {
            final Component component = engine.loadComponent(bytes);
            final ComponentInstance instance = component.instantiate();
            try {
                final WasmosAsyncExtension async = instance
                        .extension(WasmosAsyncExtension.class)
                        .orElseThrow(() -> new AssertionError("async extension missing"));

                // Prime the pool by dispatching at least one invoke — this
                // forces asyncExecutor() to create the ThreadPoolExecutor
                // whose shutdown we want to exercise.
                async.invokeAsync("run").get(30, TimeUnit.SECONDS);
            } finally {
                if (instance instanceof WasmosComponentInstanceAdapter) {
                    ((WasmosComponentInstanceAdapter) instance).closeInternal();
                }
                component.close();
            }
        } finally {
            // The main assertion: close() returns in bounded time with the
            // pool torn down. If pool shutdown hangs, the JVM-level test
            // timeout will fail this. Bounded via an explicit future in
            // case someone regresses the internal awaitTermination.
            final CompletableFuture<Void> closed = CompletableFuture.runAsync(engine::close);
            try {
                closed.get(10, TimeUnit.SECONDS);
            } catch (TimeoutException te) {
                throw new AssertionError("engine.close() did not return within 10s — "
                        + "async pool shutdown likely hanging");
            }
        }
    }
}
