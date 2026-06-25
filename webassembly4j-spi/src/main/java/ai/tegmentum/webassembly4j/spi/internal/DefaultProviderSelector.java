package ai.tegmentum.webassembly4j.spi.internal;

import ai.tegmentum.webassembly4j.spi.EngineProvider;
import ai.tegmentum.webassembly4j.spi.ProviderAvailability;
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

        // Single pass: filter + track highest priority candidates. Collect a
        // human-readable reason for every rejected provider so that a failed
        // selection can explain *why* each candidate was excluded.
        int maxPriority = Integer.MIN_VALUE;
        List<EngineProvider> topCandidates = new ArrayList<>();
        List<String> rejections = new ArrayList<>();

        for (EngineProvider p : providers) {
            String id = p.descriptor().providerId();

            ProviderAvailability availability = p.availability();
            if (!availability.available()) {
                rejections.add(id + ": " + availability.message());
                continue;
            }
            if (filterByProvider && !requestedProviderId.equals(p.descriptor().providerId())) {
                rejections.add(id + ": provider id does not match requested '"
                        + requestedProviderId + "'");
                continue;
            }
            if (filterByEngine && !requestedEngineId.equals(p.descriptor().engineId())) {
                rejections.add(id + ": engine id '" + p.descriptor().engineId()
                        + "' does not match requested '" + requestedEngineId + "'");
                continue;
            }
            if (p.descriptor().minimumJavaVersion() > javaVersion) {
                rejections.add(id + ": requires Java " + p.descriptor().minimumJavaVersion()
                        + " but running on Java " + javaVersion);
                continue;
            }
            if (filterByConfig && !p.supports(context.config().engineConfig().get())) {
                rejections.add(id + ": does not support the supplied engine configuration");
                continue;
            }

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
            StringBuilder explanation = new StringBuilder("No compatible provider found for");
            if (filterByEngine) explanation.append(" engine=").append(requestedEngineId);
            if (filterByProvider) explanation.append(" provider=").append(requestedProviderId);
            explanation.append(" on Java ").append(javaVersion);
            if (!rejections.isEmpty()) {
                explanation.append(". Providers considered: ")
                        .append(String.join("; ", rejections));
            }
            return result(null, explanation.toString());
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
