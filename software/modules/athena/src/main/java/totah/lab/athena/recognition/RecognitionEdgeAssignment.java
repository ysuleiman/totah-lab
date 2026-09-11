package totah.lab.athena.recognition;

import java.util.List;

/** One-to-one recognition-edge assignment, including unresolved ties. */
public record RecognitionEdgeAssignment(List<Match> matches,
        List<RecognitionEdge> unmatchedSource, List<RecognitionEdge> unmatchedTarget,
        List<Ambiguity> ambiguities, String provenance) {
    public RecognitionEdgeAssignment {
        matches = List.copyOf(matches); unmatchedSource = List.copyOf(unmatchedSource);
        unmatchedTarget = List.copyOf(unmatchedTarget); ambiguities = List.copyOf(ambiguities);
    }
    public boolean hasAmbiguousAssignment() { return !ambiguities.isEmpty(); }
    public record Match(RecognitionEdge source, RecognitionEdge target, MatchKind kind,
            GeometryDeviation geometry) { }
    public enum MatchKind { PRESERVED, SUBSTITUTED, REROUTED }
    public record GeometryDeviation(double distanceAngstroms,
            double primaryAngleDegrees, double secondaryAngleDegrees) { }
    public record Ambiguity(RecognitionEdge source, List<RecognitionEdge> equallyPlausibleTargets,
            String status) {
        public Ambiguity { equallyPlausibleTargets = List.copyOf(equallyPlausibleTargets); }
    }
}
