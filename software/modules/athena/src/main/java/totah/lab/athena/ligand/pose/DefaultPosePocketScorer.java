package totah.lab.athena.ligand.pose;

import java.util.Objects;

/**
 * Weighted-sum {@link PosePocketScorer}: occupancy (containment) is the
 * primary term, contact-residue coverage the second, centroid proximity
 * the weakest. Weights are constructor-injected so the scorer can be
 * replaced or re-weighted without changing the assigner. Deterministic
 * and pure.
 */
public final class DefaultPosePocketScorer implements PosePocketScorer {

    private final PosePocketScoringWeights weights;

    public DefaultPosePocketScorer() {
        this(PosePocketScoringWeights.defaults());
    }

    public DefaultPosePocketScorer(PosePocketScoringWeights weights) {
        this.weights = Objects.requireNonNull(weights, "weights");
    }

    @Override
    public double score(PosePocketMetrics metrics) {
        Objects.requireNonNull(metrics, "metrics");

        return weights.occupancyWeight()
                * metrics.atomContainmentFraction()
                + weights.contactCoverageWeight()
                * metrics.contactResidueCoverage()
                + weights.centroidProximityWeight()
                * metrics.centroidProximity();
    }
}
