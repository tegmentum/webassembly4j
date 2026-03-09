package ai.tegmentum.webassembly4j.pool;

import ai.tegmentum.webassembly4j.api.Function;
import ai.tegmentum.webassembly4j.api.Global;
import ai.tegmentum.webassembly4j.api.Instance;
import ai.tegmentum.webassembly4j.api.Memory;
import ai.tegmentum.webassembly4j.api.Table;

import java.util.Optional;

/**
 * A wrapper around an {@link Instance} that returns it to the pool when closed
 * instead of destroying it.
 *
 * <pre>{@code
 * try (PooledInstance inst = pool.borrow()) {
 *     Function add = inst.function("add").orElseThrow();
 *     int result = ((Number) add.invoke(3, 4)).intValue();
 * } // automatically returned to pool
 * }</pre>
 */
public final class PooledInstance implements Instance, AutoCloseable {

    private final Instance delegate;
    private final WasmInstancePool pool;
    private volatile boolean returned;

    PooledInstance(Instance delegate, WasmInstancePool pool) {
        this.delegate = delegate;
        this.pool = pool;
    }

    @Override
    public Optional<Function> function(String name) {
        checkNotReturned();
        return delegate.function(name);
    }

    @Override
    public Optional<Memory> memory(String name) {
        checkNotReturned();
        return delegate.memory(name);
    }

    @Override
    public Optional<Table> table(String name) {
        checkNotReturned();
        return delegate.table(name);
    }

    @Override
    public Optional<Global> global(String name) {
        checkNotReturned();
        return delegate.global(name);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> Optional<T> unwrap(Class<T> nativeType) {
        checkNotReturned();
        return delegate.unwrap(nativeType);
    }

    /**
     * Returns the instance to the pool instead of destroying it.
     */
    @Override
    public void close() {
        if (!returned) {
            returned = true;
            pool.returnInstance(this);
        }
    }

    Instance delegate() {
        return delegate;
    }

    private void checkNotReturned() {
        if (returned) {
            throw new IllegalStateException("Instance has been returned to the pool");
        }
    }
}
