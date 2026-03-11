package ai.tegmentum.webassembly4j.api.gc;

import ai.tegmentum.webassembly4j.api.exception.WebAssemblyException;

/**
 * Exception thrown for GC-related errors such as type mismatches,
 * invalid field access, or unsupported operations.
 */
public class GcException extends WebAssemblyException {

    public GcException(String message) {
        super(message);
    }

    public GcException(String message, Throwable cause) {
        super(message, cause);
    }
}
