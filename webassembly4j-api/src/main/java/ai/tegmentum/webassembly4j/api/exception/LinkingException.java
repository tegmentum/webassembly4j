package ai.tegmentum.webassembly4j.api.exception;

public class LinkingException extends WebAssemblyException {

    public LinkingException(String message) {
        super(message);
    }

    public LinkingException(String message, Throwable cause) {
        super(message, cause);
    }
}
