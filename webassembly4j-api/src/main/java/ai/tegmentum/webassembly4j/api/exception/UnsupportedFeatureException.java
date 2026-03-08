package ai.tegmentum.webassembly4j.api.exception;

public class UnsupportedFeatureException extends WebAssemblyException {

    public UnsupportedFeatureException(String message) {
        super(message);
    }

    public UnsupportedFeatureException(String message, Throwable cause) {
        super(message, cause);
    }
}
