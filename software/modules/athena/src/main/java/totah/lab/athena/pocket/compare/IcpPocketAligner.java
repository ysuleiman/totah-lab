package totah.lab.athena.pocket.compare;

import totah.lab.athena.pocket.geometry.PocketPointCloud;
import totah.lab.gaia.geometry.Point3D;
import totah.lab.gaia.geometry.RigidTransform;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Aligns a candidate pocket point cloud onto a query pocket using iterative
 * closest-point refinement.
 *
 * <p>The candidate is repeatedly matched to nearest query points and then
 * transformed using a rigid-body fit. Reflection is not permitted by the
 * underlying {@link RigidPointAligner}.</p>
 *
 * <p>The returned transform maps the original candidate coordinates directly
 * into the query coordinate system.</p>
 */
public final class IcpPocketAligner implements PocketAligner {

    private static final int DEFAULT_MAX_ITERATIONS = 50;
    private static final double DEFAULT_CONVERGENCE_TOLERANCE = 1.0e-5;
    private static final int MINIMUM_POINT_COUNT = 3;

    private final int maxIterations;
    private final double convergenceTolerance;
    private final RigidPointAligner rigidPointAligner;

    public IcpPocketAligner() {
        this(
                DEFAULT_MAX_ITERATIONS,
                DEFAULT_CONVERGENCE_TOLERANCE,
                new KabschRigidPointAligner()
        );
    }

    public IcpPocketAligner(
            int maxIterations,
            double convergenceTolerance,
            RigidPointAligner rigidPointAligner
    ) {
        if (maxIterations <= 0) {
            throw new IllegalArgumentException(
                    "maxIterations must be greater than zero"
            );
        }

        if (!Double.isFinite(convergenceTolerance)
                || convergenceTolerance <= 0.0) {
            throw new IllegalArgumentException(
                    "convergenceTolerance must be finite and positive"
            );
        }

        this.maxIterations = maxIterations;
        this.convergenceTolerance = convergenceTolerance;
        this.rigidPointAligner = Objects.requireNonNull(
                rigidPointAligner,
                "rigidPointAligner"
        );
    }

    @Override
    public PocketAlignment align(
            PocketPointCloud query,
            PocketPointCloud candidate
    ) {
        validateInputs(query, candidate);

        PocketPointCloud transformedCandidate = candidate;
        RigidTransform accumulatedTransform =
                RigidTransform.identity();

        double previousError = meanSquaredNearestDistance(
                query.points(),
                transformedCandidate.points()
        );

        double currentError = previousError;

        int completedIterations = 0;
        boolean converged = false;

        for (int iteration = 1;
             iteration <= maxIterations;
             iteration++) {

            PointCorrespondence correspondence =
                    nearestCorrespondence(
                            query.points(),
                            transformedCandidate.points()
                    );

            /*
             * Maps the current candidate correspondence points onto the
             * matched query points.
             */
            RigidTransform incrementalTransform =
                    rigidPointAligner.align(
                            correspondence.candidatePoints(),
                            correspondence.queryPoints()
                    );

            transformedCandidate = applyTransform(
                    transformedCandidate,
                    incrementalTransform
            );

            /*
             * If accumulatedTransform maps:
             *
             * original candidate -> current candidate
             *
             * and incrementalTransform maps:
             *
             * current candidate -> next candidate
             *
             * then andThen produces:
             *
             * original candidate -> next candidate
             */
            accumulatedTransform =
                    accumulatedTransform.andThen(
                            incrementalTransform
                    );

            currentError = meanSquaredNearestDistance(
                    query.points(),
                    transformedCandidate.points()
            );

            completedIterations = iteration;

            if (Math.abs(previousError - currentError)
                    <= convergenceTolerance) {
                converged = true;
                break;
            }

            previousError = currentError;
        }

        return new PocketAlignment(
                query,
                transformedCandidate,
                accumulatedTransform,
                Math.sqrt(currentError),
                completedIterations,
                converged
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
                    "Cannot align different geometry bases: "
                            + query.basis()
                            + " vs "
                            + candidate.basis()
            );
        }

        if (query.size() < MINIMUM_POINT_COUNT) {
            throw new IllegalArgumentException(
                    "Query pocket must contain at least "
                            + MINIMUM_POINT_COUNT
                            + " points, but contained "
                            + query.size()
            );
        }

        if (candidate.size() < MINIMUM_POINT_COUNT) {
            throw new IllegalArgumentException(
                    "Candidate pocket must contain at least "
                            + MINIMUM_POINT_COUNT
                            + " points, but contained "
                            + candidate.size()
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

    private static PointCorrespondence nearestCorrespondence(
            List<Point3D> queryPoints,
            List<Point3D> candidatePoints
    ) {
        List<Point3D> matchedQuery =
                new ArrayList<>(candidatePoints.size());

        List<Point3D> matchedCandidate =
                new ArrayList<>(candidatePoints.size());

        for (Point3D candidatePoint : candidatePoints) {
            matchedCandidate.add(candidatePoint);

            matchedQuery.add(
                    nearestPoint(
                            candidatePoint,
                            queryPoints
                    )
            );
        }

        return new PointCorrespondence(
                matchedQuery,
                matchedCandidate
        );
    }

    private static Point3D nearestPoint(
            Point3D source,
            List<Point3D> targets
    ) {
        Point3D nearest = null;
        double minimumSquaredDistance =
                Double.POSITIVE_INFINITY;

        for (Point3D target : targets) {
            double distanceSquared =
                    source.distanceSquared(target);

            if (distanceSquared < minimumSquaredDistance) {
                minimumSquaredDistance = distanceSquared;
                nearest = target;
            }
        }

        if (nearest == null) {
            throw new IllegalArgumentException(
                    "Target point cloud must not be empty"
            );
        }

        return nearest;
    }

    private static PocketPointCloud applyTransform(
            PocketPointCloud cloud,
            RigidTransform transform
    ) {
        return new PocketPointCloud(
                transform.apply(cloud.points()),
                cloud.basis()
        );
    }

    private static double meanSquaredNearestDistance(
            List<Point3D> queryPoints,
            List<Point3D> candidatePoints
    ) {
        double total = 0.0;

        for (Point3D candidatePoint : candidatePoints) {
            double minimumSquaredDistance =
                    Double.POSITIVE_INFINITY;

            for (Point3D queryPoint : queryPoints) {
                minimumSquaredDistance = Math.min(
                        minimumSquaredDistance,
                        candidatePoint.distanceSquared(queryPoint)
                );
            }

            total += minimumSquaredDistance;
        }

        return total / candidatePoints.size();
    }

    private record PointCorrespondence(
            List<Point3D> queryPoints,
            List<Point3D> candidatePoints
    ) {

        private PointCorrespondence {
            queryPoints = List.copyOf(
                    Objects.requireNonNull(
                            queryPoints,
                            "queryPoints"
                    )
            );

            candidatePoints = List.copyOf(
                    Objects.requireNonNull(
                            candidatePoints,
                            "candidatePoints"
                    )
            );

            if (queryPoints.size() != candidatePoints.size()) {
                throw new IllegalArgumentException(
                        "Correspondence point lists must have equal sizes"
                );
            }

            if (queryPoints.size() < MINIMUM_POINT_COUNT) {
                throw new IllegalArgumentException(
                        "At least "
                                + MINIMUM_POINT_COUNT
                                + " correspondences are required"
                );
            }
        }
    }
}