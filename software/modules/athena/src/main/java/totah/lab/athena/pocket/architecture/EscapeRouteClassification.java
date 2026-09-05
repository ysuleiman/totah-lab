package totah.lab.athena.pocket.architecture;

/**
 * Escape-route verdict of {@link EscapeRouteAnalyzer}. The stage8_11
 * Python script has no explicit escape label set — it hard-fails with
 * {@code RuntimeError("No exterior access path")} and decides
 * exterior connectivity separately via {@code reachable()} at the
 * probe clearance — so these labels are derived from that logic in
 * the same SCREAMING_SNAKE style.
 */
public enum EscapeRouteClassification {

    /** Origin voxel clearance is below the probe radius; the origin is buried in occupancy. */
    ORIGIN_OCCUPIED,

    /** Origin is passable but no probe-clear path reaches the exterior grid boundary. */
    NO_ESCAPE_ROUTE,

    /** A probe-clear connected path from the origin reaches the exterior grid boundary. */
    ESCAPE_ROUTE_EXISTS
}
