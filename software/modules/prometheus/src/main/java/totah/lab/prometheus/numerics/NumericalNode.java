package totah.lab.prometheus.numerics;

import java.util.List;
import java.util.Objects;

/** One immutable node in a numerical dependency graph. */
public record NumericalNode<T extends IntermediateValue>(
        String nodeId,
        List<String> dependencyIds,
        ReusePolicy reusePolicy,
        double estimatedComputeCost,
        long estimatedRetainedBytes,
        NodeComputation<T> computation) {

    public NumericalNode {
        Objects.requireNonNull(nodeId, "nodeId");
        if (nodeId.isBlank()) throw new IllegalArgumentException("nodeId must be non-blank");
        dependencyIds = List.copyOf(Objects.requireNonNull(dependencyIds, "dependencyIds"));
        Objects.requireNonNull(reusePolicy, "reusePolicy");
        if (!Double.isFinite(estimatedComputeCost) || estimatedComputeCost < 0.0) {
            throw new IllegalArgumentException("estimatedComputeCost must be finite and non-negative");
        }
        if (estimatedRetainedBytes < 0) throw new IllegalArgumentException("estimatedRetainedBytes must be non-negative");
        Objects.requireNonNull(computation, "computation");
    }

    @FunctionalInterface
    public interface NodeComputation<T extends IntermediateValue> {
        T evaluate(ResolvedDependencies dependencies);
    }
}
