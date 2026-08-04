package totah.lab.athena.pocket.geometry;

import totah.lab.athena.pocket.selection.PocketResidueSelection;
import totah.lab.gaia.geometry.BoundingBox;
import totah.lab.gaia.geometry.Point3D;
import totah.lab.gaia.pocket.AlphaSphere;
import totah.lab.gaia.pocket.Pocket;
import totah.lab.gaia.structure.Atom;
import totah.lab.gaia.structure.Residue;
import totah.lab.gaia.structure.Structure;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Immutable point-cloud representation of a pocket.
 *
 * <p>Fpocket pockets are represented by alpha-sphere centers because those
 * points describe the detected cavity. Pockets from other sources are
 * represented by the heavy atoms of their resolved pocket residues.</p>
 */
public final class PocketPointCloud {

    private static final PocketResidueSelection RESIDUE_SELECTION =
            new PocketResidueSelection();

    private final List<Point3D> points;
    private final PocketGeometryBasis basis;
    private final Point3D centroid;
    private final BoundingBox bounds;

    public PocketPointCloud(
            List<Point3D> points,
            PocketGeometryBasis basis
    ) {
        Objects.requireNonNull(points, "points");

        this.basis = Objects.requireNonNull(
                basis,
                "basis"
        );

        if (points.isEmpty()) {
            throw new IllegalArgumentException(
                    "Pocket point cloud must not be empty"
            );
        }

        List<Point3D> validated =
                new ArrayList<>(points.size());

        for (int index = 0; index < points.size(); index++) {
            Point3D point = Objects.requireNonNull(
                    points.get(index),
                    "points[" + index + "]"
            );

            requireFinite(point);
            validated.add(point);
        }

        this.points = List.copyOf(validated);
        this.centroid = calculateCentroid(this.points);
        this.bounds = calculateBounds(this.points);
    }

    /**
     * Creates the appropriate point-cloud representation for a pocket.
     *
     * <p>Fpocket pockets use alpha-sphere centers. All other pocket sources
     * use resolved residue heavy atoms.</p>
     */
    public static PocketPointCloud from(
            Structure structure,
            Pocket pocket
    ) {
        Objects.requireNonNull(structure, "structure");
        Objects.requireNonNull(pocket, "pocket");

        if (isFpocket(pocket)) {
            return fromAlphaSpheres(pocket);
        }

        return fromResidueAtoms(
                structure,
                pocket
        );
    }

    private static PocketPointCloud fromAlphaSpheres(
            Pocket pocket
    ) {
        if (!hasAlphaSpheres(pocket)) {
            throw new IllegalArgumentException(
                    "Fpocket pocket has no alpha spheres: "
                            + pocket.id()
            );
        }

        return new PocketPointCloud(
                alphaSpherePoints(pocket),
                PocketGeometryBasis.ALPHA_SPHERES
        );
    }

    private static PocketPointCloud fromResidueAtoms(
            Structure structure,
            Pocket pocket
    ) {
        return new PocketPointCloud(
                residueHeavyAtomPoints(
                        structure,
                        pocket
                ),
                PocketGeometryBasis.RESIDUE_ATOMS
        );
    }

    private static boolean isFpocket(
            Pocket pocket
    ) {
        Object source = Objects.requireNonNull(
                pocket.source(),
                "pocket.source"
        );

        return "FPOCKET".equalsIgnoreCase(
                source.toString()
        );
    }

    private static boolean hasAlphaSpheres(
            Pocket pocket
    ) {
        return pocket.alphaSphereSet()
                .filter(set -> set.spheres() != null)
                .filter(set -> !set.spheres().isEmpty())
                .isPresent();
    }

    private static List<Point3D> alphaSpherePoints(
            Pocket pocket
    ) {
        return pocket.alphaSphereSet()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Pocket has no alpha-sphere set: "
                                + pocket.id()
                ))
                .spheres()
                .stream()
                .map(PocketPointCloud::sphereCenter)
                .toList();
    }

    private static Point3D sphereCenter(
            AlphaSphere sphere
    ) {
        AlphaSphere required = Objects.requireNonNull(
                sphere,
                "Alpha-sphere collection must not contain null spheres"
        );

        Point3D center = Objects.requireNonNull(
                required.center(),
                "Alpha-sphere center"
        );

        requireFinite(center);
        return center;
    }

    private static List<Point3D> residueHeavyAtomPoints(
            Structure structure,
            Pocket pocket
    ) {
        List<Point3D> resolvedPoints = RESIDUE_SELECTION
                .resolvedResidues(
                        structure,
                        pocket
                )
                .stream()
                .filter(Objects::nonNull)
                .flatMap(residue -> atoms(residue).stream())
                .filter(Objects::nonNull)
                .filter(Atom::isHeavyAtom)
                .map(PocketPointCloud::atomPosition)
                .toList();

        if (resolvedPoints.isEmpty()) {
            throw new IllegalArgumentException(
                    "Pocket does not resolve to any heavy atoms: "
                            + pocket.id()
            );
        }

        return resolvedPoints;
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

    private static Point3D calculateCentroid(
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

    private static BoundingBox calculateBounds(
            List<Point3D> points
    ) {
        double minimumX = Double.POSITIVE_INFINITY;
        double minimumY = Double.POSITIVE_INFINITY;
        double minimumZ = Double.POSITIVE_INFINITY;

        double maximumX = Double.NEGATIVE_INFINITY;
        double maximumY = Double.NEGATIVE_INFINITY;
        double maximumZ = Double.NEGATIVE_INFINITY;

        for (Point3D point : points) {
            minimumX = Math.min(
                    minimumX,
                    point.x()
            );

            minimumY = Math.min(
                    minimumY,
                    point.y()
            );

            minimumZ = Math.min(
                    minimumZ,
                    point.z()
            );

            maximumX = Math.max(
                    maximumX,
                    point.x()
            );

            maximumY = Math.max(
                    maximumY,
                    point.y()
            );

            maximumZ = Math.max(
                    maximumZ,
                    point.z()
            );
        }

        return new BoundingBox(
                new Point3D(
                        minimumX,
                        minimumY,
                        minimumZ
                ),
                new Point3D(
                        maximumX,
                        maximumY,
                        maximumZ
                )
        );
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

    public PocketPointCloud centered() {
        List<Point3D> centeredPoints = points.stream()
                .map(point -> new Point3D(
                        point.x() - centroid.x(),
                        point.y() - centroid.y(),
                        point.z() - centroid.z()
                ))
                .toList();

        return new PocketPointCloud(
                centeredPoints,
                basis
        );
    }

    public List<Point3D> points() {
        return points;
    }

    public PocketGeometryBasis basis() {
        return basis;
    }

    public Point3D centroid() {
        return centroid;
    }

    public BoundingBox bounds() {
        return bounds;
    }

    public int size() {
        return points.size();
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