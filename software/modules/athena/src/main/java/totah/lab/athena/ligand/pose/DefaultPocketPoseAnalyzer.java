package totah.lab.athena.ligand.pose;

import totah.lab.gaia.geometry.Point3D;
import totah.lab.gaia.molecule.Ligand;
import totah.lab.gaia.pocket.Pocket;
import totah.lab.gaia.structure.Atom;

import java.util.List;
import java.util.Objects;

public final class DefaultPocketPoseAnalyzer
        implements PocketPoseAnalyzer {

    @Override
    public PocketPose analyze(
            Pocket pocket,
            Ligand ligand
    ) {
        Objects.requireNonNull(pocket, "pocket");
        Objects.requireNonNull(ligand, "ligand");

        Point3D ligandCentroid = centroid(ligand);
        Point3D pocketCentroid = pocket.center();

        return new PocketPose(
                ligandCentroid,
                distance(ligandCentroid, pocketCentroid)
        );
    }

    private static List<Atom> atoms(Ligand ligand) {
        return ligand.structure().getChains().stream()
                .flatMap(chain -> chain.residues().stream())
                .flatMap(residue -> residue.getAtoms().stream())
                .toList();
    }

    private static Point3D centroid(Ligand ligand) {
        List<Atom> atoms = atoms(ligand);
        if (atoms.isEmpty()) {
            throw new IllegalArgumentException(
                    "Ligand contains no atoms"
            );
        }

        double x = 0.0;
        double y = 0.0;
        double z = 0.0;

        for (var atom : atoms) {
            Point3D position = Objects.requireNonNull(
                    atom.getPosition(),
                    "ligand atom position"
            );

            x += position.x();
            y += position.y();
            z += position.z();
        }

        double count = atoms.size();

        return new Point3D(
                x / count,
                y / count,
                z / count
        );
    }

    private static double distance(
            Point3D first,
            Point3D second
    ) {
        double dx = first.x() - second.x();
        double dy = first.y() - second.y();
        double dz = first.z() - second.z();

        return Math.sqrt(
                dx * dx + dy * dy + dz * dz
        );
    }
}
