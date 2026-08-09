package totah.lab.web.poseanalysis;

/**
 * The coordinate provenance of one pocket-geometry analysis: which
 * artifact the docked receptor came from, which structure artifact the
 * pocket rows were generated from, their validated frame
 * compatibility, and whether sphere-derived metrics may be computed
 * ({@code false} unless the compatibility is
 * {@link CoordinateCompatibility#IDENTICAL_ARTIFACT} or
 * {@link CoordinateCompatibility#VALIDATED_TRANSFORM}). The note
 * carries the human-readable audit trail (hashes, fit RMSD, pair
 * count, legacy/mixed-frame wording).
 */
public record CoordinateProvenance(
        StructureArtifactRef receptorArtifact,
        StructureArtifactRef pocketStructureArtifact,
        CoordinateCompatibility compatibility,
        StructureTransform transform,
        boolean sphereMetricsAvailable,
        String note
) {
}
