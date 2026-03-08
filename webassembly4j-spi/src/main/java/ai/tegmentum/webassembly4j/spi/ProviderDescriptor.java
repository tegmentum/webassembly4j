package ai.tegmentum.webassembly4j.spi;

import java.util.Set;

public interface ProviderDescriptor {

    String engineId();

    String providerId();

    String version();

    int minimumJavaVersion();

    Set<String> tags();

    int priority();
}
