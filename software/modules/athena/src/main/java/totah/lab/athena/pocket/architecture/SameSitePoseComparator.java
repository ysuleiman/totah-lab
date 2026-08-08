package totah.lab.athena.pocket.architecture;

import totah.lab.athena.pocket.compare.KabschRigidPointAligner;
import totah.lab.gaia.geometry.Point3D;
import totah.lab.gaia.geometry.RigidTransform;
import totah.lab.gaia.geometry.Vector3D;
import totah.lab.gaia.molecule.Ligand;
import totah.lab.gaia.structure.Atom;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Builds a {@link SameSitePoseComparison}. All ligand geometry is
 * heavy-atom based. The RMSD pairs atoms through the verified
 * {@link LigandAtomCorrespondence} mapping — never by blind index —
 * and the rotation angle comes from the Kabsch fit of the aligned
 * pose B onto pose A over that same mapping. The rotation is
 * reported as {@code null} when the mapped point sets are degenerate
 * (fewer than three atoms or effectively collinear), where the
 * rotation is not robustly defined.
 */
public final class SameSitePoseComparator {

    private static final double COLLINEARITY_TOLERANCE = 1.0e-9;

    private final KabschRigidPointAligner kabschAligner =
            new KabschRigidPointAligner();

    public SameSitePoseComparison compare(
            String poseALabel,
            Ligand poseA,
            String poseBLabel,
            Ligand poseB,
            RigidTransform transformBtoA,
            PocketArchitecture referencePocketArchitecture
    ) {
        Objects.requireNonNull(poseA, "poseA");
        Objects.requireNonNull(poseB, "poseB");
        Objects.requireNonNull(transformBtoA, "transformBtoA");
        Objects.requireNonNull(
                referencePocketArchitecture,
                "referencePocketArchitecture"
        );

        List<Point3D> positionsA = heavyAtomPositions(poseA);
        List<Point3D> positionsBAligned =
                transformBtoA.apply(heavyAtomPositions(poseB));

        if (positionsA.isEmpty() || positionsBAligned.isEmpty()) {
            throw new IllegalArgumentException(
                    "Both poses must contain heavy atoms"
            );
        }

        Point3D centroidA = centroid(positionsA);
        Point3D centroidB = centroid(positionsBAligned);

        Vector3D translation = centroidB.vectorFrom(centroidA);

        PrincipalComponents pocketPca =
                referencePocketArchitecture.principalComponents();
        double alongU1 = pocketPca.offsetAlong(
                centroidB, centroidA, 0);
        double alongU2 = pocketPca.offsetAlong(
                centroidB, centroidA, 1);
        double alongU3 = pocketPca.offsetAlong(
                centroidB, centroidA, 2);
        double lateral =
                Math.sqrt(alongU2 * alongU2 + alongU3 * alongU3);

        LigandAtomCorrespondence.Mapping mapping =
                LigandAtomCorrespondence.map(poseA, poseB);

        Double rmsd = null;
        Double rotationAngle = null;

        if (mapping.method() != LigandAtomCorrespondence.Method.NONE) {
            List<Point3D> mappedA = new ArrayList<>();
            List<Point3D> mappedB = new ArrayList<>();

            int[] bToA = mapping.bToAIndexView();

            for (int indexB = 0; indexB < bToA.length; indexB++) {
                mappedA.add(positionsA.get(bToA[indexB]));
                mappedB.add(positionsBAligned.get(indexB));
            }

            rmsd = indexRmsd(mappedA, mappedB);
            rotationAngle = rotationAngle(mappedB, mappedA);
        }

        return new SameSitePoseComparison(
                poseALabel,
                poseBLabel,
                centroidA.distance(centroidB),
                translation,
                rotationAngle,
                rmsd,
                mapping.method(),
                mapping.reason(),
                alongU1,
                alongU2,
                alongU3,
                lateral,
                orientationAngles(positionsA, pocketPca),
                orientationAngles(positionsBAligned, pocketPca)
        );
    }

    /**
     * Acute angles (degrees) between the ligand long axis (first
     * principal component of its heavy atoms) and each pocket axis.
     */
    private static List<Double> orientationAngles(
            List<Point3D> positions,
            PrincipalComponents pocketPca
    ) {
        Vector3D longAxis = ligandLongAxis(positions);

        List<Double> angles = new ArrayList<>(3);

        for (int axis = 0; axis < 3; axis++) {
            double cosine = Math.min(1.0, Math.abs(
                    longAxis.dot(pocketPca.axes().get(axis))));
            angles.add(Math.toDegrees(Math.acos(cosine)));
        }

        return List.copyOf(angles);
    }

    /**
     * Returns the unoriented heavy-atom long axis. Two points define
     * only this primary axis, so avoid asking PCA to invent the
     * underdetermined transverse frame.
     */
    private static Vector3D ligandLongAxis(List<Point3D> positions) {
        if (positions.size() == 2) {
            Vector3D axis = positions.get(1).vectorFrom(positions.getFirst());
            if (axis.magnitude() <= COLLINEARITY_TOLERANCE) {
                throw new IllegalArgumentException(
                        "Two-atom ligand positions must be distinct");
            }
            return axis.normalize();
        }
        return PrincipalComponents.of(positions).axes().getFirst();
    }

    private Double rotationAngle(
            List<Point3D> from,
            List<Point3D> to
    ) {
        if (from.size() < 3
                || effectivelyCollinear(from)
                || effectivelyCollinear(to)) {
            return null;
        }

        try {
            RigidTransform fit = kabschAligner.align(from, to);
            double[][] rotation = fit.rotation();
            double trace = rotation[0][0]
                    + rotation[1][1]
                    + rotation[2][2];
            double cosine = Math.max(
                    -1.0,
                    Math.min(1.0, (trace - 1.0) / 2.0)
            );
            return Math.toDegrees(Math.acos(cosine));
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private static boolean effectivelyCollinear(List<Point3D> points) {
        Point3D first = points.getFirst();
        Point3D farthest = null;
        double maximumDistanceSquared = 0.0;

        for (Point3D point : points) {
            double distanceSquared = first.distanceSquared(point);

            if (distanceSquared > maximumDistanceSquared) {
                maximumDistanceSquared = distanceSquared;
                farthest = point;
            }
        }

        if (farthest == null
                || maximumDistanceSquared <= COLLINEARITY_TOLERANCE) {
            return true;
        }

        double axisX = farthest.x() - first.x();
        double axisY = farthest.y() - first.y();
        double axisZ = farthest.z() - first.z();

        for (Point3D point : points) {
            double dx = point.x() - first.x();
            double dy = point.y() - first.y();
            double dz = point.z() - first.z();

            double crossX = dy * axisZ - dz * axisY;
            double crossY = dz * axisX - dx * axisZ;
            double crossZ = dx * axisY - dy * axisX;

            double crossNormSquared = crossX * crossX
                    + crossY * crossY
                    + crossZ * crossZ;

            double tolerance = COLLINEARITY_TOLERANCE
                    * maximumDistanceSquared
                    * (1.0 + maximumDistanceSquared);

            if (crossNormSquared > tolerance) {
                return false;
            }
        }

        return true;
    }

    private static double indexRmsd(
            List<Point3D> positionsA,
            List<Point3D> positionsB
    ) {
        double sum = 0.0;

        for (int index = 0; index < positionsA.size(); index++) {
            sum += positionsA.get(index).distanceSquared(
                    positionsB.get(index));
        }

        return Math.sqrt(sum / positionsA.size());
    }

    private static Point3D centroid(List<Point3D> points) {
        double x = 0.0;
        double y = 0.0;
        double z = 0.0;

        for (Point3D point : points) {
            x += point.x();
            y += point.y();
            z += point.z();
        }

        double n = points.size();

        return new Point3D(x / n, y / n, z / n);
    }

    private static List<Point3D> heavyAtomPositions(Ligand pose) {
        return pose.structure().getChains().stream()
                .flatMap(chain -> chain.residues().stream())
                .flatMap(residue -> residue.getAtoms().stream())
                .filter(Atom::isHeavyAtom)
                .map(Atom::getPosition)
                .toList();
    }
}
