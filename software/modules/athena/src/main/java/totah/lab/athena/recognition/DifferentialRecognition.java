package totah.lab.athena.recognition;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalDouble;

/** Directional, unweighted comparison of two recognition graphs. */
public record DifferentialRecognition(
        RecognitionGraph source,
        RecognitionGraph target,
        List<EdgeComparison> edges,
        GraphMetrics metrics) {

    public DifferentialRecognition {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(target, "target");
        edges = List.copyOf(Objects.requireNonNull(edges, "edges"));
        Objects.requireNonNull(metrics, "metrics");
    }

    public List<EdgeComparison> edges(CrossParalogEdgeState state) {
        Objects.requireNonNull(state, "state");
        return edges.stream().filter(edge -> edge.state() == state).toList();
    }

    /** Preserved, non-defining edges outside a residue-identity differential. */
    public List<EdgeComparison> preservedBackgroundInteractions() {
        return edges.stream().filter(edge -> edge.state() == CrossParalogEdgeState.PRESERVED)
                .filter(edge -> edge.role() != InteractionRole.DEFINING)
                .filter(edge -> !edge.sourceEdge().differentialSurface()
                        .map(score -> score.rup() > 0.0).orElse(false)).toList();
    }

    /** Changed edges grouped by the stable ligand feature that was rerouted. */
    public Map<String, List<EdgeComparison>> reroutedSubgraphs() {
        return edges(CrossParalogEdgeState.REROUTED).stream().collect(
                java.util.stream.Collectors.groupingBy(
                        edge -> edge.sourceEdge().ligandFeature().id(),
                        java.util.LinkedHashMap::new,
                        java.util.stream.Collectors.toUnmodifiableList()));
    }

    /** Defined only when at least one edge has a canonical DEFINING label. */
    public OptionalDouble definingEdgePreservationFraction() {
        long defining = edges.stream().filter(edge -> edge.role() == InteractionRole.DEFINING).count();
        if (defining == 0) return OptionalDouble.empty();
        long preserved = edges.stream().filter(edge -> edge.role() == InteractionRole.DEFINING)
                .filter(edge -> edge.state() == CrossParalogEdgeState.PRESERVED).count();
        return OptionalDouble.of((double) preserved / defining);
    }

    public record EdgeComparison(RecognitionEdge sourceEdge,
            java.util.Optional<RecognitionEdge> targetEdge,
            CrossParalogEdgeState state,
            InteractionRole role,
            boolean differentialEnvironment,
            String reason) {
        public EdgeComparison {
            Objects.requireNonNull(sourceEdge, "sourceEdge");
            targetEdge = Objects.requireNonNull(targetEdge, "targetEdge");
            Objects.requireNonNull(state, "state");
            Objects.requireNonNull(role, "role");
            if (reason == null || reason.isBlank()) throw new IllegalArgumentException("reason required");
        }
    }

    public record GraphMetrics(int sourceEdgeCount, int targetEdgeCount,
            int typedIntersection, int typedUnion, double typedJaccard,
            Map<CrossParalogEdgeState, Integer> stateCounts,
            int changedDifferentialEnvironmentCount) {
        public GraphMetrics {
            if (sourceEdgeCount < 0 || targetEdgeCount < 0 || typedIntersection < 0
                    || typedUnion < 0 || changedDifferentialEnvironmentCount < 0
                    || !Double.isFinite(typedJaccard) || typedJaccard < 0 || typedJaccard > 1) {
                throw new IllegalArgumentException("invalid graph metrics");
            }
            EnumMap<CrossParalogEdgeState, Integer> copy =
                    new EnumMap<>(CrossParalogEdgeState.class);
            copy.putAll(Objects.requireNonNull(stateCounts, "stateCounts"));
            for (CrossParalogEdgeState state : CrossParalogEdgeState.values()) {
                copy.putIfAbsent(state, 0);
                if (copy.get(state) < 0) throw new IllegalArgumentException("negative state count");
            }
            stateCounts = Map.copyOf(copy);
        }
    }
}
