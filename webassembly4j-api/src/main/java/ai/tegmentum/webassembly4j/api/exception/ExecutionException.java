package ai.tegmentum.webassembly4j.api.exception;

public class ExecutionException extends WebAssemblyException {

    public ExecutionException(String message) {
        super(message);
    }

    public ExecutionException(String message, Throwable cause) {
        super(message, cause);
    }
}
