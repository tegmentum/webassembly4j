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
}
