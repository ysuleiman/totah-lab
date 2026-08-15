package totah.lab.prometheus.numerics;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Immutable DAG definition. Each evaluation creates a state-scoped cache; no
 * intermediate may leak across different NumericalStateIdentity values.
 */
public final class NumericalComputationGraph {
    private final Map<String, NumericalNode<?>> nodes;

    public NumericalComputationGraph(List<NumericalNode<?>> nodes) {
        Objects.requireNonNull(nodes, "nodes");
        Map<String, NumericalNode<?>> indexed = new LinkedHashMap<>();
        for (NumericalNode<?> node : nodes) {
            if (indexed.putIfAbsent(node.nodeId(), node) != null) {
                throw new IllegalArgumentException("duplicate node id: " + node.nodeId());
            }
        }
        indexed.values().forEach(node -> node.dependencyIds().forEach(dependency -> {
            if (!indexed.containsKey(dependency)) throw new IllegalArgumentException("unknown dependency: " + dependency);
        }));
        detectCycles(indexed);
        this.nodes = Map.copyOf(indexed);
    }

    public Evaluation evaluate(Set<String> targetNodeIds, NumericalStateIdentity state,
            long memoryBudgetBytes, Map<String, Integer> expectedReuseCounts,
            IntermediateRetentionPolicy retentionPolicy) {
        Objects.requireNonNull(targetNodeIds, "targetNodeIds"); Objects.requireNonNull(state, "state");
        Objects.requireNonNull(expectedReuseCounts, "expectedReuseCounts");
        Objects.requireNonNull(retentionPolicy, "retentionPolicy");
        EvaluationContext context = new EvaluationContext(state, memoryBudgetBytes,
                expectedReuseCounts, retentionPolicy);
        Map<String, IntermediateValue> results = new LinkedHashMap<>();
        for (String target : targetNodeIds) results.put(target, resolve(target, context));
        return new Evaluation(state, results, context.evaluationCounts, context.retainedBytes);
    }

    private IntermediateValue resolve(String nodeId, EvaluationContext context) {
        NumericalNode<?> node = nodes.get(nodeId);
        if (node == null) throw new IllegalArgumentException("unknown target node: " + nodeId);
        IntermediateValue cached = context.cache.get(nodeId);
        if (cached != null) return cached;
        Map<String, IntermediateValue> dependencies = new LinkedHashMap<>();
        node.dependencyIds().forEach(id -> dependencies.put(id, resolve(id, context)));
        IntermediateValue value = node.computation().evaluate(new ResolvedDependencies(dependencies));
        context.evaluationCounts.merge(nodeId, 1, Integer::sum);
        if (shouldRetain(node, context)) {
            context.cache.put(nodeId, value); context.retainedBytes += node.estimatedRetainedBytes();
        }
        return value;
    }

    private static boolean shouldRetain(NumericalNode<?> node, EvaluationContext context) {
        return switch (node.reusePolicy()) {
            case MANDATORY_REUSE -> true;
            case RECOMPUTE_IF_CHEAPER -> false;
            case CACHE_IF_BENEFICIAL -> context.retentionPolicy.retain(node,
                    new IntermediateRetentionPolicy.RetentionContext(context.memoryBudgetBytes,
                            context.retainedBytes, context.expectedReuseCounts.getOrDefault(node.nodeId(), 0)));
        };
    }

    private static void detectCycles(Map<String, NumericalNode<?>> nodes) {
        Set<String> complete = new HashSet<>(); Set<String> active = new HashSet<>();
        for (String id : nodes.keySet()) visit(id, nodes, complete, active);
    }

    private static void visit(String id, Map<String, NumericalNode<?>> nodes,
            Set<String> complete, Set<String> active) {
        if (complete.contains(id)) return;
        if (!active.add(id)) throw new IllegalArgumentException("cycle detected at node: " + id);
        nodes.get(id).dependencyIds().forEach(dependency -> visit(dependency, nodes, complete, active));
        active.remove(id); complete.add(id);
    }

    public record Evaluation(NumericalStateIdentity state, Map<String, IntermediateValue> values,
            Map<String, Integer> evaluationCounts, long retainedBytes) {
        public Evaluation {
            Objects.requireNonNull(state, "state"); values = Map.copyOf(values);
            evaluationCounts = Map.copyOf(evaluationCounts);
        }
    }

    private static final class EvaluationContext {
        final NumericalStateIdentity state; final long memoryBudgetBytes;
        final Map<String, Integer> expectedReuseCounts; final IntermediateRetentionPolicy retentionPolicy;
        final Map<String, IntermediateValue> cache = new HashMap<>();
        final Map<String, Integer> evaluationCounts = new HashMap<>(); long retainedBytes;
        EvaluationContext(NumericalStateIdentity state, long memoryBudgetBytes,
                Map<String, Integer> expectedReuseCounts, IntermediateRetentionPolicy retentionPolicy) {
            if (memoryBudgetBytes < 0) throw new IllegalArgumentException("memoryBudgetBytes must be non-negative");
            this.state=state; this.memoryBudgetBytes=memoryBudgetBytes;
            this.expectedReuseCounts=Map.copyOf(expectedReuseCounts); this.retentionPolicy=retentionPolicy;
        }
    }
}
