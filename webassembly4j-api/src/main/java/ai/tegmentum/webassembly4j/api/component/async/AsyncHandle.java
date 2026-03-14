package ai.tegmentum.webassembly4j.api.component.async;

/**
 * Base interface for async component model handles (futures, streams, error contexts).
 * Handles are {@link AutoCloseable} to ensure proper resource cleanup.
 */
public interface AsyncHandle extends AutoCloseable {

    /**
     * Returns whether this handle is still valid (has not been closed or transferred).
     */
    boolean isValid();

    @Override
    void close();
}
