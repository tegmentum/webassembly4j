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

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Factory for the {@code wasmos-provider-async} worker pool used by
 * {@link ai.tegmentum.webassembly4j.provider.wasmos.ext.WasmosAsyncExtension}
 * to schedule blocking JNI invokes off the caller's thread.
 *
 * <p>Rationale for a dedicated pool over {@link java.util.concurrent.ForkJoinPool#commonPool}:
 * the commonPool is shared with parallel streams and any other JVM async
 * code; blocking JNI calls on it starve unrelated work and can deadlock
 * fork/join-based algorithms. A named pool also makes stacks / thread
 * dumps immediately readable.
 *
 * <p>Sizing: 0 core threads (so an idle engine doesn't hold worker threads
 * indefinitely), max {@value #MAX_WORKERS} workers, 60s keep-alive. Chosen
 * to match the "cached thread pool with a bounded max" recommendation in
 * the slice-1 brief. Callers submitting more than {@value #MAX_WORKERS}
 * concurrent invocations will queue — safer than unbounded expansion which
 * would fight wasmtime's own worker threads for OS-level scheduling
 * resources.
 *
 * <p>Threads are daemons so a stray reference to an unclosed engine doesn't
 * block JVM shutdown.
 */
final class WasmosAsyncExecutors {

    static final int MAX_WORKERS = 32;
    private static final long KEEP_ALIVE_SECONDS = 60L;

    private WasmosAsyncExecutors() {}

    /**
     * Create a fresh bounded cached pool. One per {@link WasmosEngineAdapter};
     * shut down when the engine closes.
     */
    static ThreadPoolExecutor newEnginePool() {
        return new ThreadPoolExecutor(
                0,
                MAX_WORKERS,
                KEEP_ALIVE_SECONDS,
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(),
                new AsyncThreadFactory());
    }

    /** Named daemon threads for cleaner stack dumps. */
    private static final class AsyncThreadFactory implements ThreadFactory {
        private static final AtomicInteger POOL_ID = new AtomicInteger();
        private final AtomicInteger threadId = new AtomicInteger();
        private final int poolIndex = POOL_ID.incrementAndGet();

        @Override
        public Thread newThread(Runnable r) {
            final Thread t = new Thread(r,
                    "wasmos-provider-async-" + poolIndex + "-" + threadId.incrementAndGet());
            t.setDaemon(true);
            return t;
        }
    }
}
