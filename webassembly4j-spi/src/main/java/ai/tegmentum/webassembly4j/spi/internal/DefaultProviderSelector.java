package ai.tegmentum.webassembly4j.spi.internal;

import ai.tegmentum.webassembly4j.spi.EngineProvider;
import ai.tegmentum.webassembly4j.spi.ProviderContext;
import ai.tegmentum.webassembly4j.spi.ProviderDescriptor;
import ai.tegmentum.webassembly4j.spi.ProviderSelectionResult;
import ai.tegmentum.webassembly4j.spi.ProviderSelector;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class DefaultProviderSelector implements ProviderSelector {

    @Override
    public ProviderSelectionResult select(List<EngineProvider> providers, ProviderContext context) {
        if (providers.isEmpty()) {
            return result(null, "No providers discovered");
        }

        Stream<EngineProvider> stream = providers.stream();

        // Filter by availability
        stream = stream.filter(p -> p.availability().available());

        // Filter by explicit provider ID if requested
        String requestedProviderId = context.requestedProviderId();
        if (requestedProviderId != null && !requestedProviderId.isEmpty()) {
            stream = stream.filter(p ->
                    requestedProviderId.equals(p.descriptor().providerId()));
        }

        // Filter by engine ID if requested
        String requestedEngineId = context.requestedEngineId();
        if (requestedEngineId != null && !requestedEngineId.isEmpty()) {
            stream = stream.filter(p ->
                    requestedEngineId.equals(p.descriptor().engineId()));
        }

        // Filter by Java version compatibility
        int javaVersion = context.currentJavaVersion();
        stream = stream.filter(p -> p.descriptor().minimumJavaVersion() <= javaVersion);

        // Filter by config compatibility
        if (context.config() != null && context.config().engineConfig().isPresent()) {
            stream = stream.filter(p -> p.supports(context.config().engineConfig().get()));
        }

        // Sort by priority descending
        List<EngineProvider> candidates = stream
                .sorted(Comparator.comparingInt(
                        (EngineProvider p) -> p.descriptor().priority()).reversed())
                .collect(Collectors.toList());

        if (candidates.isEmpty()) {
            return result(null, "No compatible provider found for"
                    + (requestedEngineId != null ? " engine=" + requestedEngineId : "")
                    + (requestedProviderId != null ? " provider=" + requestedProviderId : "")
                    + " on Java " + javaVersion);
        }

        // Check for ambiguous ties
        if (candidates.size() > 1) {
            int topPriority = candidates.get(0).descriptor().priority();
            long tieCount = candidates.stream()
                    .filter(p -> p.descriptor().priority() == topPriority)
                    .count();
            if (tieCount > 1) {
                String tied = candidates.stream()
                        .filter(p -> p.descriptor().priority() == topPriority)
                        .map(p -> p.descriptor().providerId())
                        .collect(Collectors.joining(", "));
                return result(null, "Ambiguous provider selection: " + tied
                        + " all have priority " + topPriority);
            }
        }

        EngineProvider selected = candidates.get(0);
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
