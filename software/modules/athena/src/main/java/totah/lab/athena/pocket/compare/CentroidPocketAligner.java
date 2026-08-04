package totah.lab.athena.pocket.compare;

import totah.lab.athena.pocket.geometry.PocketPointCloud;
import totah.lab.gaia.geometry.Point3D;
import totah.lab.gaia.geometry.RigidTransform;

import java.util.List;
import java.util.Objects;

/**
 * Aligns a candidate pocket onto a query pocket by matching their centroids.
 *
 * <p>This removes translational displacement but does not correct rotational
 * differences.</p>
 *
 * <p>The returned transform maps points from the original candidate coordinate
 * system into the original query coordinate system.</p>
 */
public final class CentroidPocketAligner implements PocketAligner {

    private static final int MINIMUM_POINT_COUNT = 1;

    @Override
    public PocketAlignment align(
            PocketPointCloud query,
            PocketPointCloud candidate
    ) {
        validateInputs(query, candidate);

        Point3D queryCentroid = query.centroid();
        Point3D candidateCentroid = candidate.centroid();

        /*
         * Move the candidate centroid directly onto the query centroid:
         *
         * translation = queryCentroid - candidateCentroid
         */
        RigidTransform transform = RigidTransform.translation(
                queryCentroid.x() - candidateCentroid.x(),
                queryCentroid.y() - candidateCentroid.y(),
                queryCentroid.z() - candidateCentroid.z()
        );

        PocketPointCloud alignedCandidate = transformed(
                candidate,
                transform
        );

        double rmsd = nearestNeighborRmsd(
                query.points(),
                alignedCandidate.points()
        );

        return new PocketAlignment(
                query,
                alignedCandidate,
                transform,
                rmsd,
                0,
                true
        );
    }

    private static void validateInputs(
            PocketPointCloud query,
            PocketPointCloud candidate
    ) {
        Objects.requireNonNull(query, "query");
        Objects.requireNonNull(candidate, "candidate");

        if (query.basis() != candidate.basis()) {
            throw new IllegalArgumentException(
                    "Cannot align pockets represented by different bases: "
                            + query.basis()
                            + " vs "
                            + candidate.basis()
            );
        }

        if (query.size() < MINIMUM_POINT_COUNT) {
            throw new IllegalArgumentException(
                    "Query pocket must contain at least one point"
            );
        }

        if (candidate.size() < MINIMUM_POINT_COUNT) {
            throw new IllegalArgumentException(
                    "Candidate pocket must contain at least one point"
            );
        }

        requireFinitePoints(
                query.points(),
                "query"
        );

        requireFinitePoints(
                candidate.points(),
                "candidate"
        );
    }

    private static void requireFinitePoints(
            List<Point3D> points,
            String name
    ) {
        for (int index = 0; index < points.size(); index++) {
            Point3D point = Objects.requireNonNull(
                    points.get(index),
                    name + ".points[" + index + "]"
            );

            if (!Double.isFinite(point.x())
                    || !Double.isFinite(point.y())
                    || !Double.isFinite(point.z())) {
                throw new IllegalArgumentException(
                        name
                                + ".points["
                                + index
                                + "] contains non-finite coordinates: "
                                + point
                );
            }
        }
    }

    private static PocketPointCloud transformed(
            PocketPointCloud cloud,
            RigidTransform transform
    ) {
        return new PocketPointCloud(
                transform.apply(cloud.points()),
                cloud.basis()
        );
    }

    /**
     * Computes one-way candidate-to-query nearest-neighbor RMSD.
     *
     * <p>This matches the error definition used by the ICP aligner.</p>
     */
    private static double nearestNeighborRmsd(
            List<Point3D> queryPoints,
            List<Point3D> candidatePoints
    ) {
        double totalSquaredDistance = 0.0;

        for (Point3D candidatePoint : candidatePoints) {
            double minimumSquaredDistance =
                    Double.POSITIVE_INFINITY;

            for (Point3D queryPoint : queryPoints) {
                minimumSquaredDistance = Math.min(
                        minimumSquaredDistance,
                        candidatePoint.distanceSquared(queryPoint)
                );
            }

            totalSquaredDistance += minimumSquaredDistance;
        }

        return Math.sqrt(
                totalSquaredDistance / candidatePoints.size()
        );
    }
}