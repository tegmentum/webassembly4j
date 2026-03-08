package ai.tegmentum.webassembly4j.api;

public interface EngineInfo {

    String engineId();

    String providerId();

    String providerVersion();

    String engineVersion();

    int minimumJavaVersion();
}
