package totah.lab.athena.pocket.architecture;

/**
 * Which documented geometric difference between two pockets dominates
 * the pose displacement, computed from the metrics — never free text.
 * Rules are evaluated in declaration order.
 */
public enum DominantArchitectureDifference {

    /** The poses occupy disjoint connected components of the
     *  reference pocket's sphere set in the shared frame. */
    DIFFERENT_COMPARTMENT,

    /** The aligned pose centroids are displaced mostly perpendicular
     *  to the pocket's depth axis (the lateral u2/u3 components
     *  dominate the u1 component). */
    LATERAL_SHIFT,

    /** The aligned pose centroids are displaced mostly along the
     *  pocket's depth axis u1. */
    DIFFERENT_DEPTH,

    /** The pose centroids sit at clearly different radial distances
     *  from their pocket's mouth center. */
    DIFFERENT_MOUTH_REGION,

    /** The poses' mean wall distances differ clearly: the wall moved
     *  relative to the pose. */
    SHIFTED_WALL,

    /** No documented difference exceeded its threshold. */
    NONE
}
