package ai.tegmentum.webassembly4j.api;

public interface EngineCapabilities {

    boolean supportsCoreModules();

    boolean supportsComponents();

    boolean supportsWasi();

    boolean supportsFuel();

    boolean supportsEpochInterruption();

    boolean supportsThreads();

    boolean supportsGc();

    boolean supportsReferenceTypes();

    boolean supportsMultiMemory();

    boolean supportsNativeInterop();

    /**
     * Returns whether this engine supports WASI HTTP.
     */
    default boolean supportsWasiHttp() {
        return false;
    }

    /**
     * Returns whether this engine supports async component model execution.
     */
    default boolean supportsAsyncComponents() {
        return false;
    }
}
