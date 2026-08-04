package totah.lab.athena.pocket.similar;

import totah.lab.athena.pocket.geometry.PocketGeometryBasis;
import totah.lab.athena.pocket.geometry.PocketPointCloud;
import totah.lab.athena.pocket.selection.PocketResidueSelection;
import totah.lab.gaia.geometry.Point3D;
import totah.lab.gaia.pocket.AlphaSphere;
import totah.lab.gaia.pocket.Pocket;
import totah.lab.gaia.structure.Atom;
import totah.lab.gaia.structure.Residue;
import totah.lab.gaia.structure.Structure;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Creates rotation- and translation-independent shape descriptors for pockets.
 *
 * <p>Fpocket pockets are described from alpha-sphere centers. Pockets from
 * other sources are described from the heavy atoms of their resolved pocket
 * residues.</p>
 */
public final class PocketShapeDescriptorFactory {

    public static final int DEFAULT_RADIAL_BIN_COUNT = 12;

    private static final PocketResidueSelection RESIDUE_SELECTION =
            new PocketResidueSelection();

    private PocketShapeDescriptorFactory() {
    }

    public static PocketShapeDescriptor describe(
            Structure structure,
            Pocket pocket
    ) {
        return describe(
                structure,
                pocket,
                DEFAULT_RADIAL_BIN_COUNT
        );
    }

    public static PocketShapeDescriptor describe(
            Structure structure,
            Pocket pocket,
            int radialBinCount
    ) {
        Objects.requireNonNull(structure, "structure");
        Objects.requireNonNull(pocket, "pocket");

        PocketPointCloud pointCloud = isFpocket(pocket)
                ? fpocketPointCloud(pocket)
                : residueAtomPointCloud(
                structure,
                pocket
        );

        return describe(
                pointCloud,
                radialBinCount
        );
    }

    public static PocketShapeDescriptor describe(
            PocketPointCloud pointCloud
    ) {
        return describe(
                pointCloud,
                DEFAULT_RADIAL_BIN_COUNT
        );
    }

    public static PocketShapeDescriptor describe(
            PocketPointCloud pointCloud,
            int radialBinCount
    ) {
        Objects.requireNonNull(pointCloud, "pointCloud");

        if (radialBinCount < 2) {
            throw new IllegalArgumentException(
                    "radialBinCount must be at least 2"
            );
        }

        List<Point3D> points = pointCloud.points();
        Point3D centroid = pointCloud.centroid();

        double[] distances =
                new double[points.size()];

        CompensatedSum distanceSum =
                new CompensatedSum();

        CompensatedSum squaredDistanceSum =
                new CompensatedSum();

        double maximumRadius = 0.0;

        for (int index = 0;
             index < points.size();
             index++) {

            double distance = centroid.distance(
                    points.get(index)
            );

            distances[index] = distance;

            distanceSum.add(distance);

            squaredDistanceSum.add(
                    distance * distance
            );

            maximumRadius = Math.max(
                    maximumRadius,
                    distance
            );
        }

        double pointCount = points.size();

        double meanRadius =
                distanceSum.value() / pointCount;

        double radiusOfGyration =
                Math.sqrt(
                        squaredDistanceSum.value()
                                / pointCount
                );

        CompensatedSum varianceSum =
                new CompensatedSum();

        for (double distance : distances) {
            double difference =
                    distance - meanRadius;

            varianceSum.add(
                    difference * difference
            );
        }

        double radiusStandardDeviation =
                Math.sqrt(
                        varianceSum.value()
                                / pointCount
                );

        double[] extents = {
                pointCloud.bounds().width(),
                pointCloud.bounds().height(),
                pointCloud.bounds().depth()
        };

        Arrays.sort(extents);

        double minorExtent = extents[0];
        double middleExtent = extents[1];
        double majorExtent = extents[2];

        double elongation =
                safeRatio(
                        majorExtent,
                        middleExtent
                );

        double flatness =
                safeRatio(
                        middleExtent,
                        minorExtent
                );

        double[] radialHistogram =
                radialHistogram(
                        distances,
                        maximumRadius,
                        radialBinCount
                );

        return new PocketShapeDescriptor(
                pointCloud.size(),
                pointCloud.basis(),
                radiusOfGyration,
                maximumRadius,
                meanRadius,
                radiusStandardDeviation,
                majorExtent,
                middleExtent,
                minorExtent,
                elongation,
                flatness,
                radialHistogram
        );
    }

    private static boolean isFpocket(Pocket pocket) {
        Object source = Objects.requireNonNull(
                pocket.source(),
                "pocket.source"
        );

        return "FPOCKET".equalsIgnoreCase(
                source.toString()
        );
    }

    private static PocketPointCloud fpocketPointCloud(
            Pocket pocket
    ) {
        List<Point3D> points = pocket.alphaSphereSet()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Fpocket pocket has no alpha-sphere set: "
                                + pocket.id()
                ))
                .spheres()
                .stream()
                .map(PocketShapeDescriptorFactory::alphaSphereCenter)
                .toList();

        if (points.isEmpty()) {
            throw new IllegalArgumentException(
                    "Fpocket pocket has no alpha spheres: "
                            + pocket.id()
            );
        }

        return new PocketPointCloud(
                points,
                PocketGeometryBasis.ALPHA_SPHERES
        );
    }

    private static Point3D alphaSphereCenter(
            AlphaSphere sphere
    ) {
        AlphaSphere requiredSphere =
                Objects.requireNonNull(
                        sphere,
                        "Alpha-sphere collection must not contain null"
                );

        Point3D center = Objects.requireNonNull(
                requiredSphere.center(),
                "Alpha-sphere center"
        );

        requireFinite(center);
        return center;
    }

    private static PocketPointCloud residueAtomPointCloud(
            Structure structure,
            Pocket pocket
    ) {
        List<Point3D> points = RESIDUE_SELECTION
                .resolvedResidues(
                        structure,
                        pocket
                )
                .stream()
                .filter(Objects::nonNull)
                .flatMap(residue -> atoms(residue).stream())
                .filter(Objects::nonNull)
                .filter(Atom::isHeavyAtom)
                .map(PocketShapeDescriptorFactory::atomPosition)
                .toList();

        if (points.isEmpty()) {
            throw new IllegalArgumentException(
                    "Pocket does not resolve to any heavy atoms: "
                            + pocket.id()
            );
        }

        return new PocketPointCloud(
                points,
                PocketGeometryBasis.RESIDUE_ATOMS
        );
    }

    private static List<Atom> atoms(
            Residue residue
    ) {
        return Objects.requireNonNull(
                residue.getAtoms(),
                "Pocket residue atoms"
        );
    }

    private static Point3D atomPosition(
            Atom atom
    ) {
        Point3D position = Objects.requireNonNull(
                atom.getPosition(),
                "Pocket atom position"
        );

        requireFinite(position);
        return position;
    }

    private static void requireFinite(
            Point3D point
    ) {
        if (!Double.isFinite(point.x())
                || !Double.isFinite(point.y())
                || !Double.isFinite(point.z())) {
            throw new IllegalArgumentException(
                    "Pocket point must contain finite coordinates: "
                            + point
            );
        }
    }

    private static double[] radialHistogram(
            double[] distances,
            double maximumRadius,
            int binCount
    ) {
        double[] histogram =
                new double[binCount];

        if (maximumRadius == 0.0) {
            histogram[0] = 1.0;
            return histogram;
        }

        for (double distance : distances) {
            double normalized =
                    distance / maximumRadius;

            int bin = Math.min(
                    binCount - 1,
                    (int) Math.floor(
                            normalized * binCount
                    )
            );

            histogram[bin]++;
        }

        double inverseCount =
                1.0 / distances.length;

        for (int index = 0;
             index < histogram.length;
             index++) {

            histogram[index] *= inverseCount;
        }

        return histogram;
    }

    private static double safeRatio(
            double numerator,
            double denominator
    ) {
        if (denominator == 0.0) {
            return numerator == 0.0
                    ? 1.0
                    : Double.POSITIVE_INFINITY;
        }

        return numerator / denominator;
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