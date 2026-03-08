package ai.tegmentum.webassembly4j.api.exception;

public class ConfigurationException extends WebAssemblyException {

    public ConfigurationException(String message) {
        super(message);
    }

    public ConfigurationException(String message, Throwable cause) {
        super(message, cause);
    }
}
