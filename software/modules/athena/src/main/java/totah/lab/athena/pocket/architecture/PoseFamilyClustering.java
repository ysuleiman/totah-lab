package totah.lab.athena.pocket.architecture;

import java.util.List;
import java.util.Objects;

/**
 * Result of clustering N poses of the same ligand in one receptor
 * frame. Matrices are row-major, indexed by input pose index.
 *
 * <ul>
 *   <li>{@code rmsdMatrix}: pairwise heavy-atom RMSD after Kabsch
 *       superposition over the VERIFIED atom correspondence (each
 *       pose mapped onto pose 0's indexing); {@code NaN} for pairs
 *       involving a pose with no verified correspondence.</li>
 *   <li>{@code centroidDistanceMatrix}: pairwise heavy-atom centroid
 *       distances (always available).</li>
 *   <li>{@code contactJaccardMatrix}: Jaccard of the contact-residue
 *       sets, {@code null} when no contact lists were supplied; 0.0
 *       when both sets are empty (no contact evidence).</li>
 *   <li>{@code occupancyJaccardMatrix}: Jaccard of the
 *       occupied-sphere sets, {@code null} when no pocket was
 *       supplied; 0.0 when both sets are empty.</li>
 *   <li>{@code correspondenceByPose}: how each pose's atoms mapped
 *       onto pose 0 (a pose with {@code NONE} always starts its own
 *       family).</li>
 * </ul>
 */
public record PoseFamilyClustering(
        List<PoseFamily> families,
        List<List<Double>> rmsdMatrix,
        List<List<Double>> centroidDistanceMatrix,
        List<List<Double>> contactJaccardMatrix,
        List<List<Double>> occupancyJaccardMatrix,
        List<LigandAtomCorrespondence.Method> correspondenceByPose
) {

    /**
     * One pose family: the representative is the FIRST member in
     * input order (the pose that started the family).
     */
    public record PoseFamily(
            int representativeIndex,
            List<Integer> memberIndices
    ) {
        public PoseFamily {
            if (representativeIndex < 0) {
                throw new IllegalArgumentException(
                        "representativeIndex must be non-negative"
                );
            }

            memberIndices = List.copyOf(
                    Objects.requireNonNull(
                            memberIndices,
                            "memberIndices"
                    )
            );

            if (memberIndices.isEmpty()
                    || memberIndices.get(0) != representativeIndex) {
                throw new IllegalArgumentException(
                        "memberIndices must start with the "
                                + "representative"
                );
            }
        }

        public int size() {
            return memberIndices.size();
        }
    }

    public PoseFamilyClustering {
        families = List.copyOf(
                Objects.requireNonNull(families, "families")
        );
        rmsdMatrix = copyMatrix(rmsdMatrix, "rmsdMatrix");
        centroidDistanceMatrix = copyMatrix(
                centroidDistanceMatrix,
                "centroidDistanceMatrix"
        );
        contactJaccardMatrix = contactJaccardMatrix == null
                ? null
                : copyMatrix(
                        contactJaccardMatrix,
                        "contactJaccardMatrix"
                );
        occupancyJaccardMatrix = occupancyJaccardMatrix == null
                ? null
                : copyMatrix(
                        occupancyJaccardMatrix,
                        "occupancyJaccardMatrix"
                );
        correspondenceByPose = List.copyOf(
                Objects.requireNonNull(correspondenceByPose,
                        "correspondenceByPose")
        );
    }

    private static List<List<Double>> copyMatrix(
            List<List<Double>> matrix,
            String fieldName
    ) {
        Objects.requireNonNull(matrix, fieldName);

        return matrix.stream()
                .map(row -> List.copyOf(
                        Objects.requireNonNull(row, fieldName + " row")
                ))
                .toList();
    }
}
