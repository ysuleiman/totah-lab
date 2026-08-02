package totah.lab.athena.pocket.geometry;

import totah.lab.gaia.geometry.Point3D;

import java.util.List;
import java.util.Objects;

/**
 * Distributional shape statistics over a pocket's heavy-atom positions.
 *
 * <p>All distances are in angstroms and measured against the centroid of
 * the given positions.</p>
 */
public record PocketShapeStatistics(
        int heavyAtomCount,
        double maximumCentroidDistance,
        double meanCentroidDistance,
        double percentile95CentroidDistance,
        double maximumPairwiseSpan,
        double radiusOfGyration) {

    public static PocketShapeStatistics of(List<Point3D> positions) {
        Objects.requireNonNull(positions, "positions");
        if (positions.isEmpty()) {
            throw new IllegalArgumentException(
                    "positions must not be empty");
        }
        if (positions.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException(
                    "positions must not contain null elements");
        }
        Point3D centroid = centroid(positions);
        List<Double> centroidDistances = positions.stream()
                .map(position -> position.distance(centroid))
                .sorted()
                .toList();
        double meanCentroidDistance = centroidDistances.stream()
                .mapToDouble(Double::doubleValue)
                .average()
                .orElseThrow();
        double radiusOfGyration = Math.sqrt(positions.stream()
                .mapToDouble(position -> {
                    double distance = position.distance(centroid);
                    return distance * distance;
                })
                .average()
                .orElseThrow());
        return new PocketShapeStatistics(
                positions.size(),
                centroidDistances.get(centroidDistances.size() - 1),
                meanCentroidDistance,
                percentileNearestRank(centroidDistances, 0.95),
                maximumPairwiseDistance(positions),
                radiusOfGyration);
    }

    private static Point3D centroid(List<Point3D> positions) {
        return new Point3D(
                positions.stream().mapToDouble(Point3D::x)
                        .average().orElseThrow(),
                positions.stream().mapToDouble(Point3D::y)
                        .average().orElseThrow(),
                positions.stream().mapToDouble(Point3D::z)
                        .average().orElseThrow());
    }

    private static double percentileNearestRank(
            List<Double> sorted,
            double percentile) {
        int index = Math.max(
                0,
                (int) Math.ceil(percentile * sorted.size()) - 1);
        return sorted.get(index);
    }

    private static double maximumPairwiseDistance(
            List<Point3D> positions) {
        double maximum = 0.0;
        for (int first = 0; first < positions.size(); first++) {
            for (int second = first + 1;
                 second < positions.size();
                 second++) {
                maximum = Math.max(
                        maximum,
                        positions.get(first)
                                .distance(positions.get(second)));
            }
        }
        return maximum;
    }
}
