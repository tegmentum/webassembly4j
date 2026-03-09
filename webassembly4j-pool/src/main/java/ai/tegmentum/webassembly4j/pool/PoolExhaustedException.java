package ai.tegmentum.webassembly4j.pool;

/**
 * Thrown when a pool cannot provide an instance within the configured timeout.
 */
public class PoolExhaustedException extends RuntimeException {

    public PoolExhaustedException(String message) {
        super(message);
    }

    public PoolExhaustedException(String message, Throwable cause) {
        super(message, cause);
    }
}
