package totah.lab.athena.pocket.geometry;

import totah.lab.athena.pocket.selection.PocketResidueSelection;
import totah.lab.gaia.geometry.BoundingBox;
import totah.lab.gaia.geometry.Point3D;
import totah.lab.gaia.pocket.Pocket;
import totah.lab.gaia.structure.Atom;
import totah.lab.gaia.structure.Residue;
import totah.lab.gaia.structure.Structure;

import java.util.List;
import java.util.Objects;

public final class ResidueAtomPocketGeometry
        implements PocketGeometryStrategy {
    private final PocketResidueSelection selection;

    public ResidueAtomPocketGeometry() {
        this(new PocketResidueSelection());
    }

    public ResidueAtomPocketGeometry(PocketResidueSelection selection) {
        this.selection = Objects.requireNonNull(selection, "selection");
    }

    @Override
    public BoundingBox bounds(Structure structure, Pocket pocket) {
        return residueGeometry(structure, pocket).bounds();
    }

    @Override
    public Point3D centroid(Structure structure, Pocket pocket) {
        return residueGeometry(structure, pocket).centroid();
    }

    public PocketResidueGeometry residueGeometry(
            Structure structure,
            Pocket pocket) {
        Objects.requireNonNull(structure, "structure");
        Objects.requireNonNull(pocket, "pocket");
        List<Residue> residues = selection.resolvedResidues(
                structure, pocket);
        List<Atom> heavyAtoms = residues.stream()
                .flatMap(residue -> residue.getAtoms().stream())
                .filter(Atom::isHeavyAtom)
                .toList();
        if (heavyAtoms.isEmpty()) {
            throw new IllegalArgumentException(
                    "Pocket has no resolved residue heavy atoms: "
                            + pocket.id());
        }
        return new PocketResidueGeometry(
                boundsOf(heavyAtoms),
                centroidOf(heavyAtoms),
                residues,
                selection.unresolvedResidues(structure, pocket));
    }

    @Override
    public PocketGeometryBasis basis() {
        return PocketGeometryBasis.RESOLVED_RESIDUE_HEAVY_ATOMS;
    }

    private static BoundingBox boundsOf(List<Atom> atoms) {
        double minX = atoms.stream().map(Atom::getPosition)
                .mapToDouble(Point3D::x).min().orElseThrow();
        double minY = atoms.stream().map(Atom::getPosition)
                .mapToDouble(Point3D::y).min().orElseThrow();
        double minZ = atoms.stream().map(Atom::getPosition)
                .mapToDouble(Point3D::z).min().orElseThrow();
        double maxX = atoms.stream().map(Atom::getPosition)
                .mapToDouble(Point3D::x).max().orElseThrow();
        double maxY = atoms.stream().map(Atom::getPosition)
                .mapToDouble(Point3D::y).max().orElseThrow();
        double maxZ = atoms.stream().map(Atom::getPosition)
                .mapToDouble(Point3D::z).max().orElseThrow();
        return new BoundingBox(
                new Point3D(minX, minY, minZ),
                new Point3D(maxX, maxY, maxZ));
    }

    private static Point3D centroidOf(List<Atom> atoms) {
        return new Point3D(
                atoms.stream().map(Atom::getPosition)
                        .mapToDouble(Point3D::x).average().orElseThrow(),
                atoms.stream().map(Atom::getPosition)
                        .mapToDouble(Point3D::y).average().orElseThrow(),
                atoms.stream().map(Atom::getPosition)
                        .mapToDouble(Point3D::z).average().orElseThrow());
    }
}
