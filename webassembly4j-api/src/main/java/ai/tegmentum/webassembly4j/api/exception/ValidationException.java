package ai.tegmentum.webassembly4j.api.exception;

public class ValidationException extends WebAssemblyException {

    public ValidationException(String message) {
        super(message);
    }

    public ValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}
