package totah.lab.athena.pocket.architecture;

import totah.lab.gaia.geometry.Point3D;

import java.util.Objects;

/**
 * One 26-connected component of the probe-passable voxel set.
 * Labels are 1-based and assigned after sorting by descending voxel
 * count with the lowest flat voxel index breaking ties (the stage8_11
 * {@code components()} ordering), so labeling is deterministic.
 */
public record EscapeRouteComponent(
        int label,
        int voxelCount,
        double volumeCubicAngstroms,
        Point3D centroid) {

    public EscapeRouteComponent {
        if (label < 1) {
            throw new IllegalArgumentException("label must be 1-based");
        }
        if (voxelCount < 1) {
            throw new IllegalArgumentException("voxelCount must be positive");
        }
        if (!Double.isFinite(volumeCubicAngstroms)
                || volumeCubicAngstroms <= 0.0) {
            throw new IllegalArgumentException(
                    "volumeCubicAngstroms must be finite and positive");
        }
        Objects.requireNonNull(centroid, "centroid");
    }
}
