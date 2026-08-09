package totah.lab.web.poseanalysis;

import totah.lab.gaia.geometry.RigidTransform;

/**
 * An explicit, validated rigid transform between two structure
 * artifacts' coordinate frames: the Kabsch CA fit of the pocket-side
 * structure onto the docked receptor structure, with the evidence of
 * the fit (matched CA pairs and post-fit RMSD) and the validation
 * verdict. Only coordinates moved through this transform may be
 * compared across the two artifacts.
 */
public record StructureTransform(
        String sourceArtifactId,
        String targetArtifactId,
        RigidTransform transform,
        int matchedPairs,
        double rmsd,
        String method,
        String validationStatus
) {
}
