package ai.tegmentum.webassembly4j.pool;

import ai.tegmentum.webassembly4j.api.Engine;
import ai.tegmentum.webassembly4j.api.Instance;
import ai.tegmentum.webassembly4j.api.Module;

import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Thread-safe pool of WASM instances compiled from a single module.
 * Compile once, instantiate many — instances are reused across requests.
 *
 * <pre>{@code
 * try (WasmInstancePool pool = WasmInstancePool.create(wasmBytes, PoolConfig.defaults())) {
 *     try (PooledInstance inst = pool.borrow()) {
 *         Function add = inst.function("add").orElseThrow();
 *         int result = ((Number) add.invoke(3, 4)).intValue();
 *     }
 * }
 * }</pre>
 */
public final class WasmInstancePool implements AutoCloseable {

    private final Engine engine;
    private final Module module;
    private final PoolConfig config;
    private final BlockingQueue<Instance> available;
    private final AtomicInteger totalCreated = new AtomicInteger(0);
    private final AtomicBoolean closed = new AtomicBoolean(false);

    private WasmInstancePool(Engine engine, Module module, PoolConfig config) {
        this.engine = engine;
        this.module = module;
        this.config = config;
        this.available = new ArrayBlockingQueue<>(config.maxSize());

        // Pre-warm with minSize instances
        for (int i = 0; i < config.minSize(); i++) {
            available.offer(createInstance());
        }
    }

    /**
     * Creates a pool using the default engine discovered on the classpath.
     */
    public static WasmInstancePool create(byte[] wasmBytes, PoolConfig config) {
        Objects.requireNonNull(wasmBytes, "wasmBytes");
        Objects.requireNonNull(config, "config");
        Engine engine = ai.tegmentum.webassembly4j.api.WebAssembly.builder().build();
        Module module = engine.loadModule(wasmBytes);
        return new WasmInstancePool(engine, module, config);
    }

    /**
     * Creates a pool using a specific engine.
     */
    public static WasmInstancePool create(Engine engine, byte[] wasmBytes, PoolConfig config) {
        Objects.requireNonNull(engine, "engine");
        Objects.requireNonNull(wasmBytes, "wasmBytes");
        Objects.requireNonNull(config, "config");
        Module module = engine.loadModule(wasmBytes);
        return new WasmInstancePool(engine, module, config);
    }

    /**
     * Creates a pool from a pre-compiled module.
     */
    public static WasmInstancePool create(Engine engine, Module module, PoolConfig config) {
        Objects.requireNonNull(engine, "engine");
        Objects.requireNonNull(module, "module");
        Objects.requireNonNull(config, "config");
        return new WasmInstancePool(engine, module, config);
    }

    /**
     * Borrows an instance from the pool. If none are available and the pool
     * has not reached its maximum size, a new instance is created. Otherwise,
     * this method blocks until an instance becomes available or the timeout expires.
     *
     * @return a pooled instance that must be closed to return it to the pool
     * @throws PoolExhaustedException if no instance is available within the timeout
     * @throws IllegalStateException if the pool has been closed
     */
    public PooledInstance borrow() {
        if (closed.get()) {
            throw new IllegalStateException("Pool has been closed");
        }

        // Try to get an available instance
        Instance instance = available.poll();
        if (instance != null) {
            return new PooledInstance(instance, this);
        }

        // Try to create a new instance if under max
        if (totalCreated.get() < config.maxSize()) {
            int current = totalCreated.get();
            if (current < config.maxSize() && totalCreated.compareAndSet(current, current + 1)) {
                try {
                    instance = module.instantiate();
                    return new PooledInstance(instance, this);
                } catch (Exception e) {
                    totalCreated.decrementAndGet();
                    throw e;
                }
            }
        }

        // Block waiting for a returned instance
        try {
            instance = available.poll(config.borrowTimeoutMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new PoolExhaustedException(
                    "Interrupted while waiting for a pool instance", e);
        }

        if (instance == null) {
            throw new PoolExhaustedException(
                    "No instance available within " + config.borrowTimeoutMillis() + "ms "
                    + "(pool maxSize=" + config.maxSize() + ")");
        }

        return new PooledInstance(instance, this);
    }

    /**
     * Returns an instance to the pool. Called automatically by
     * {@link PooledInstance#close()}.
     */
    void returnInstance(PooledInstance pooled) {
        if (closed.get()) {
            // Pool is closed, discard the instance
            return;
        }
        if (!available.offer(pooled.delegate())) {
            // Queue is full (shouldn't happen), discard
        }
    }

    /**
     * Returns the number of instances currently available in the pool.
     */
    public int availableCount() {
        return available.size();
    }

    /**
     * Returns the total number of instances created by this pool.
     */
    public int totalCreated() {
        return totalCreated.get();
    }

    /**
     * Returns the pool configuration.
     */
    public PoolConfig config() {
        return config;
    }

    /**
     * Returns whether this pool has been closed.
     */
    public boolean isClosed() {
        return closed.get();
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            // Drain and discard remaining instances
            available.clear();
            module.close();
            engine.close();
        }
    }

    private Instance createInstance() {
        totalCreated.incrementAndGet();
        return module.instantiate();
    }
}
