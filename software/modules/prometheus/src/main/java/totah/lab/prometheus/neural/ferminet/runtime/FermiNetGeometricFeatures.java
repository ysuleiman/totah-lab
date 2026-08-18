package totah.lab.prometheus.neural.ferminet.runtime;

import java.util.Objects;

import totah.lab.prometheus.molecular.Molecule;
import totah.lab.prometheus.variational.QuantumCoordinates;

/**
 * Reference-aligned geometric input features for Prometheus FermiNet-v1.
 *
 * <p>Aligned to DeepMind FermiNet commit
 * c4312c315dda1c5728994ba89629744f71c6eb66.
 *
 * <p>Feature conventions:
 *
 * <ul>
 *   <li>electron-nuclear:
 *       [distance, dx, dy, dz] for each nucleus</li>
 *   <li>electron-electron:
 *       [distance, dx, dy, dz]</li>
 *   <li>electron-electron vector orientation:
 *       r_j - r_i</li>
 *   <li>self electron-electron entries:
 *       exactly zero</li>
 * </ul>
 */
public final class FermiNetGeometricFeatures {

    private FermiNetGeometricFeatures() {
    }

    public static Features build(
            Molecule molecule,
            QuantumCoordinates coordinates) {

        Objects.requireNonNull(
                molecule,
                "molecule");

        Objects.requireNonNull(
                coordinates,
                "coordinates");

        int electronCount =
                molecule.electrons().value();

        if (coordinates.particles().size()
                != electronCount) {

            throw new IllegalArgumentException(
                    "electron count mismatch");
        }

        int nucleusCount =
                molecule.nuclei().size();

        /*
         * Reference one-electron FermiNet features:
         *
         * for every electron i and nucleus A:
         *
         *   |r_i - R_A|,
         *   x_i - X_A,
         *   y_i - Y_A,
         *   z_i - Z_A
         *
         * flattened nucleus-by-nucleus.
         */
        double[][] oneElectron =
                new double[electronCount]
                        [4 * nucleusCount];

        for (int electron = 0;
             electron < electronCount;
             electron++) {

            var electronPosition =
                    coordinates.particles()
                            .get(electron);

            for (int nucleus = 0;
                 nucleus < nucleusCount;
                 nucleus++) {

                var nucleusPosition =
                        molecule.nuclei()
                                .get(nucleus)
                                .position()
                                .inBohr();

                double dx =
                        electronPosition.xBohr()
                                - nucleusPosition.x();

                double dy =
                        electronPosition.yBohr()
                                - nucleusPosition.y();

                double dz =
                        electronPosition.zBohr()
                                - nucleusPosition.z();

                int base =
                        4 * nucleus;

                /*
                 * DeepMind order:
                 *
                 * [r, dx, dy, dz]
                 */
                oneElectron[electron][base] =
                        norm(dx, dy, dz);

                oneElectron[electron][base + 1] =
                        dx;

                oneElectron[electron][base + 2] =
                        dy;

                oneElectron[electron][base + 3] =
                        dz;
            }
        }

        /*
         * Reference two-electron FermiNet features:
         *
         * ee[i][j] = r_j - r_i
         *
         * followed by:
         *
         * [distance, dx, dy, dz]
         *
         * The diagonal is exactly zero.
         */
        double[][][] twoElectron =
                new double[electronCount]
                        [electronCount]
                        [4];

        for (int i = 0;
             i < electronCount;
             i++) {

            var ri =
                    coordinates.particles()
                            .get(i);

            for (int j = 0;
                 j < electronCount;
                 j++) {

                if (i == j) {

                    /*
                     * Java arrays are already zero-filled, but keep
                     * the reference behavior explicit.
                     */
                    twoElectron[i][j][0] = 0.0;
                    twoElectron[i][j][1] = 0.0;
                    twoElectron[i][j][2] = 0.0;
                    twoElectron[i][j][3] = 0.0;

                    continue;
                }

                var rj =
                        coordinates.particles()
                                .get(j);

                /*
                 * IMPORTANT:
                 *
                 * Reference orientation is r_j - r_i.
                 */
                double dx =
                        rj.xBohr()
                                - ri.xBohr();

                double dy =
                        rj.yBohr()
                                - ri.yBohr();

                double dz =
                        rj.zBohr()
                                - ri.zBohr();

                twoElectron[i][j][0] =
                        norm(dx, dy, dz);

                twoElectron[i][j][1] =
                        dx;

                twoElectron[i][j][2] =
                        dy;

                twoElectron[i][j][3] =
                        dz;
            }
        }

        return new Features(
                oneElectron,
                twoElectron);
    }

    private static double norm(
            double x,
            double y,
            double z) {

        return Math.sqrt(
                x * x
                        + y * y
                        + z * z);
    }

    /**
     * Immutable reference feature arrays.
     */
    public record Features(
            double[][] oneElectron,
            double[][][] twoElectron) {

        public Features {

            Objects.requireNonNull(
                    oneElectron,
                    "oneElectron");

            Objects.requireNonNull(
                    twoElectron,
                    "twoElectron");

            oneElectron =
                    deepCopy(oneElectron);

            twoElectron =
                    deepCopy(twoElectron);
        }

        @Override
        public double[][] oneElectron() {
            return deepCopy(oneElectron);
        }

        @Override
        public double[][][] twoElectron() {
            return deepCopy(twoElectron);
        }

        private static double[][] deepCopy(
                double[][] source) {

            double[][] copy =
                    new double[source.length][];

            for (int i = 0;
                 i < source.length;
                 i++) {

                copy[i] =
                        source[i].clone();
            }

            return copy;
        }

        private static double[][][] deepCopy(
                double[][][] source) {

            double[][][] copy =
                    new double[source.length][][];

            for (int i = 0;
                 i < source.length;
                 i++) {

                copy[i] =
                        new double[source[i].length][];

                for (int j = 0;
                     j < source[i].length;
                     j++) {

                    copy[i][j] =
                            source[i][j].clone();
                }
            }

            return copy;
        }
    }
}