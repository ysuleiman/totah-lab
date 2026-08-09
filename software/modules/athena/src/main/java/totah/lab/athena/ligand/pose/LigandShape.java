package totah.lab.athena.ligand.pose;

import totah.lab.gaia.geometry.BoundingBox;
import totah.lab.gaia.geometry.Point3D;

import java.util.Objects;

/**
 * Heavy-atom geometric summary of a predicted ligand pose: centroid,
 * bounding box, and the radius of the smallest sphere centered on the
 * centroid that contains every heavy atom. Hydrogens are excluded so the
 * summary reflects the pose's actual occupied volume.
 */
public record LigandShape(
        Point3D centroid,
        BoundingBox bounds,
        double radiusFromCentroid,
        int heavyAtomCount
) {
    public LigandShape {
        Objects.requireNonNull(centroid, "centroid");
        Objects.requireNonNull(bounds, "bounds");

        if (!Double.isFinite(radiusFromCentroid)
                || radiusFromCentroid < 0.0) {
            throw new IllegalArgumentException(
                    "radiusFromCentroid must be finite and non-negative"
            );
        }

        if (heavyAtomCount <= 0) {
            throw new IllegalArgumentException(
                    "heavyAtomCount must be positive"
            );
        }
    }
}
