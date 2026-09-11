package totah.lab.athena.recognition;

import totah.lab.athena.surface.differential.ExplicitResidueCorrespondence;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Deterministic conservative one-to-one assignment; tied best candidates remain ambiguous. */
public final class RecognitionEdgeAssigner {
    public RecognitionEdgeAssignment assign(RecognitionGraph source, RecognitionGraph target,
            ExplicitResidueCorrespondence residues, RecognitionEdgeAssignmentPolicy policy) {
        Objects.requireNonNull(source); Objects.requireNonNull(target); Objects.requireNonNull(residues);
        Objects.requireNonNull(policy);
        List<RecognitionEdgeAssignment.Match> matches = new ArrayList<>();
        List<RecognitionEdgeAssignment.Ambiguity> ambiguities = new ArrayList<>();
        List<RecognitionEdge> adequateSource = source.edges().stream()
                .filter(e -> e.quality() == EvidenceQuality.ADEQUATE)
                .sorted(Comparator.comparing(RecognitionEdge::id)).toList();
        Map<RecognitionEdge, List<Candidate>> ranked = new java.util.LinkedHashMap<>();
        for (RecognitionEdge edge : adequateSource) {
            List<Candidate> candidates = candidates(edge, target, residues, policy).stream().sorted().toList();
            ranked.put(edge, candidates);
        }
        Map<RecognitionEdge, Integer> nextCandidate = new java.util.HashMap<>();
        Map<String, Preferred> heldByTarget = new java.util.HashMap<>();
        Set<String> blockedTargets = new HashSet<>();
        ArrayDeque<RecognitionEdge> pending = new ArrayDeque<>();
        for (RecognitionEdge edge : adequateSource) {
            List<Candidate> candidates = ranked.get(edge);
            if (candidates.isEmpty()) continue;
            Candidate best = candidates.getFirst();
            List<RecognitionEdge> tied = candidates.stream()
                    .filter(candidate -> candidate.rank().equals(best.rank())).map(Candidate::edge).toList();
            if (tied.size() > 1) {
                ambiguities.add(new RecognitionEdgeAssignment.Ambiguity(edge, tied,
                        "AMBIGUOUS_ASSIGNMENT"));
            } else {
                nextCandidate.put(edge, 0);
                pending.add(edge);
            }
        }
        while (!pending.isEmpty()) {
            RecognitionEdge sourceEdge = pending.removeFirst();
            List<Candidate> candidates = ranked.get(sourceEdge);
            int index = nextCandidate.get(sourceEdge);
            while (index < candidates.size() && blockedTargets.contains(candidates.get(index).edge().id())) index++;
            if (index >= candidates.size()) continue;
            Candidate candidate = candidates.get(index);
            nextCandidate.put(sourceEdge, index + 1);
            Preferred current = heldByTarget.get(candidate.edge().id());
            if (current == null) {
                heldByTarget.put(candidate.edge().id(), new Preferred(sourceEdge, candidate));
            } else {
                int comparison = candidate.rank().compareTo(current.candidate().rank());
                if (comparison < 0) {
                    heldByTarget.put(candidate.edge().id(), new Preferred(sourceEdge, candidate));
                    pending.addLast(current.source());
                } else if (comparison > 0) {
                    pending.addLast(sourceEdge);
                } else {
                    heldByTarget.remove(candidate.edge().id());
                    blockedTargets.add(candidate.edge().id());
                    List<RecognitionEdge> tiedTarget = List.of(candidate.edge());
                    String status = "COMPETING_SOURCES:" + current.source().id() + "," + sourceEdge.id();
                    ambiguities.add(new RecognitionEdgeAssignment.Ambiguity(current.source(), tiedTarget, status));
                    ambiguities.add(new RecognitionEdgeAssignment.Ambiguity(sourceEdge, tiedTarget, status));
                }
            }
        }
        heldByTarget.values().stream().sorted(Comparator.comparing(p -> p.source().id())).forEach(preferred -> {
            Candidate candidate = preferred.candidate();
            matches.add(new RecognitionEdgeAssignment.Match(preferred.source(), candidate.edge(),
                    candidate.kind(), candidate.geometry()));
        });
        Set<String> usedTarget = heldByTarget.keySet();
        Set<String> matchedSource = matches.stream().map(m -> m.source().id()).collect(java.util.stream.Collectors.toSet());
        Set<String> ambiguousSource = ambiguities.stream().map(a -> a.source().id()).collect(java.util.stream.Collectors.toSet());
        List<RecognitionEdge> unmatchedSource = source.edges().stream()
                .filter(e -> !matchedSource.contains(e.id()) && !ambiguousSource.contains(e.id())).toList();
        List<RecognitionEdge> unmatchedTarget = target.edges().stream().filter(e -> !usedTarget.contains(e.id())).toList();
        return new RecognitionEdgeAssignment(matches, unmatchedSource, unmatchedTarget, ambiguities,
                policy.provenance());
    }

    private List<Candidate> candidates(RecognitionEdge source, RecognitionGraph target,
            ExplicitResidueCorrespondence residues, RecognitionEdgeAssignmentPolicy policy) {
        String mappedFeature = policy.sourceToTargetFeature().get(source.ligandFeature().id());
        if (mappedFeature == null) return List.of();
        var mappedResidue = residues.subjectOf(source.environmentResidue());
        if (mappedResidue.isEmpty()) return List.of();
        List<Candidate> result = new ArrayList<>();
        for (RecognitionEdge edge : target.edges()) {
            if (edge.quality() != EvidenceQuality.ADEQUATE || !edge.ligandFeature().id().equals(mappedFeature)) continue;
            var tolerance = policy.tolerance(source.interactionType());
            var geometry = deviation(source, edge);
            if (geometry.distanceAngstroms() > tolerance.distanceAngstroms()
                    || geometry.primaryAngleDegrees() > tolerance.primaryAngleDegrees()
                    || geometry.secondaryAngleDegrees() > tolerance.secondaryAngleDegrees()) continue;
            boolean sameResidue = edge.environmentResidue().equals(mappedResidue.get());
            boolean sameType = edge.interactionType() == source.interactionType();
            var kind = sameResidue && sameType ? RecognitionEdgeAssignment.MatchKind.PRESERVED
                    : sameResidue ? RecognitionEdgeAssignment.MatchKind.SUBSTITUTED
                    : RecognitionEdgeAssignment.MatchKind.REROUTED;
            int classRank = kind.ordinal();
            result.add(new Candidate(edge, kind, geometry,
                    new Rank(classRank, geometry.distanceAngstroms(), geometry.primaryAngleDegrees(),
                            geometry.secondaryAngleDegrees())));
        }
        return result;
    }

    private static RecognitionEdgeAssignment.GeometryDeviation deviation(RecognitionEdge first,
            RecognitionEdge second) {
        return new RecognitionEdgeAssignment.GeometryDeviation(
                Math.abs(first.interaction().distanceAngstroms() - second.interaction().distanceAngstroms()),
                angle(first.interaction().primaryAngleDegrees(), second.interaction().primaryAngleDegrees()),
                angle(first.interaction().secondaryAngleDegrees(), second.interaction().secondaryAngleDegrees()));
    }
    private static double angle(Double first, Double second) {
        if (first == null && second == null) return 0;
        if (first == null || second == null) return Double.POSITIVE_INFINITY;
        return Math.abs(first - second);
    }
    private record Candidate(RecognitionEdge edge, RecognitionEdgeAssignment.MatchKind kind,
            RecognitionEdgeAssignment.GeometryDeviation geometry, Rank rank) implements Comparable<Candidate> {
        @Override public int compareTo(Candidate other) { return rank.compareTo(other.rank); }
    }
    private record Preferred(RecognitionEdge source, Candidate candidate) { }
    private record Rank(int kind, double distance, double primary, double secondary) implements Comparable<Rank> {
        @Override public int compareTo(Rank o) {
            int value = Integer.compare(kind, o.kind); if (value != 0) return value;
            value = Double.compare(distance, o.distance); if (value != 0) return value;
            value = Double.compare(primary, o.primary); if (value != 0) return value;
            return Double.compare(secondary, o.secondary);
        }
    }
}
