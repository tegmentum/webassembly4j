package ai.tegmentum.webassembly4j.api.component.async;

/**
 * RAII guard wrapping an {@link AsyncFuture}. Closing the guard releases the
 * underlying future. Ownership can be transferred via {@link #intoFuture()},
 * after which closing this guard is a no-op.
 */
public final class GuardedFuture implements AutoCloseable {

    private AsyncFuture future;
    private boolean active;

    public GuardedFuture(AsyncFuture future) {
        if (future == null) {
            throw new NullPointerException("future");
        }
        this.future = future;
        this.active = true;
    }

    /**
     * Returns the underlying future without transferring ownership.
     *
     * @throws IllegalStateException if ownership has been transferred
     */
    public AsyncFuture getFuture() {
        if (!active) {
            throw new IllegalStateException("Ownership has been transferred");
        }
        return future;
    }

    /**
     * Transfers ownership of the underlying future to the caller.
     * After this call, {@link #close()} becomes a no-op.
     *
     * @return the underlying future
     * @throws IllegalStateException if ownership has already been transferred
     */
    public AsyncFuture intoFuture() {
        if (!active) {
            throw new IllegalStateException("Ownership has already been transferred");
        }
        active = false;
        AsyncFuture result = future;
        future = null;
        return result;
    }

    /**
     * Returns whether this guard still owns the future.
     */
    public boolean isActive() {
        return active;
    }

    /**
     * Closes the underlying future if ownership has not been transferred.
     * Idempotent — safe to call multiple times.
     */
    @Override
    public void close() {
        if (active && future != null) {
            future.close();
            active = false;
            future = null;
        }
    }
}
