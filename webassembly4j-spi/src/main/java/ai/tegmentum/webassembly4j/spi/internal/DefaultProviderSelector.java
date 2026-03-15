package ai.tegmentum.webassembly4j.spi.internal;

import ai.tegmentum.webassembly4j.spi.EngineProvider;
import ai.tegmentum.webassembly4j.spi.ProviderContext;
import ai.tegmentum.webassembly4j.spi.ProviderSelectionResult;
import ai.tegmentum.webassembly4j.spi.ProviderSelector;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class DefaultProviderSelector implements ProviderSelector {

    @Override
    public ProviderSelectionResult select(List<EngineProvider> providers, ProviderContext context) {
        if (providers.isEmpty()) {
            return result(null, "No providers discovered");
        }

        String requestedProviderId = context.requestedProviderId();
        boolean filterByProvider = requestedProviderId != null && !requestedProviderId.isEmpty();

        String requestedEngineId = context.requestedEngineId();
        boolean filterByEngine = requestedEngineId != null && !requestedEngineId.isEmpty();

        int javaVersion = context.currentJavaVersion();

        boolean filterByConfig = context.config() != null
                && context.config().engineConfig().isPresent();

        // Single pass: filter + track highest priority candidates
        int maxPriority = Integer.MIN_VALUE;
        List<EngineProvider> topCandidates = new ArrayList<>();

        for (EngineProvider p : providers) {
            if (!p.availability().available()) continue;
            if (filterByProvider && !requestedProviderId.equals(p.descriptor().providerId())) continue;
            if (filterByEngine && !requestedEngineId.equals(p.descriptor().engineId())) continue;
            if (p.descriptor().minimumJavaVersion() > javaVersion) continue;
            if (filterByConfig && !p.supports(context.config().engineConfig().get())) continue;

            int priority = p.descriptor().priority();
            if (priority > maxPriority) {
                maxPriority = priority;
                topCandidates.clear();
                topCandidates.add(p);
            } else if (priority == maxPriority) {
                topCandidates.add(p);
            }
        }

        if (topCandidates.isEmpty()) {
            return result(null, "No compatible provider found for"
                    + (filterByEngine ? " engine=" + requestedEngineId : "")
                    + (filterByProvider ? " provider=" + requestedProviderId : "")
                    + " on Java " + javaVersion);
        }

        if (topCandidates.size() > 1) {
            StringBuilder tied = new StringBuilder();
            for (int i = 0; i < topCandidates.size(); i++) {
                if (i > 0) tied.append(", ");
                tied.append(topCandidates.get(i).descriptor().providerId());
            }
            return result(null, "Ambiguous provider selection: " + tied
                    + " all have priority " + maxPriority);
        }

        EngineProvider selected = topCandidates.get(0);
        return result(selected, "Selected provider: " + selected.descriptor().providerId());
    }

    private static ProviderSelectionResult result(EngineProvider provider, String explanation) {
        return new ProviderSelectionResult() {
            @Override
            public Optional<EngineProvider> selectedProvider() {
                return Optional.ofNullable(provider);
            }

            @Override
            public String explanation() {
                return explanation;
            }
        };
    }
}
