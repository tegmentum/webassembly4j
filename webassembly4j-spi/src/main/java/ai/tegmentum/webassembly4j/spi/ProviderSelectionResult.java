package ai.tegmentum.webassembly4j.spi;

import java.util.Optional;

public interface ProviderSelectionResult {

    Optional<EngineProvider> selectedProvider();

    String explanation();
}
