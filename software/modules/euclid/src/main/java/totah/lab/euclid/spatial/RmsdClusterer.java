package totah.lab.euclid.spatial;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Pairwise heavy-atom RMSD clustering of docked poses without
 * superposition: all poses of a run share the receptor frame, so the
 * plain coordinate RMSD over corresponding atoms is meaningful.
 *
 * <p>Complete-linkage agglomerative clustering: clusters merge while
 * the maximum pairwise RMSD between their members stays within the
 * threshold.</p>
 */
public final class RmsdClusterer {

    /**
     * Clusters of pose indices (indices into the input list), sorted
     * by size descending; ties keep the lowest member index first.
     */
    public record Clustering(List<List<Integer>> clusters) {

        public Clustering {
            clusters = clusters.stream()
                    .map(List::copyOf)
                    .toList();
        }

        public int clusterCount() {
            return clusters.size();
        }

        public int largestClusterSize() {
            return clusters.isEmpty() ? 0 : clusters.getFirst().size();
        }

        /**
         * Members of the largest cluster (empty when there are no
         * poses).
         */
        public List<Integer> topCluster() {
            return clusters.isEmpty()
                    ? List.of()
                    : clusters.getFirst();
        }
    }

    /**
     * Clusters the given poses (each a list of heavy-atom coordinates)
     * at the RMSD threshold. All poses must have the same atom count.
     */
    public Clustering cluster(
            List<List<double[]>> poses,
            double thresholdAngstroms
    ) {
        Objects.requireNonNull(poses, "poses");
        if (!Double.isFinite(thresholdAngstroms)
                || thresholdAngstroms < 0.0) {
            throw new IllegalArgumentException(
                    "RMSD threshold must be finite and non-negative"
            );
        }
        if (poses.isEmpty()) {
            return new Clustering(List.of());
        }

        int count = poses.size();
        double[][] rmsd = new double[count][count];
        for (int i = 0; i < count; i++) {
            for (int j = i + 1; j < count; j++) {
                double value = rmsd(poses.get(i), poses.get(j));
                rmsd[i][j] = value;
                rmsd[j][i] = value;
            }
        }

        List<List<Integer>> clusters = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            List<Integer> singleton = new ArrayList<>();
            singleton.add(i);
            clusters.add(singleton);
        }

        while (true) {
            int bestA = -1;
            int bestB = -1;
            double bestDistance = Double.MAX_VALUE;
            for (int a = 0; a < clusters.size(); a++) {
                for (int b = a + 1; b < clusters.size(); b++) {
                    double distance = completeLinkage(
                            clusters.get(a),
                            clusters.get(b),
                            rmsd
                    );
                    if (distance < bestDistance) {
                        bestDistance = distance;
                        bestA = a;
                        bestB = b;
                    }
                }
            }
            if (bestA < 0 || bestDistance > thresholdAngstroms) {
                break;
            }
            clusters.get(bestA).addAll(clusters.get(bestB));
            clusters.remove(bestB);
        }

        clusters.sort(Comparator
                .<List<Integer>>comparingInt(List::size)
                .reversed()
                .thenComparingInt(List::getFirst));
        return new Clustering(clusters);
    }

    /**
     * Heavy-atom RMSD between two poses over corresponding atoms,
     * without superposition.
     */
    public static double rmsd(List<double[]> first, List<double[]> second) {
        Objects.requireNonNull(first, "first");
        Objects.requireNonNull(second, "second");
        if (first.size() != second.size()) {
            throw new IllegalArgumentException(
                    "Pose atom counts differ: " + first.size()
                            + " vs " + second.size()
            );
        }
        if (first.isEmpty()) {
            throw new IllegalArgumentException("Pose has no atoms");
        }
        double sum = 0.0;
        for (int i = 0; i < first.size(); i++) {
            double[] a = first.get(i);
            double[] b = second.get(i);
            requireCoordinate(a, "first", i);
            requireCoordinate(b, "second", i);
            double dx = a[0] - b[0];
            double dy = a[1] - b[1];
            double dz = a[2] - b[2];
            sum += dx * dx + dy * dy + dz * dz;
        }
        return Math.sqrt(sum / first.size());
    }

    private static void requireCoordinate(
            double[] coordinate,
            String poseName,
            int atomIndex
    ) {
        if (coordinate == null || coordinate.length != 3) {
            throw new IllegalArgumentException(
                    poseName + " pose atom " + atomIndex
                            + " must have exactly three coordinates"
            );
        }
        for (double value : coordinate) {
            if (!Double.isFinite(value)) {
                throw new IllegalArgumentException(
                        poseName + " pose atom " + atomIndex
                                + " contains a non-finite coordinate"
                );
            }
        }
    }

    private static double completeLinkage(
            List<Integer> first,
            List<Integer> second,
            double[][] rmsd
    ) {
        double maximum = 0.0;
        for (int a : first) {
            for (int b : second) {
                maximum = Math.max(maximum, rmsd[a][b]);
            }
        }
        return maximum;
    }
}
