package ai.tegmentum.webassembly4j.api.exception;

public class ProviderUnavailableException extends WebAssemblyException {

    public ProviderUnavailableException(String message) {
        super(message);
    }

    public ProviderUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
