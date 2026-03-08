package ai.tegmentum.webassembly4j.api.exception;

public class TrapException extends ExecutionException {

    public TrapException(String message) {
        super(message);
    }

    public TrapException(String message, Throwable cause) {
        super(message, cause);
    }
}
