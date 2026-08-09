package totah.lab.athena.ligand.pose;

import totah.lab.gaia.pocket.Pocket;

import java.util.Objects;

/**
 * Evidence record comparing one Vina pose docked against a query
 * protein with one docked against a candidate protein: whether the two
 * predicted poses occupy structurally homologous sites, decided by
 * structural pocket alignment.
 *
 * <p>{@code samePocketNumber} is informational only: pocket numbers are
 * per-protein detection artifacts, so equal numbers are NOT evidence of
 * correspondence and never influence {@code relationship}.
 *
 * <p>Terminology: this record says a predicted pose <i>occupies</i> a
 * site. It never asserts binding; a {@link PoseSiteRelationship#DIFFERENT_SITE}
 * verdict is geometric evidence about Vina poses, not proof about the
 * biological binding site.
 *
 * <p>{@code alignedLigandCentroidDistance} is the distance between the
 * heavy-atom centroids after moving the candidate pose into the query
 * frame with the pocket-alignment transform. {@code alignedLigandRmsd}
 * is the index-correspondence heavy-atom RMSD over the same transform;
 * it assumes both ligands are the same compound in the same atom order
 * (atom ordering is preserved end to end) and is {@code null} when the
 * heavy-atom counts differ, because the correspondence is then invalid.
 * {@code pocketSimilarity} and the aligned distances are {@code null}
 * when the comparison could not run (relationship
 * {@link PoseSiteRelationship#AMBIGUOUS}).
 */
public record CrossProteinPoseComparison(
        String queryPoseLabel,
        String candidatePoseLabel,
        Pocket queryPocket,
        Pocket candidatePocket,
        boolean samePocketNumber,
        boolean pocketsStructurallyHomologous,
        Double pocketSimilarity,
        Double alignedLigandCentroidDistance,
        Double alignedLigandRmsd,
        int sharedAlignedContactResidues,
        double contactResidueSimilarity,
        PoseSiteRelationship relationship,
        String reason
) {

    public CrossProteinPoseComparison {
        queryPoseLabel = requireLabel(queryPoseLabel, "queryPoseLabel");
        candidatePoseLabel = requireLabel(
                candidatePoseLabel,
                "candidatePoseLabel"
        );
        Objects.requireNonNull(relationship, "relationship");
        Objects.requireNonNull(reason, "reason");

        if (reason.isBlank()) {
            throw new IllegalArgumentException(
                    "reason must not be blank"
            );
        }

        if (sharedAlignedContactResidues < 0) {
            throw new IllegalArgumentException(
                    "sharedAlignedContactResidues must be non-negative"
            );
        }

        if (!Double.isFinite(contactResidueSimilarity)
                || contactResidueSimilarity < 0.0
                || contactResidueSimilarity > 1.0) {
            throw new IllegalArgumentException(
                    "contactResidueSimilarity must be between 0 and 1"
            );
        }

        if (relationship != PoseSiteRelationship.AMBIGUOUS) {
            Objects.requireNonNull(queryPocket, "queryPocket");
            Objects.requireNonNull(candidatePocket, "candidatePocket");
            Objects.requireNonNull(
                    pocketSimilarity,
                    "pocketSimilarity"
            );
            Objects.requireNonNull(
                    alignedLigandCentroidDistance,
                    "alignedLigandCentroidDistance"
            );
        }
    }

    private static String requireLabel(
            String label,
            String fieldName
    ) {
        Objects.requireNonNull(label, fieldName);

        if (label.isBlank()) {
            throw new IllegalArgumentException(
                    fieldName + " must not be blank"
            );
        }

        return label;
    }
}
