package totah.lab.athena.tmt;

import java.util.Objects;

public final class NearAttackAssessor {
    private static final double NUMERIC_TOLERANCE = 1.0e-9;

    public NearAttackAssessment assess(
            NearAttackGeometry geometry,
            NearAttackCriteria criteria,
            boolean sulfurStateEvaluated,
            boolean protonNetworkEvaluated) {
        Objects.requireNonNull(geometry, "geometry");
        Objects.requireNonNull(criteria, "criteria");

        boolean geometryCompatible = between(
                geometry.substrateSulfurToMethylCarbonAngstrom(),
                criteria.minimumAttackDistanceAngstrom(),
                criteria.maximumAttackDistanceAngstrom())
                && between(
                geometry.substrateSulfurMethylCarbonSamSulfurAngleDegrees(),
                criteria.minimumAttackAngleDegrees(),
                criteria.maximumAttackAngleDegrees())
                && geometry.methylCarbonToSamSulfurAngstrom()
                <= criteria.maximumDonorBondDistanceAngstrom();
        boolean clashCompatible = geometry.severeClashCount() <= criteria.maximumSevereClashCount();

        if (!geometryCompatible || !clashCompatible) {
            return new NearAttackAssessment(
                    NearAttackClassification.CLEARLY_NONPRODUCTIVE,
                    geometryCompatible,
                    clashCompatible,
                    sulfurStateEvaluated,
                    protonNetworkEvaluated,
                    "Fails at least one provenance-bound geometry or clash criterion.",
                    criteria.provenance());
        }
        if (!sulfurStateEvaluated || !protonNetworkEvaluated) {
            return new NearAttackAssessment(
                    NearAttackClassification.GEOMETRICALLY_NEAR_PRODUCTIVE,
                    true,
                    true,
                    sulfurStateEvaluated,
                    protonNetworkEvaluated,
                    "Geometry is candidate-compatible, but chemical competence is unevaluated.",
                    criteria.provenance());
        }
        return new NearAttackAssessment(
                NearAttackClassification.CHEMICALLY_COMPATIBLE_CANDIDATE,
                true,
                true,
                true,
                true,
                "Candidate geometry and the explicitly requested chemistry evidence are compatible; this is not proof of catalysis.",
                criteria.provenance());
    }

    private static boolean between(double value, double minimum, double maximum) {
        return value >= minimum - NUMERIC_TOLERANCE
                && value <= maximum + NUMERIC_TOLERANCE;
    }
}
