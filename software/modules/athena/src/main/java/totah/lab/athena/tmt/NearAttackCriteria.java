package totah.lab.athena.tmt;

import java.util.Objects;

/** Evidence-derived candidate ranges for a sulfur methyl-transfer near-attack geometry. */
public record NearAttackCriteria(
        double minimumAttackDistanceAngstrom,
        double maximumAttackDistanceAngstrom,
        double minimumAttackAngleDegrees,
        double maximumAttackAngleDegrees,
        double maximumDonorBondDistanceAngstrom,
        int maximumSevereClashCount,
        String provenance) {

    public NearAttackCriteria {
        requireFinitePositive(minimumAttackDistanceAngstrom, "minimumAttackDistanceAngstrom");
        requireFinitePositive(maximumAttackDistanceAngstrom, "maximumAttackDistanceAngstrom");
        requireFinitePositive(minimumAttackAngleDegrees, "minimumAttackAngleDegrees");
        requireFinitePositive(maximumAttackAngleDegrees, "maximumAttackAngleDegrees");
        requireFinitePositive(maximumDonorBondDistanceAngstrom, "maximumDonorBondDistanceAngstrom");
        if (minimumAttackDistanceAngstrom > maximumAttackDistanceAngstrom) {
            throw new IllegalArgumentException("attack distance range is reversed");
        }
        if (minimumAttackAngleDegrees > maximumAttackAngleDegrees
                || maximumAttackAngleDegrees > 180.0) {
            throw new IllegalArgumentException("attack angle range is invalid");
        }
        if (maximumSevereClashCount < 0) {
            throw new IllegalArgumentException("maximumSevereClashCount must be non-negative");
        }
        Objects.requireNonNull(provenance, "provenance");
        provenance = provenance.trim();
        if (provenance.isEmpty()) {
            throw new IllegalArgumentException("provenance must not be blank");
        }
    }

    private static void requireFinitePositive(double value, String name) {
        if (!Double.isFinite(value) || value <= 0.0) {
            throw new IllegalArgumentException(name + " must be finite and positive");
        }
    }
}
