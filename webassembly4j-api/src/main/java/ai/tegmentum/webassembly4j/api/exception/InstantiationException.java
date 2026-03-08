package ai.tegmentum.webassembly4j.api.exception;

public class InstantiationException extends WebAssemblyException {

    public InstantiationException(String message) {
        super(message);
    }

    public InstantiationException(String message, Throwable cause) {
        super(message, cause);
    }
}
