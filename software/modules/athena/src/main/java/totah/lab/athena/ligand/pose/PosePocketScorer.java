package totah.lab.athena.ligand.pose;

/**
 * Scores how well a predicted pose occupies one candidate pocket, from
 * the raw {@link PosePocketMetrics}. Implementations must be
 * deterministic and pure: the same metrics always yield the same
 * score.
 */
public interface PosePocketScorer {

    double score(PosePocketMetrics metrics);
}
