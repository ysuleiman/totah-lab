package totah.lab.athena.ligand.selectivity;

import java.util.Objects;

/**
 * Similarity of a mutant pose to two wild-type reference poses,
 * measured against each reference independently:
 * index-correspondence heavy-atom RMSD (nullable when the atom
 * correspondence is invalid), heavy-atom centroid shift, and
 * contact-set Jaccard, plus the deterministic classification and the
 * reason it fired.
 *
 * <p>Computational pose evidence only: no metric here says anything
 * about mechanism, and docking confidence is never an input.
 */
public record PoseReferenceSimilarity(
        Double rmsdToA,
        double centroidShiftToA,
        double contactSimilarityToA,
        Double rmsdToB,
        double centroidShiftToB,
        double contactSimilarityToB,
        PoseSimilarityClassification classification,
        String reason
) {

    public PoseReferenceSimilarity {
        if (!Double.isFinite(centroidShiftToA)
                || centroidShiftToA < 0.0
                || !Double.isFinite(centroidShiftToB)
                || centroidShiftToB < 0.0) {
            throw new IllegalArgumentException(
                    "centroid shifts must be finite and non-negative"
            );
        }

        validateUnitInterval(
                contactSimilarityToA,
                "contactSimilarityToA"
        );
        validateUnitInterval(
                contactSimilarityToB,
                "contactSimilarityToB"
        );

        Objects.requireNonNull(classification, "classification");
        Objects.requireNonNull(reason, "reason");

        if (reason.isBlank()) {
            throw new IllegalArgumentException(
                    "reason must not be blank"
            );
        }
    }

    private static void validateUnitInterval(
            double value,
            String fieldName
    ) {
        if (!Double.isFinite(value) || value < 0.0 || value > 1.0) {
            throw new IllegalArgumentException(
                    fieldName + " must be between 0 and 1"
            );
        }
    }
}
