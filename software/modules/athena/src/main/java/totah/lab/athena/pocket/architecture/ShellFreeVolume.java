package totah.lab.athena.pocket.architecture;

import totah.lab.gaia.geometry.Point3D;

import java.util.List;
import java.util.Objects;

/**
 * Deterministic local free-volume proxy: the fraction of 14 fixed
 * sample points (6 axis directions plus 8 cube corners) on a
 * probe-radius shell around a center that have NO atom within the
 * probe radius. Fixed pattern, no randomness; shared by the
 * ligand-space and loop-region analyses.
 */
final class ShellFreeVolume {

    private static final double INV_SQRT_3 = 1.0 / Math.sqrt(3.0);

    /** Fixed shell-sampling directions: 6 axes + 8 cube corners. */
    private static final double[][] DIRECTIONS = {
            {1, 0, 0}, {-1, 0, 0},
            {0, 1, 0}, {0, -1, 0},
            {0, 0, 1}, {0, 0, -1},
            {INV_SQRT_3, INV_SQRT_3, INV_SQRT_3},
            {INV_SQRT_3, INV_SQRT_3, -INV_SQRT_3},
            {INV_SQRT_3, -INV_SQRT_3, INV_SQRT_3},
            {INV_SQRT_3, -INV_SQRT_3, -INV_SQRT_3},
            {-INV_SQRT_3, INV_SQRT_3, INV_SQRT_3},
            {-INV_SQRT_3, INV_SQRT_3, -INV_SQRT_3},
            {-INV_SQRT_3, -INV_SQRT_3, INV_SQRT_3},
            {-INV_SQRT_3, -INV_SQRT_3, -INV_SQRT_3}
    };

    private ShellFreeVolume() {
    }

    /**
     * Free fraction in [0, 1]; 1.0 when {@code atoms} is empty.
     */
    static double freeFraction(
            Point3D center,
            List<Point3D> atoms,
            double probeRadiusAngstroms
    ) {
        Objects.requireNonNull(center, "center");
        Objects.requireNonNull(atoms, "atoms");

        int free = 0;

        for (double[] direction : DIRECTIONS) {
            Point3D sample = new Point3D(
                    center.x() + direction[0] * probeRadiusAngstroms,
                    center.y() + direction[1] * probeRadiusAngstroms,
                    center.z() + direction[2] * probeRadiusAngstroms
            );

            boolean occupied = false;

            for (Point3D atom : atoms) {
                if (sample.distance(atom) <= probeRadiusAngstroms) {
                    occupied = true;
                    break;
                }
            }

            if (!occupied) {
                free++;
            }
        }

        return free / (double) DIRECTIONS.length;
    }
}
