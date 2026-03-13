package ai.tegmentum.webassembly4j.api.exception;

import ai.tegmentum.webassembly4j.api.debug.WasmBacktrace;

import java.util.Optional;

public class TrapException extends ExecutionException {

    private final WasmBacktrace backtrace;

    public TrapException(String message) {
        super(message);
        this.backtrace = null;
    }

    public TrapException(String message, Throwable cause) {
        super(message, cause);
        this.backtrace = null;
    }

    public TrapException(String message, WasmBacktrace backtrace) {
        super(message);
        this.backtrace = backtrace;
    }

    public TrapException(String message, Throwable cause, WasmBacktrace backtrace) {
        super(message, cause);
        this.backtrace = backtrace;
    }

    /**
     * Returns the WebAssembly call stack at the point of the trap.
     */
    public Optional<WasmBacktrace> backtrace() {
        return Optional.ofNullable(backtrace);
    }
}
