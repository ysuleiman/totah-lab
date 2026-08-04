package totah.lab.athena.pocket.align;

import org.apache.commons.math3.linear.Array2DRowRealMatrix;
import org.apache.commons.math3.linear.EigenDecomposition;
import org.apache.commons.math3.linear.RealVector;
import totah.lab.athena.pocket.compare.PocketAligner;
import totah.lab.athena.pocket.compare.PocketAlignment;
import totah.lab.athena.pocket.geometry.PocketPointCloud;
import totah.lab.gaia.geometry.Point3D;
import totah.lab.gaia.geometry.RigidTransform;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Computes a coarse rigid-body alignment between two pocket point clouds by
 * matching their principal-axis frames.
 *
 * <p>The candidate pocket is transformed onto the fixed query pocket.</p>
 *
 * <p>The aligner:</p>
 *
 * <ol>
 *     <li>Computes each point cloud's centroid and covariance matrix.</li>
 *     <li>Extracts a right-handed principal-axis frame.</li>
 *     <li>Evaluates all four orientation-preserving PCA sign combinations.</li>
 *     <li>Selects the candidate with the lowest symmetric nearest-neighbor
 *     RMSD.</li>
 * </ol>
 *
 * <p>This provides a coarse alignment suitable for use before ICP
 * refinement.</p>
 */
public final class PrincipalAxisPocketAligner
        implements PocketAligner {

    private static final int MINIMUM_POINT_COUNT = 6;

    private static final double DEFAULT_DEGENERACY_TOLERANCE =
            1.0e-6;

    private static final double DEFAULT_RANK_TOLERANCE =
            1.0e-8;

    private static final double VECTOR_NORM_TOLERANCE =
            1.0e-12;

    private static final double NEGATIVE_EIGENVALUE_TOLERANCE =
            1.0e-12;

    /**
     * PCA eigenvector signs are arbitrary. These are the four sign
     * combinations that preserve handedness and therefore do not introduce
     * reflection.
     */
    private static final int[][] PROPER_AXIS_SIGNS = {
            {1, 1, 1},
            {1, -1, -1},
            {-1, 1, -1},
            {-1, -1, 1}
    };

    private final double degeneracyTolerance;
    private final double rankTolerance;

    public PrincipalAxisPocketAligner() {
        this(
                DEFAULT_DEGENERACY_TOLERANCE,
                DEFAULT_RANK_TOLERANCE
        );
    }

    public PrincipalAxisPocketAligner(
            double degeneracyTolerance,
            double rankTolerance
    ) {
        this.degeneracyTolerance = requireNonNegativeFinite(
                degeneracyTolerance,
                "degeneracyTolerance"
        );

        this.rankTolerance = requireNonNegativeFinite(
                rankTolerance,
                "rankTolerance"
        );
    }

    /**
     * Aligns {@code candidate} onto {@code query}.
     */
    @Override
    public PocketAlignment align(
            PocketPointCloud query,
            PocketPointCloud candidate
    ) {
        validateInputs(query, candidate);

        List<Point3D> targetPoints = query.points();
        List<Point3D> sourcePoints = candidate.points();

        PrincipalFrame sourceFrame =
                principalFrame(sourcePoints);

        PrincipalFrame targetFrame =
                principalFrame(targetPoints);

        Candidate best = null;

        for (int[] signs : PROPER_AXIS_SIGNS) {
            Candidate current = createCandidate(
                    sourcePoints,
                    targetPoints,
                    sourceFrame,
                    targetFrame,
                    signs
            );

            if (best == null
                    || current.rmsd() < best.rmsd()) {
                best = current;
            }
        }

        if (best == null) {
            throw new IllegalStateException(
                    "No principal-axis alignment candidate was generated"
            );
        }

        PocketPointCloud alignedCandidate =
                new PocketPointCloud(
                        best.transform().apply(candidate.points()),
                        candidate.basis()
                );

        return new PocketAlignment(
                query,
                alignedCandidate,
                best.transform(),
                best.rmsd(),
                0,
                true
        );
    }

    private Candidate createCandidate(
            List<Point3D> sourcePoints,
            List<Point3D> targetPoints,
            PrincipalFrame sourceFrame,
            PrincipalFrame targetFrame,
            int[] signs
    ) {
        double[][] signedTargetAxes = applyColumnSigns(
                targetFrame.axes(),
                signs
        );

        /*
         * The principal axes are stored as matrix columns.
         *
         * R = Vtarget * Vsource^T
         */
        double[][] rotation = multiply(
                signedTargetAxes,
                transpose(sourceFrame.axes())
        );

        /*
         * t = targetCentroid - R * sourceCentroid
         */
        Point3D rotatedSourceCentroid = rotate(
                rotation,
                sourceFrame.centroid()
        );

        Point3D translation = subtract(
                targetFrame.centroid(),
                rotatedSourceCentroid
        );

        RigidTransform transform = new RigidTransform(
                rotation,
                translation
        );

        double rmsd = symmetricNearestNeighborRmsd(
                sourcePoints,
                targetPoints,
                transform
        );

        return new Candidate(
                transform,
                rmsd
        );
    }

    private PrincipalFrame principalFrame(
            List<Point3D> points
    ) {
        Point3D centroid = centroid(points);

        double[][] covariance = covariance(
                points,
                centroid
        );

        EigenDecomposition decomposition =
                new EigenDecomposition(
                        new Array2DRowRealMatrix(
                                covariance,
                                false
                        )
                );

        List<EigenPair> pairs = new ArrayList<>(3);

        for (int index = 0; index < 3; index++) {
            double eigenvalue = sanitizeEigenvalue(
                    decomposition.getRealEigenvalue(index)
            );

            RealVector eigenvector =
                    decomposition.getEigenvector(index);

            if (eigenvector == null
                    || eigenvector.getDimension() != 3) {
                throw new IllegalArgumentException(
                        "Pocket covariance matrix does not define a complete "
                                + "three-dimensional real eigenbasis"
                );
            }

            double[] vector = {
                    eigenvector.getEntry(0),
                    eigenvector.getEntry(1),
                    eigenvector.getEntry(2)
            };

            pairs.add(
                    new EigenPair(
                            eigenvalue,
                            normalize(vector)
                    )
            );
        }

        pairs.sort(
                Comparator.comparingDouble(
                        EigenPair::eigenvalue
                ).reversed()
        );

        double firstEigenvalue =
                pairs.get(0).eigenvalue();

        double secondEigenvalue =
                pairs.get(1).eigenvalue();

        double thirdEigenvalue =
                pairs.get(2).eigenvalue();

        if (firstEigenvalue <= 0.0) {
            throw new IllegalArgumentException(
                    "Pocket point cloud has no measurable spatial variance"
            );
        }

        double relativeSecondVariance =
                secondEigenvalue / firstEigenvalue;

        double relativeThirdVariance =
                thirdEigenvalue / firstEigenvalue;

        if (relativeSecondVariance <= rankTolerance) {
            throw new IllegalArgumentException(
                    "Pocket point cloud is effectively collinear and does not "
                            + "define a stable principal-axis frame"
            );
        }

        boolean planar =
                relativeThirdVariance <= rankTolerance;

        boolean repeatedEigenvalues =
                approximatelyEqualEigenvalues(
                        firstEigenvalue,
                        secondEigenvalue
                )
                        || approximatelyEqualEigenvalues(
                        secondEigenvalue,
                        thirdEigenvalue
                );

        /*
         * These diagnostics are retained internally because they are useful
         * when debugging unstable PCA frames, even though PocketAlignment
         * does not expose algorithm-specific diagnostics.
         */
        boolean degenerate =
                planar || repeatedEigenvalues;

        double[] firstAxis = normalize(
                pairs.get(0).eigenvector()
        );

        double[] secondAxis = subtractProjection(
                pairs.get(1).eigenvector(),
                firstAxis
        );

        secondAxis = normalize(secondAxis);

        double[] thirdAxis = normalize(
                cross(firstAxis, secondAxis)
        );

        /*
         * Recompute the second axis to produce an explicitly orthonormal,
         * right-handed frame.
         */
        secondAxis = normalize(
                cross(thirdAxis, firstAxis)
        );

        return new PrincipalFrame(
                centroid,
                matrixFromColumns(
                        firstAxis,
                        secondAxis,
                        thirdAxis
                ),
                degenerate,
                planar
        );
    }

    private boolean approximatelyEqualEigenvalues(
            double first,
            double second
    ) {
        double scale = Math.max(
                1.0,
                Math.max(
                        Math.abs(first),
                        Math.abs(second)
                )
        );

        return Math.abs(first - second)
                <= degeneracyTolerance * scale;
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

        validatePoints(
                query.points(),
                "query.points"
        );

        validatePoints(
                candidate.points(),
                "candidate.points"
        );
    }

    private static void validatePoints(
            List<Point3D> points,
            String parameterName
    ) {
        Objects.requireNonNull(points, parameterName);

        if (points.size() < MINIMUM_POINT_COUNT) {
            throw new IllegalArgumentException(
                    parameterName
                            + " must contain at least "
                            + MINIMUM_POINT_COUNT
                            + " points, but contained "
                            + points.size()
            );
        }

        for (int index = 0; index < points.size(); index++) {
            Point3D point = Objects.requireNonNull(
                    points.get(index),
                    parameterName + "[" + index + "]"
            );

            if (!Double.isFinite(point.x())
                    || !Double.isFinite(point.y())
                    || !Double.isFinite(point.z())) {
                throw new IllegalArgumentException(
                        parameterName
                                + "["
                                + index
                                + "] contains non-finite coordinates: "
                                + point
                );
            }
        }
    }

    private static Point3D centroid(
            List<Point3D> points
    ) {
        CompensatedSum x = new CompensatedSum();
        CompensatedSum y = new CompensatedSum();
        CompensatedSum z = new CompensatedSum();

        for (Point3D point : points) {
            x.add(point.x());
            y.add(point.y());
            z.add(point.z());
        }

        double inverseCount =
                1.0 / points.size();

        return new Point3D(
                x.value() * inverseCount,
                y.value() * inverseCount,
                z.value() * inverseCount
        );
    }

    private static double[][] covariance(
            List<Point3D> points,
            Point3D centroid
    ) {
        CompensatedSum xx = new CompensatedSum();
        CompensatedSum xy = new CompensatedSum();
        CompensatedSum xz = new CompensatedSum();

        CompensatedSum yy = new CompensatedSum();
        CompensatedSum yz = new CompensatedSum();

        CompensatedSum zz = new CompensatedSum();

        for (Point3D point : points) {
            double x = point.x() - centroid.x();
            double y = point.y() - centroid.y();
            double z = point.z() - centroid.z();

            xx.add(x * x);
            xy.add(x * y);
            xz.add(x * z);

            yy.add(y * y);
            yz.add(y * z);

            zz.add(z * z);
        }

        double inverseCount =
                1.0 / points.size();

        return new double[][]{
                {
                        xx.value() * inverseCount,
                        xy.value() * inverseCount,
                        xz.value() * inverseCount
                },
                {
                        xy.value() * inverseCount,
                        yy.value() * inverseCount,
                        yz.value() * inverseCount
                },
                {
                        xz.value() * inverseCount,
                        yz.value() * inverseCount,
                        zz.value() * inverseCount
                }
        };
    }

    private static double symmetricNearestNeighborRmsd(
            List<Point3D> source,
            List<Point3D> target,
            RigidTransform transform
    ) {
        List<Point3D> transformedSource =
                transform.apply(source);

        double sourceToTarget =
                meanMinimumSquaredDistance(
                        transformedSource,
                        target
                );

        double targetToSource =
                meanMinimumSquaredDistance(
                        target,
                        transformedSource
                );

        return Math.sqrt(
                0.5 * (
                        sourceToTarget
                                + targetToSource
                )
        );
    }

    private static double meanMinimumSquaredDistance(
            List<Point3D> queryPoints,
            List<Point3D> referencePoints
    ) {
        CompensatedSum total =
                new CompensatedSum();

        for (Point3D queryPoint : queryPoints) {
            double minimumSquaredDistance =
                    Double.POSITIVE_INFINITY;

            for (Point3D referencePoint : referencePoints) {
                double distanceSquared =
                        queryPoint.distanceSquared(
                                referencePoint
                        );

                if (distanceSquared < minimumSquaredDistance) {
                    minimumSquaredDistance =
                            distanceSquared;
                }
            }

            total.add(minimumSquaredDistance);
        }

        return total.value() / queryPoints.size();
    }

    private static Point3D rotate(
            double[][] rotation,
            Point3D point
    ) {
        return new Point3D(
                rotation[0][0] * point.x()
                        + rotation[0][1] * point.y()
                        + rotation[0][2] * point.z(),

                rotation[1][0] * point.x()
                        + rotation[1][1] * point.y()
                        + rotation[1][2] * point.z(),

                rotation[2][0] * point.x()
                        + rotation[2][1] * point.y()
                        + rotation[2][2] * point.z()
        );
    }

    private static Point3D subtract(
            Point3D first,
            Point3D second
    ) {
        return new Point3D(
                first.x() - second.x(),
                first.y() - second.y(),
                first.z() - second.z()
        );
    }

    private static double[][] applyColumnSigns(
            double[][] matrix,
            int[] signs
    ) {
        double[][] result =
                new double[3][3];

        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 3; column++) {
                result[row][column] =
                        matrix[row][column]
                                * signs[column];
            }
        }

        return result;
    }

    private static double[][] matrixFromColumns(
            double[] first,
            double[] second,
            double[] third
    ) {
        return new double[][]{
                {
                        first[0],
                        second[0],
                        third[0]
                },
                {
                        first[1],
                        second[1],
                        third[1]
                },
                {
                        first[2],
                        second[2],
                        third[2]
                }
        };
    }

    private static double[][] transpose(
            double[][] matrix
    ) {
        return new double[][]{
                {
                        matrix[0][0],
                        matrix[1][0],
                        matrix[2][0]
                },
                {
                        matrix[0][1],
                        matrix[1][1],
                        matrix[2][1]
                },
                {
                        matrix[0][2],
                        matrix[1][2],
                        matrix[2][2]
                }
        };
    }

    private static double[][] multiply(
            double[][] first,
            double[][] second
    ) {
        double[][] result =
                new double[3][3];

        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 3; column++) {
                result[row][column] =
                        first[row][0] * second[0][column]
                                + first[row][1] * second[1][column]
                                + first[row][2] * second[2][column];
            }
        }

        return result;
    }

    private static double[] subtractProjection(
            double[] vector,
            double[] unitAxis
    ) {
        double projection =
                dot(vector, unitAxis);

        return new double[]{
                vector[0] - projection * unitAxis[0],
                vector[1] - projection * unitAxis[1],
                vector[2] - projection * unitAxis[2]
        };
    }

    private static double dot(
            double[] first,
            double[] second
    ) {
        return first[0] * second[0]
                + first[1] * second[1]
                + first[2] * second[2];
    }

    private static double[] cross(
            double[] first,
            double[] second
    ) {
        return new double[]{
                first[1] * second[2]
                        - first[2] * second[1],

                first[2] * second[0]
                        - first[0] * second[2],

                first[0] * second[1]
                        - first[1] * second[0]
        };
    }

    private static double[] normalize(
            double[] vector
    ) {
        Objects.requireNonNull(vector, "vector");

        if (vector.length != 3) {
            throw new IllegalArgumentException(
                    "A three-dimensional vector is required"
            );
        }

        double normSquared =
                dot(vector, vector);

        if (!Double.isFinite(normSquared)) {
            throw new IllegalArgumentException(
                    "Vector contains a non-finite value"
            );
        }

        double norm =
                Math.sqrt(normSquared);

        if (norm <= VECTOR_NORM_TOLERANCE) {
            throw new IllegalArgumentException(
                    "Pocket points do not define a stable "
                            + "principal-axis frame"
            );
        }

        return new double[]{
                vector[0] / norm,
                vector[1] / norm,
                vector[2] / norm
        };
    }

    private static double sanitizeEigenvalue(
            double eigenvalue
    ) {
        if (!Double.isFinite(eigenvalue)) {
            throw new IllegalArgumentException(
                    "Pocket covariance produced a non-finite eigenvalue"
            );
        }

        if (eigenvalue >= 0.0) {
            return eigenvalue;
        }

        if (eigenvalue
                >= -NEGATIVE_EIGENVALUE_TOLERANCE) {
            return 0.0;
        }

        throw new IllegalArgumentException(
                "Pocket covariance produced a materially negative "
                        + "eigenvalue: "
                        + eigenvalue
        );
    }

    private static double requireNonNegativeFinite(
            double value,
            String name
    ) {
        if (!Double.isFinite(value)
                || value < 0.0) {
            throw new IllegalArgumentException(
                    name
                            + " must be finite and non-negative"
            );
        }

        return value;
    }

    private record EigenPair(
            double eigenvalue,
            double[] eigenvector
    ) {

        private EigenPair {
            eigenvector = eigenvector.clone();
        }

        @Override
        public double[] eigenvector() {
            return eigenvector.clone();
        }
    }

    private record PrincipalFrame(
            Point3D centroid,
            double[][] axes,
            boolean degenerate,
            boolean planar
    ) {

        private PrincipalFrame {
            Objects.requireNonNull(
                    centroid,
                    "centroid"
            );

            axes = copyMatrix3(axes);
        }

        @Override
        public double[][] axes() {
            return copyMatrix3(axes);
        }
    }

    private record Candidate(
            RigidTransform transform,
            double rmsd
    ) {

        private Candidate {
            Objects.requireNonNull(
                    transform,
                    "transform"
            );

            if (!Double.isFinite(rmsd)
                    || rmsd < 0.0) {
                throw new IllegalArgumentException(
                        "rmsd must be finite and non-negative"
                );
            }
        }
    }

    private static double[][] copyMatrix3(
            double[][] matrix
    ) {
        Objects.requireNonNull(matrix, "matrix");

        if (matrix.length != 3) {
            throw new IllegalArgumentException(
                    "A 3-by-3 matrix is required"
            );
        }

        for (int row = 0; row < 3; row++) {
            if (matrix[row] == null
                    || matrix[row].length != 3) {
                throw new IllegalArgumentException(
                        "A 3-by-3 matrix is required"
                );
            }
        }

        return new double[][]{
                matrix[0].clone(),
                matrix[1].clone(),
                matrix[2].clone()
        };
    }

    private static final class CompensatedSum {

        private double sum;
        private double compensation;

        void add(double value) {
            double adjusted =
                    value - compensation;

            double next =
                    sum + adjusted;

            compensation =
                    (next - sum) - adjusted;

            sum = next;
        }

        double value() {
            return sum;
        }
    }
}