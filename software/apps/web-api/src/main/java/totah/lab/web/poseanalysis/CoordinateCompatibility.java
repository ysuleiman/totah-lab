package totah.lab.web.poseanalysis;

/**
 * Coordinate-frame compatibility between the receptor artifact a pose
 * was docked against and the structure artifact a pocket's rows were
 * generated from. Same accession or same sequence is NEVER evidence of
 * compatibility — only artifact identity or an explicit validated
 * rigid transform is.
 */
public enum CoordinateCompatibility {

    /** Both sides are the same artifact file (SHA-256 match). */
    IDENTICAL_ARTIFACT,

    /** Different artifacts; a rigid CA fit met the validation
     *  thresholds, so coordinates may be compared through the recorded
     *  transform. */
    VALIDATED_TRANSFORM,

    /** Different artifacts and the CA fit failed validation (too few
     *  matched pairs or RMSD above threshold). Sphere-derived metrics
     *  must not be computed. */
    INCOMPATIBLE,

    /** The pocket-side structure artifact could not be loaded, so no
     *  validation could run. Sphere-derived metrics must not be
     *  computed. */
    UNKNOWN
}
