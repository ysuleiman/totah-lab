package totah.lab.athena.recognition;

import java.util.DoubleSummaryStatistics;
import java.util.Map;

/** Transparent topology comparison vector; no composite score or hidden weights. */
public record RecognitionTopologyDistance(int preservedEdges, int substitutedEdges,
        int reroutedEdges, int lostEdges, int unobservedEdges, int unavailableEdges,
        int ligandFeatureRouteChanges, int residueRouteChanges, double typedJaccard,
        GeometryDeviationSummary matchedGeometryDeviation,
        int differentialSurfaceIntersectionChanges, int ambiguousAssignments) {

    public static RecognitionTopologyDistance from(RecognitionEdgeAssignment assignment) {
        Map<RecognitionEdgeAssignment.MatchKind, Long> kinds = assignment.matches().stream().collect(
                java.util.stream.Collectors.groupingBy(RecognitionEdgeAssignment.Match::kind,
                        () -> new java.util.EnumMap<>(RecognitionEdgeAssignment.MatchKind.class),
                        java.util.stream.Collectors.counting()));
        int unavailable = (int) assignment.unmatchedSource().stream()
                .filter(edge -> edge.quality() != EvidenceQuality.ADEQUATE).count();
        int lost = (int) assignment.unmatchedSource().stream()
                .filter(edge -> edge.quality() == EvidenceQuality.ADEQUATE)
                .filter(edge -> edge.role() == InteractionRole.DEFINING).count();
        int unobserved = (int) assignment.unmatchedSource().stream()
                .filter(edge -> edge.quality() == EvidenceQuality.ADEQUATE)
                .filter(edge -> edge.role() != InteractionRole.DEFINING).count();
        int preserved = kinds.getOrDefault(RecognitionEdgeAssignment.MatchKind.PRESERVED, 0L).intValue();
        int substituted = kinds.getOrDefault(RecognitionEdgeAssignment.MatchKind.SUBSTITUTED, 0L).intValue();
        int rerouted = kinds.getOrDefault(RecognitionEdgeAssignment.MatchKind.REROUTED, 0L).intValue();
        int adequateTarget = (int) assignment.matches().stream().count()
                + (int) assignment.unmatchedTarget().stream().filter(e -> e.quality() == EvidenceQuality.ADEQUATE).count();
        int adequateSource = assignment.matches().size() + unobserved + lost;
        int union = adequateSource + adequateTarget - preserved;
        double jaccard = union == 0 ? 1.0 : (double) preserved / union;
        DoubleSummaryStatistics distance = assignment.matches().stream()
                .mapToDouble(m -> m.geometry().distanceAngstroms()).summaryStatistics();
        DoubleSummaryStatistics primary = assignment.matches().stream()
                .mapToDouble(m -> m.geometry().primaryAngleDegrees()).filter(Double::isFinite).summaryStatistics();
        DoubleSummaryStatistics secondary = assignment.matches().stream()
                .mapToDouble(m -> m.geometry().secondaryAngleDegrees()).filter(Double::isFinite).summaryStatistics();
        int differentialChanges = (int) assignment.matches().stream()
                .filter(m -> m.kind() != RecognitionEdgeAssignment.MatchKind.PRESERVED)
                .filter(m -> m.source().engagesDifferentialEnvironment()
                        || m.target().engagesDifferentialEnvironment()).count();
        return new RecognitionTopologyDistance(preserved, substituted, rerouted, lost, unobserved,
                unavailable, substituted + rerouted, rerouted, jaccard,
                new GeometryDeviationSummary(mean(distance), max(distance), mean(primary), max(primary),
                        mean(secondary), max(secondary)), differentialChanges,
                assignment.ambiguities().size());
    }

    /** Explicit, inspectable scalar allowed only for basin clustering. */
    public double scalarDistance(ScalarPolicy policy) {
        return policy.typedJaccardWeight() * (1.0 - typedJaccard)
                + policy.substitutionWeight() * substitutedEdges
                + policy.rerouteWeight() * reroutedEdges
                + policy.unobservedWeight() * unobservedEdges
                + policy.unavailableWeight() * unavailableEdges;
    }

    public record ScalarPolicy(double typedJaccardWeight, double substitutionWeight,
            double rerouteWeight, double unobservedWeight, double unavailableWeight,
            String provenance) {
        public ScalarPolicy {
            for (double value : new double[]{typedJaccardWeight, substitutionWeight, rerouteWeight,
                    unobservedWeight, unavailableWeight}) if (!Double.isFinite(value) || value < 0)
                throw new IllegalArgumentException("weights must be finite and non-negative");
            if (provenance == null || provenance.isBlank()) throw new IllegalArgumentException("provenance required");
        }
    }
    public record GeometryDeviationSummary(double meanDistanceAngstroms, double maxDistanceAngstroms,
            double meanPrimaryAngleDegrees, double maxPrimaryAngleDegrees,
            double meanSecondaryAngleDegrees, double maxSecondaryAngleDegrees) {
        public boolean distanceAvailable() { return Double.isFinite(meanDistanceAngstroms); }
        public boolean primaryAngleAvailable() { return Double.isFinite(meanPrimaryAngleDegrees); }
        public boolean secondaryAngleAvailable() { return Double.isFinite(meanSecondaryAngleDegrees); }
    }
    private static double mean(DoubleSummaryStatistics stats) { return stats.getCount() == 0 ? Double.NaN : stats.getAverage(); }
    private static double max(DoubleSummaryStatistics stats) { return stats.getCount() == 0 ? Double.NaN : stats.getMax(); }
}
