package totah.lab.athena.ligand.pose;

import totah.lab.gaia.geometry.Point3D;

public record PocketPose(
        Point3D ligandCentroid,
        double pocketCentroidDistance
) {
    public PocketPose {
        if (!Double.isFinite(pocketCentroidDistance)
                || pocketCentroidDistance < 0.0) {
            throw new IllegalArgumentException(
                    "pocketCentroidDistance must be finite and non-negative"
            );
        }
    }
}
