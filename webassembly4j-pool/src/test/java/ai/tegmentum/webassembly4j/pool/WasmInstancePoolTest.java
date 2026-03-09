package ai.tegmentum.webassembly4j.pool;

import ai.tegmentum.webassembly4j.api.Component;
import ai.tegmentum.webassembly4j.api.Engine;
import ai.tegmentum.webassembly4j.api.EngineCapabilities;
import ai.tegmentum.webassembly4j.api.EngineInfo;
import ai.tegmentum.webassembly4j.api.Function;
import ai.tegmentum.webassembly4j.api.Global;
import ai.tegmentum.webassembly4j.api.Instance;
import ai.tegmentum.webassembly4j.api.LinkingContext;
import ai.tegmentum.webassembly4j.api.Memory;
import ai.tegmentum.webassembly4j.api.Module;
import ai.tegmentum.webassembly4j.api.Table;
import ai.tegmentum.webassembly4j.api.ValueType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WasmInstancePoolTest {

    private WasmInstancePool pool;

    @AfterEach
    void tearDown() {
        if (pool != null && !pool.isClosed()) {
            pool.close();
        }
    }

    @Test
    void borrowAndReturn() {
        pool = createPool(PoolConfig.builder().maxSize(4).build());

        PooledInstance inst = pool.borrow();
        assertNotNull(inst);
        assertNotNull(inst.function("add"));
        assertEquals(1, pool.totalCreated());

        inst.close(); // returns to pool
        assertEquals(1, pool.availableCount());
    }

    @Test
    void borrowReusesReturnedInstance() {
        pool = createPool(PoolConfig.builder().maxSize(4).build());

        PooledInstance inst1 = pool.borrow();
        inst1.close();

        PooledInstance inst2 = pool.borrow();
        inst2.close();

        assertEquals(1, pool.totalCreated());
    }

    @Test
    void preWarmMinSize() {
        pool = createPool(PoolConfig.builder().minSize(3).maxSize(8).build());

        assertEquals(3, pool.availableCount());
        assertEquals(3, pool.totalCreated());
    }

    @Test
    void borrowCreatesUpToMax() {
        pool = createPool(PoolConfig.builder().maxSize(2).borrowTimeoutMillis(100).build());

        PooledInstance inst1 = pool.borrow();
        PooledInstance inst2 = pool.borrow();

        assertEquals(2, pool.totalCreated());
        assertEquals(0, pool.availableCount());

        // Third borrow should time out
        assertThrows(PoolExhaustedException.class, () -> pool.borrow());

        inst1.close();
        inst2.close();
    }

    @Test
    void closedPoolRejectsBorrow() {
        pool = createPool(PoolConfig.defaults());
        pool.close();

        assertTrue(pool.isClosed());
        assertThrows(IllegalStateException.class, () -> pool.borrow());
    }

    @Test
    void returnedInstanceRejectsUse() {
        pool = createPool(PoolConfig.defaults());
        PooledInstance inst = pool.borrow();
        inst.close();

        assertThrows(IllegalStateException.class, () -> inst.function("add"));
    }

    @Test
    void doubleCloseIsIdempotent() {
        pool = createPool(PoolConfig.defaults());
        PooledInstance inst = pool.borrow();
        inst.close();
        inst.close(); // should not throw

        assertEquals(1, pool.availableCount());
    }

    @Test
    void concurrentBorrowAndReturn() throws InterruptedException {
        pool = createPool(PoolConfig.builder().minSize(2).maxSize(4).build());
        int threadCount = 8;
        int iterations = 50;
        AtomicInteger successCount = new AtomicInteger(0);
        CountDownLatch latch = new CountDownLatch(threadCount);
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);

        for (int t = 0; t < threadCount; t++) {
            executor.submit(() -> {
                try {
                    for (int i = 0; i < iterations; i++) {
                        try (PooledInstance inst = pool.borrow()) {
                            Function add = inst.function("add").orElse(null);
                            if (add != null) {
                                Object result = add.invoke(1, 2);
                                if (((Number) result).intValue() == 3) {
                                    successCount.incrementAndGet();
                                }
                            }
                        }
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        assertTrue(latch.await(30, TimeUnit.SECONDS));
        executor.shutdown();
        assertEquals(threadCount * iterations, successCount.get());
        assertTrue(pool.totalCreated() <= 4);
    }

    @Test
    void poolConfig() {
        PoolConfig config = PoolConfig.builder().minSize(1).maxSize(4).build();
        pool = createPool(config);
        assertEquals(config, pool.config());
    }

    // ─── Stub implementations ───

    private WasmInstancePool createPool(PoolConfig config) {
        StubEngine engine = new StubEngine();
        StubModule module = new StubModule();
        return WasmInstancePool.create(engine, module, config);
    }

    private static class StubEngine implements Engine {
        @Override public EngineInfo info() { return null; }
        @Override public EngineCapabilities capabilities() { return null; }
        @Override public Module loadModule(byte[] bytes) { return new StubModule(); }
        @Override public Component loadComponent(byte[] bytes) { return null; }
        @Override public <T> Optional<T> extension(Class<T> t) { return Optional.empty(); }
        @Override public <T> Optional<T> unwrap(Class<T> t) { return Optional.empty(); }
        @Override public void close() {}
    }

    private static class StubModule implements Module {
        @Override public Instance instantiate() { return new StubInstance(); }
        @Override public Instance instantiate(LinkingContext ctx) { return new StubInstance(); }
        @Override public void close() {}
    }

    private static class StubInstance implements Instance {
        @Override
        public Optional<Function> function(String name) {
            if ("add".equals(name)) {
                return Optional.of(new StubFunction());
            }
            return Optional.empty();
        }
        @Override public Optional<Memory> memory(String name) { return Optional.empty(); }
        @Override public Optional<Table> table(String name) { return Optional.empty(); }
        @Override public Optional<Global> global(String name) { return Optional.empty(); }
        @Override public <T> Optional<T> unwrap(Class<T> t) { return Optional.empty(); }
    }

    private static class StubFunction implements Function {
        @Override public ValueType[] parameterTypes() {
            return new ValueType[]{ValueType.I32, ValueType.I32};
        }
        @Override public ValueType[] resultTypes() {
            return new ValueType[]{ValueType.I32};
        }
        @Override public Object invoke(Object... args) {
            return ((Number) args[0]).intValue() + ((Number) args[1]).intValue();
        }
    }
}
