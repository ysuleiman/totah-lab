package totah.lab.athena.pocket.evidence;

/**
 * Summary of how the configured key residues of the query pocket
 * fared under the selected alignment. Per-residue detail (which key
 * residue matched, and how) is available through the
 * {@code queryKeyResidue} flag of the correspondences in
 * {@link PocketResidueEvidence}.
 *
 * @param totalKeyResidueCount configured key residues present in the
 *                             query pocket
 * @param matchedKeyResidueCount key residues with a spatial
 *                             correspondence
 * @param identicalKeyResidueCount matched key residues with an
 *                             identical partner
 * @param chemistryCompatibleKeyResidueCount matched key residues
 *                             whose pair is chemically acceptable
 *                             (identical, conservative or
 *                             chemistry-compatible)
 */
public record KeyResidueEvidence(
        int totalKeyResidueCount,
        int matchedKeyResidueCount,
        int identicalKeyResidueCount,
        int chemistryCompatibleKeyResidueCount
) {

    public KeyResidueEvidence {
        requireCount(totalKeyResidueCount, "totalKeyResidueCount");
        requireCount(matchedKeyResidueCount, "matchedKeyResidueCount");
        requireCount(
                identicalKeyResidueCount,
                "identicalKeyResidueCount"
        );
        requireCount(
                chemistryCompatibleKeyResidueCount,
                "chemistryCompatibleKeyResidueCount"
        );

        if (identicalKeyResidueCount
                > chemistryCompatibleKeyResidueCount) {
            throw new IllegalArgumentException(
                    "identicalKeyResidueCount cannot exceed"
                            + " chemistryCompatibleKeyResidueCount"
            );
        }

        if (chemistryCompatibleKeyResidueCount > matchedKeyResidueCount) {
            throw new IllegalArgumentException(
                    "chemistryCompatibleKeyResidueCount cannot exceed"
                            + " matchedKeyResidueCount"
            );
        }

        if (matchedKeyResidueCount > totalKeyResidueCount) {
            throw new IllegalArgumentException(
                    "matchedKeyResidueCount cannot exceed"
                            + " totalKeyResidueCount"
            );
        }
    }

    private static void requireCount(int value, String name) {
        if (value < 0) {
            throw new IllegalArgumentException(
                    name + " must be non-negative"
            );
        }
    }
}
