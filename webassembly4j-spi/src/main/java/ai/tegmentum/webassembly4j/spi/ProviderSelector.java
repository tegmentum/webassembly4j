package ai.tegmentum.webassembly4j.spi;

import java.util.List;

public interface ProviderSelector {

    ProviderSelectionResult select(List<EngineProvider> providers, ProviderContext context);
}
