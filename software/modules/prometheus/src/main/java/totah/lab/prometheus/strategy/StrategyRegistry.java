package totah.lab.prometheus.strategy;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Immutable registry of strategy providers keyed by stable strategy id. */
public final class StrategyRegistry {

    private final Map<String, ParameterizationStrategy> byId;

    public StrategyRegistry(List<? extends ParameterizationStrategy> strategies) {
        Objects.requireNonNull(strategies, "strategies");
        Map<String, ParameterizationStrategy> indexed = new LinkedHashMap<>();
        for (ParameterizationStrategy strategy : strategies) {
            Objects.requireNonNull(strategy, "strategy");
            String id = strategy.descriptor().strategyId();
            if (indexed.putIfAbsent(id, strategy) != null) {
                throw new IllegalArgumentException("duplicate strategy id: " + id);
            }
        }
        byId = Map.copyOf(indexed);
    }

    public static StrategyRegistry establishedSkeletons() {
        return new StrategyRegistry(List.of(
                new Gaff2BaselineStrategy(),
                new RespStrategy(),
                new ModifiedSeminarioStrategy(),
                new QForceStyleStrategy(),
                new QubeKitStrategy(),
                new ForceBalanceStrategy(),
                new TorsionFitStrategy()));
    }

    public Optional<ParameterizationStrategy> find(String strategyId) {
        Objects.requireNonNull(strategyId, "strategyId");
        return Optional.ofNullable(byId.get(strategyId));
    }

    public List<ParameterizationStrategy> strategies() {
        return List.copyOf(byId.values());
    }
}
