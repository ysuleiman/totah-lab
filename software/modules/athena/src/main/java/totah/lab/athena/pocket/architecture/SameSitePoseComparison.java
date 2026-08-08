package totah.lab.athena.pocket.architecture;

import totah.lab.gaia.geometry.Vector3D;

import java.util.List;
import java.util.Objects;

/**
 * Comparison of two poses of the SAME ligand within the homologous
 * site of two receptors, pose B aligned into the A frame with a
 * caller-supplied transform (typically the receptor-backbone CA
 * Kabsch transform).
 *
 * <p>{@code heavyAtomRmsd} and {@code rotationAngleDegrees} are
 * computed only over a VERIFIED atom correspondence (see
 * {@link LigandAtomCorrespondence}); they are {@code null} when the
 * correspondence is {@code NONE}, with {@code correspondenceReason}
 * saying why. Centroid, decomposition and orientation metrics are
 * always reported. This is computational pose evidence; it says
 * nothing about mechanism.</p>
 *
 * <ul>
 *   <li>{@code centroidTranslation}: vector from pose A's heavy-atom
 *       centroid to the aligned pose B centroid.</li>
 *   <li>{@code displacementAlongU1/U2/U3}: that translation decomposed
 *       onto pocket A's principal axes (u1 = depth axis);
 *       {@code lateralDisplacement} = magnitude of the u2/u3
 *       components.</li>
 *   <li>{@code orientationAnglesPoseA}/{@code orientationAnglesPoseB}:
 *       acute angles (degrees) between the ligand long axis (first
 *       principal component of the pose's heavy atoms; pose B in the
 *       aligned frame) and each of pocket A's principal axes.</li>
 * </ul>
 */
public record SameSitePoseComparison(
        String poseALabel,
        String poseBLabel,
        double centroidSeparationAngstroms,
        Vector3D centroidTranslation,
        Double rotationAngleDegrees,
        Double heavyAtomRmsd,
        LigandAtomCorrespondence.Method atomCorrespondence,
        String correspondenceReason,
        double displacementAlongU1,
        double displacementAlongU2,
        double displacementAlongU3,
        double lateralDisplacement,
        List<Double> orientationAnglesPoseA,
        List<Double> orientationAnglesPoseB
) {

    public SameSitePoseComparison {
        poseALabel = requireLabel(poseALabel, "poseALabel");
        poseBLabel = requireLabel(poseBLabel, "poseBLabel");
        Objects.requireNonNull(
                centroidTranslation,
                "centroidTranslation"
        );
        Objects.requireNonNull(
                atomCorrespondence,
                "atomCorrespondence"
        );
        Objects.requireNonNull(
                correspondenceReason,
                "correspondenceReason"
        );
        orientationAnglesPoseA = List.copyOf(
                Objects.requireNonNull(orientationAnglesPoseA,
                        "orientationAnglesPoseA")
        );
        orientationAnglesPoseB = List.copyOf(
                Objects.requireNonNull(orientationAnglesPoseB,
                        "orientationAnglesPoseB")
        );

        if (atomCorrespondence
                == LigandAtomCorrespondence.Method.NONE) {
            if (heavyAtomRmsd != null || rotationAngleDegrees != null) {
                throw new IllegalArgumentException(
                        "RMSD and rotation must be null when no atom "
                                + "correspondence is verified"
                );
            }
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
