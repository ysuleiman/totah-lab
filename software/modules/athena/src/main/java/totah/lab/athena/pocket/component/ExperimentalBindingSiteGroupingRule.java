package totah.lab.athena.pocket.component;

/** Versioned, centralized cavity grouping thresholds. */
public record ExperimentalBindingSiteGroupingRule(
        double residueJaccardThreshold,
        double ligandAtomJaccardThreshold,
        double maximumEngagedLigandAtomDistanceAngstrom,
        double maximumSphereSurfaceGapAngstrom,
        double maximumPocketCentroidDistanceAngstrom) {
    public ExperimentalBindingSiteGroupingRule {
        if (residueJaccardThreshold < 0 || residueJaccardThreshold > 1
                || ligandAtomJaccardThreshold < 0
                || ligandAtomJaccardThreshold > 1
                || maximumEngagedLigandAtomDistanceAngstrom <= 0
                || maximumSphereSurfaceGapAngstrom < 0
                || maximumPocketCentroidDistanceAngstrom <= 0) {
            throw new IllegalArgumentException("Invalid grouping thresholds");
        }
    }

    public static ExperimentalBindingSiteGroupingRule defaults() {
        return new ExperimentalBindingSiteGroupingRule(0.20, 0.25, 6.0,
                2.0, 12.0);
    }
}
