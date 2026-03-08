package ai.tegmentum.webassembly4j.api.exception;

public class WebAssemblyException extends RuntimeException {

    public WebAssemblyException(String message) {
        super(message);
    }

    public WebAssemblyException(String message, Throwable cause) {
        super(message, cause);
    }
}
