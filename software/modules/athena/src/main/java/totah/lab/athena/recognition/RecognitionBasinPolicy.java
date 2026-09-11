package totah.lab.athena.recognition;

/** Fully explicit joint geometry/topology and recurrence policy. */
public record RecognitionBasinPolicy(double maximumGeometryDistanceAngstroms,
        double maximumTopologyDistance, int minimumDistinctSeeds,
        int minimumDistinctFamilies, RecognitionTopologyDistance.ScalarPolicy topologyScalarPolicy,
        PairSymmetryPolicy pairSymmetryPolicy, String provenance) {
    public RecognitionBasinPolicy {
        require(maximumGeometryDistanceAngstroms, "geometry threshold");
        require(maximumTopologyDistance, "topology threshold");
        if (minimumDistinctSeeds < 1 || minimumDistinctFamilies < 1)
            throw new IllegalArgumentException("recurrence minima must be positive");
        if (topologyScalarPolicy == null) throw new IllegalArgumentException("topology policy required");
        if (pairSymmetryPolicy == null) throw new IllegalArgumentException("pair symmetry policy required");
        if (provenance == null || provenance.isBlank()) throw new IllegalArgumentException("provenance required");
    }
    /** REQUIRE_EQUAL deliberately means bit-identical values; it introduces no implicit tolerance. */
    public enum PairSymmetryPolicy { REQUIRE_EQUAL, MAXIMUM, MEAN }
    private static void require(double value, String name) {
        if (!Double.isFinite(value) || value < 0) throw new IllegalArgumentException(name + " invalid");
    }
}
