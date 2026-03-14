package ai.tegmentum.webassembly4j.api.component.async;

/**
 * RAII guard wrapping an {@link AsyncStream}. Closing the guard releases the
 * underlying stream. Ownership can be transferred via {@link #intoStream()},
 * after which closing this guard is a no-op.
 */
public final class GuardedStream implements AutoCloseable {

    private AsyncStream stream;
    private boolean active;

    public GuardedStream(AsyncStream stream) {
        if (stream == null) {
            throw new NullPointerException("stream");
        }
        this.stream = stream;
        this.active = true;
    }

    /**
     * Returns the underlying stream without transferring ownership.
     *
     * @throws IllegalStateException if ownership has been transferred
     */
    public AsyncStream getStream() {
        if (!active) {
            throw new IllegalStateException("Ownership has been transferred");
        }
        return stream;
    }

    /**
     * Transfers ownership of the underlying stream to the caller.
     * After this call, {@link #close()} becomes a no-op.
     *
     * @return the underlying stream
     * @throws IllegalStateException if ownership has already been transferred
     */
    public AsyncStream intoStream() {
        if (!active) {
            throw new IllegalStateException("Ownership has already been transferred");
        }
        active = false;
        AsyncStream result = stream;
        stream = null;
        return result;
    }

    /**
     * Returns whether this guard still owns the stream.
     */
    public boolean isActive() {
        return active;
    }

    /**
     * Closes the underlying stream if ownership has not been transferred.
     * Idempotent — safe to call multiple times.
     */
    @Override
    public void close() {
        if (active && stream != null) {
            stream.close();
            active = false;
            stream = null;
        }
    }
}
