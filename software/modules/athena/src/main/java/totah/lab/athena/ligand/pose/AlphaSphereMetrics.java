package totah.lab.athena.ligand.pose;

import totah.lab.gaia.geometry.Point3D;
import totah.lab.gaia.molecule.Ligand;
import totah.lab.gaia.pocket.AlphaSphere;
import totah.lab.gaia.pocket.AlphaSphereSet;
import totah.lab.gaia.pocket.Pocket;

import java.util.List;
import java.util.Objects;

/**
 * Computes {@link AlphaSphereOccupancy} metrics of a predicted pose
 * against the alpha spheres of one candidate pocket. This is the
 * primary occupancy signal of the pose-to-pocket assignment.
 */
public final class AlphaSphereMetrics {

    private AlphaSphereMetrics() {
    }

    /**
     * Measures how deeply the heavy atoms of a predicted pose occupy
     * the alpha spheres of {@code pocket}. A pocket without spheres
     * yields an occupancy record with {@code basisAvailable == false}.
     *
     * @throws IllegalArgumentException if the ligand has no heavy atoms
     */
    public static AlphaSphereOccupancy calculate(
            Ligand ligand,
            Pocket pocket
    ) {
        Objects.requireNonNull(ligand, "ligand");
        Objects.requireNonNull(pocket, "pocket");

        List<AlphaSphere> spheres = spheres(pocket);

        if (spheres.isEmpty()) {
            return new AlphaSphereOccupancy(
                    0,
                    false,
                    0.0,
                    0.0,
                    0.0,
                    0.0
            );
        }

        List<Double> nearest = nearestSurfaceDistances(ligand, spheres);

        double count = nearest.size();
        long within2A = nearest.stream()
                .filter(distance -> distance <= 2.0)
                .count();
        long within3A = nearest.stream()
                .filter(distance -> distance <= 3.0)
                .count();
        double mean = nearest.stream()
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(0.0);
        double max = nearest.stream()
                .mapToDouble(Double::doubleValue)
                .max()
                .orElse(0.0);

        return new AlphaSphereOccupancy(
                spheres.size(),
                true,
                within2A / count,
                within3A / count,
                mean,
                max
        );
    }

    /**
     * Returns the fraction of ligand heavy atoms whose nearest sphere
     * surface distance is at most {@code surfaceDistanceTolerance}
     * angstroms. Returns {@code 0.0} when the pocket has no spheres.
     *
     * @throws IllegalArgumentException if the ligand has no heavy atoms
     */
    public static double occupiedFraction(
            Ligand ligand,
            Pocket pocket,
            double surfaceDistanceTolerance
    ) {
        Objects.requireNonNull(ligand, "ligand");
        Objects.requireNonNull(pocket, "pocket");

        if (!Double.isFinite(surfaceDistanceTolerance)
                || surfaceDistanceTolerance < 0.0) {
            throw new IllegalArgumentException(
                    "surfaceDistanceTolerance must be finite and "
                            + "non-negative"
            );
        }

        List<AlphaSphere> spheres = spheres(pocket);

        if (spheres.isEmpty()) {
            return 0.0;
        }

        List<Double> nearest = nearestSurfaceDistances(ligand, spheres);
        long occupied = nearest.stream()
                .filter(distance -> distance <= surfaceDistanceTolerance)
                .count();

        return occupied / (double) nearest.size();
    }

    /**
     * Returns {@code true} when the pocket carries at least one alpha
     * sphere.
     */
    public static boolean hasSpheres(Pocket pocket) {
        Objects.requireNonNull(pocket, "pocket");

        return !spheres(pocket).isEmpty();
    }

    private static List<AlphaSphere> spheres(Pocket pocket) {
        return pocket.alphaSphereSet()
                .map(AlphaSphereSet::spheres)
                .orElse(List.of());
    }

    private static List<Double> nearestSurfaceDistances(
            Ligand ligand,
            List<AlphaSphere> spheres
    ) {
        List<Point3D> positions =
                LigandGeometry.heavyAtomPositions(ligand);

        if (positions.isEmpty()) {
            throw new IllegalArgumentException(
                    "Ligand contains no heavy atoms"
            );
        }

        return positions.stream()
                .map(position -> nearestSurfaceDistance(
                        position,
                        spheres
                ))
                .toList();
    }

    private static double nearestSurfaceDistance(
            Point3D position,
            List<AlphaSphere> spheres
    ) {
        double nearest = Double.MAX_VALUE;

        for (AlphaSphere sphere : spheres) {
            double surfaceDistance = Math.max(
                    0.0,
                    position.distance(sphere.center()) - sphere.radius()
            );
            nearest = Math.min(nearest, surfaceDistance);
        }

        return nearest;
    }
}
