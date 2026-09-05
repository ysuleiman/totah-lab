package totah.lab.athena.pocket.architecture;

import totah.lab.gaia.geometry.Point3D;

import java.util.List;
import java.util.Objects;

/**
 * Immutable result of {@link EscapeRouteAnalyzer#analyze}. All
 * coordinates are absolute grid-voxel coordinates (not voxel
 * indices). {@code escapePath} is the widest (maximum-bottleneck)
 * path from the origin voxel to the exterior boundary, ordered
 * origin-first, as in the stage8_11 {@code widest_path()}; it is
 * reported even when no probe-clear route exists, in which case its
 * bottleneck clearance falls below the probe radius.
 */
public record EscapeRouteAnalysis(
        EscapeRouteClassification classification,
        Point3D originVoxel,
        double originClearanceAngstroms,
        int reachableVoxelCount,
        double reachableVolumeCubicAngstroms,
        double bottleneckClearanceAngstroms,
        Point3D bottleneckVoxel,
        List<Point3D> escapePath,
        List<EscapeRouteComponent> components,
        EscapeRouteOptions options) {

    public EscapeRouteAnalysis {
        Objects.requireNonNull(classification, "classification");
        Objects.requireNonNull(originVoxel, "originVoxel");
        Objects.requireNonNull(bottleneckVoxel, "bottleneckVoxel");
        if (reachableVoxelCount < 0) {
            throw new IllegalArgumentException(
                    "reachableVoxelCount must be non-negative");
        }
        escapePath = List.copyOf(
                Objects.requireNonNull(escapePath, "escapePath"));
        components = List.copyOf(
                Objects.requireNonNull(components, "components"));
        Objects.requireNonNull(options, "options");
    }
}
