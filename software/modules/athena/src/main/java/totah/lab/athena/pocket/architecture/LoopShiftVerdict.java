package totah.lab.athena.pocket.architecture;

/**
 * Direction of the aligned pose displacement relative to the
 * loop-region centroid. This is a measurement of pose geometry only;
 * it says nothing about whether the loop causes the displacement.
 */
public enum LoopShiftVerdict {

    /** The A-to-B pose displacement points toward the loop centroid
     *  beyond the significance threshold. */
    POSE_SHIFTED_TOWARD_LOOP,

    /** The A-to-B pose displacement points away from the loop
     *  centroid beyond the significance threshold. */
    POSE_SHIFTED_AWAY_FROM_LOOP,

    /** The toward/away component is below the significance threshold
     *  (or undefined). */
    ORTHOGONAL_OR_NEGLIGIBLE
}
