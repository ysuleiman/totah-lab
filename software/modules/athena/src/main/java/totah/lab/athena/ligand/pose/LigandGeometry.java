package totah.lab.athena.ligand.pose;

import totah.lab.gaia.geometry.BoundingBox;
import totah.lab.gaia.geometry.Point3D;
import totah.lab.gaia.molecule.Ligand;
import totah.lab.gaia.structure.Atom;

import java.util.List;
import java.util.Objects;

/**
 * Heavy-atom-only geometry for predicted ligand poses. Unlike the
 * all-atom centroid used by {@link DefaultPocketPoseAnalyzer} (kept
 * unchanged for backwards compatibility), this utility excludes
 * hydrogens so occupancy metrics reflect the volume a pose actually
 * occupies in a pocket.
 */
public final class LigandGeometry {

    private LigandGeometry() {
    }

    /**
     * Computes the {@link LigandShape} of a predicted pose from its
     * heavy atoms.
     *
     * @throws IllegalArgumentException if the ligand has no heavy atoms
     */
    public static LigandShape shape(Ligand ligand) {
        List<Point3D> positions = heavyAtomPositions(ligand);

        if (positions.isEmpty()) {
            throw new IllegalArgumentException(
                    "Ligand contains no heavy atoms"
            );
        }

        double x = 0.0;
        double y = 0.0;
        double z = 0.0;
        double minX = Double.MAX_VALUE;
        double minY = Double.MAX_VALUE;
        double minZ = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE;
        double maxY = -Double.MAX_VALUE;
        double maxZ = -Double.MAX_VALUE;

        for (Point3D position : positions) {
            x += position.x();
            y += position.y();
            z += position.z();
            minX = Math.min(minX, position.x());
            minY = Math.min(minY, position.y());
            minZ = Math.min(minZ, position.z());
            maxX = Math.max(maxX, position.x());
            maxY = Math.max(maxY, position.y());
            maxZ = Math.max(maxZ, position.z());
        }

        double count = positions.size();
        Point3D centroid = new Point3D(
                x / count,
                y / count,
                z / count
        );

        double radius = 0.0;
        for (Point3D position : positions) {
            radius = Math.max(radius, centroid.distance(position));
        }

        return new LigandShape(
                centroid,
                new BoundingBox(
                        new Point3D(minX, minY, minZ),
                        new Point3D(maxX, maxY, maxZ)
                ),
                radius,
                positions.size()
        );
    }

    /**
     * Returns the positions of all heavy atoms of the ligand, in
     * structure order.
     */
    public static List<Point3D> heavyAtomPositions(Ligand ligand) {
        Objects.requireNonNull(ligand, "ligand");

        return ligand.structure().getChains().stream()
                .flatMap(chain -> chain.residues().stream())
                .flatMap(residue -> residue.getAtoms().stream())
                .filter(Atom::isHeavyAtom)
                .map(Atom::getPosition)
                .toList();
    }
}
