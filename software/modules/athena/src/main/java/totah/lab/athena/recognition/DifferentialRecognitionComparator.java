package totah.lab.athena.recognition;

import totah.lab.athena.interaction.InteractionType;
import totah.lab.athena.surface.differential.ExplicitResidueCorrespondence;
import totah.lab.gaia.structure.ResidueId;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Deterministic comparator that reports evidence dimensions separately. */
public final class DifferentialRecognitionComparator {

    public DifferentialRecognition compare(RecognitionGraph source,
            RecognitionGraph target,
            ExplicitResidueCorrespondence correspondence,
            RecognitionComparisonPolicy policy) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(correspondence, "correspondence");
        Objects.requireNonNull(policy, "policy");

        List<DifferentialRecognition.EdgeComparison> comparisons = new ArrayList<>();
        EnumMap<CrossParalogEdgeState, Integer> counts = new EnumMap<>(CrossParalogEdgeState.class);
        for (RecognitionEdge sourceEdge : source.edges()) {
            Comparison classified = classify(sourceEdge, target, correspondence, policy);
            counts.merge(classified.state(), 1, Integer::sum);
            comparisons.add(new DifferentialRecognition.EdgeComparison(sourceEdge,
                    classified.target(), classified.state(), policy.roleOf(sourceEdge),
                    sourceEdge.engagesDifferentialEnvironment(), classified.reason()));
        }

        Set<MappedKey> sourceKeys = mappedKeys(source, correspondence);
        Set<MappedKey> targetKeys = nativeKeys(target);
        Set<MappedKey> intersection = new HashSet<>(sourceKeys);
        intersection.retainAll(targetKeys);
        Set<MappedKey> union = new HashSet<>(sourceKeys);
        union.addAll(targetKeys);
        double jaccard = union.isEmpty() ? 1.0 : (double) intersection.size() / union.size();
        int changedDifferential = (int) comparisons.stream()
                .filter(row -> row.state() != CrossParalogEdgeState.PRESERVED)
                .filter(DifferentialRecognition.EdgeComparison::differentialEnvironment).count();
        return new DifferentialRecognition(source, target, comparisons,
                new DifferentialRecognition.GraphMetrics(source.edges().size(), target.edges().size(),
                        intersection.size(), union.size(), jaccard, counts, changedDifferential));
    }

    private Comparison classify(RecognitionEdge sourceEdge, RecognitionGraph target,
            ExplicitResidueCorrespondence correspondence, RecognitionComparisonPolicy policy) {
        if (sourceEdge.quality() != EvidenceQuality.ADEQUATE) {
            return unavailable("source edge evidence is not adequate");
        }
        Optional<ResidueId> mapped = correspondence.subjectOf(sourceEdge.environmentResidue());
        if (mapped.isEmpty()) return unavailable("residue correspondence unavailable");
        if (policy.explicitlyIncompatible(sourceEdge.ligandFeature().id(), mapped.get(),
                sourceEdge.interactionType())) {
            return new Comparison(Optional.empty(), CrossParalogEdgeState.INCOMPATIBLE,
                    "caller supplied explicit incompatible-constraint evidence");
        }
        Optional<RecognitionEdge> exact = target.edges().stream().filter(edge ->
                sameFeature(sourceEdge, edge) && edge.environmentResidue().equals(mapped.get())
                        && edge.interactionType() == sourceEdge.interactionType())
                .sorted(java.util.Comparator.comparing(RecognitionEdge::id)).findFirst();
        if (exact.isPresent()) {
            if (exact.get().quality() != EvidenceQuality.ADEQUATE) {
                return unavailable("corresponding target edge evidence is not adequate");
            }
            return new Comparison(exact, CrossParalogEdgeState.PRESERVED,
                    "same ligand feature, corresponding residue, and interaction type");
        }
        Optional<RecognitionEdge> correspondingAlternative = target.edges().stream()
                .filter(edge ->
                sameFeature(sourceEdge, edge) && edge.environmentResidue().equals(mapped.get()))
                .sorted(java.util.Comparator.comparing(RecognitionEdge::id)).findFirst();
        if (correspondingAlternative.isPresent()) {
            if (correspondingAlternative.get().quality() != EvidenceQuality.ADEQUATE) {
                return unavailable("corresponding substitute evidence is not adequate");
            }
            return new Comparison(correspondingAlternative, CrossParalogEdgeState.SUBSTITUTED,
                    "same ligand feature and corresponding environment use a different interaction type");
        }
        Optional<RecognitionEdge> route = target.edges().stream()
                .filter(edge -> sameFeature(sourceEdge, edge))
                .sorted(java.util.Comparator.comparing(RecognitionEdge::id)).findFirst();
        if (route.isPresent()) {
            if (route.get().quality() != EvidenceQuality.ADEQUATE) {
                return unavailable("rerouted target evidence is not adequate");
            }
            return new Comparison(route, CrossParalogEdgeState.REROUTED,
                    "same ligand feature engages a different residue route");
        }
        if (target.quality() != EvidenceQuality.ADEQUATE) {
            return unavailable("target graph is not adequate for an absence classification");
        }
        if (policy.roleOf(sourceEdge) == InteractionRole.DEFINING) {
            return new Comparison(Optional.empty(), CrossParalogEdgeState.LOST,
                    "adequate target evidence contains no substitute for a defining edge");
        }
        return new Comparison(Optional.empty(), CrossParalogEdgeState.UNOBSERVED,
                "edge was not observed but is not canonically defining or explicitly impossible");
    }

    private static boolean sameFeature(RecognitionEdge first, RecognitionEdge second) {
        return first.ligandFeature().id().equals(second.ligandFeature().id());
    }

    private static Set<MappedKey> mappedKeys(RecognitionGraph graph,
            ExplicitResidueCorrespondence correspondence) {
        Set<MappedKey> keys = new HashSet<>();
        graph.edges().stream().filter(edge -> edge.quality() == EvidenceQuality.ADEQUATE)
                .forEach(edge -> correspondence.subjectOf(edge.environmentResidue())
                .ifPresent(mapped -> keys.add(new MappedKey(edge.ligandFeature().id(), mapped,
                        edge.interactionType()))));
        return keys;
    }

    private static Set<MappedKey> nativeKeys(RecognitionGraph graph) {
        Set<MappedKey> keys = new HashSet<>();
        graph.edges().stream().filter(edge -> edge.quality() == EvidenceQuality.ADEQUATE)
                .forEach(edge -> keys.add(new MappedKey(edge.ligandFeature().id(),
                edge.environmentResidue(), edge.interactionType())));
        return keys;
    }

    private static Comparison unavailable(String reason) {
        return new Comparison(Optional.empty(), CrossParalogEdgeState.UNAVAILABLE, reason);
    }

    private record MappedKey(String feature, ResidueId residue, InteractionType type) { }
    private record Comparison(Optional<RecognitionEdge> target, CrossParalogEdgeState state,
                              String reason) { }
}
