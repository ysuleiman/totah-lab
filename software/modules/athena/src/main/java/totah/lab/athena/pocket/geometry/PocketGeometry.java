package totah.lab.athena.pocket.geometry;

import totah.lab.gaia.geometry.BoundingBox;
import totah.lab.gaia.geometry.Point3D;
import totah.lab.gaia.molecule.Ligand;
import totah.lab.gaia.pocket.AlphaSphere;
import totah.lab.gaia.pocket.AlphaSphereSet;
import totah.lab.gaia.pocket.Pocket;
import totah.lab.gaia.structure.Structure;
import totah.lab.gaia.structure.Atom;
import totah.lab.gaia.structure.Residue;

import java.util.List;
import java.util.Objects;

/** Source-independent geometric calculations for pocket representations. */
public final class PocketGeometry {
    private static final PocketGeometryStrategy ALPHA_SPHERE_GEOMETRY =
            new AlphaSpherePocketGeometry();
    private static final PocketGeometryStrategy RESIDUE_ATOM_GEOMETRY =
            new ResidueAtomPocketGeometry();

    private PocketGeometry() {
    }

    public static Point3D alphaSphereCentroid(AlphaSphereSet sphereSet) {
        List<AlphaSphere> spheres = requireSpheres(sphereSet);
        double x = 0.0;
        double y = 0.0;
        double z = 0.0;
        for (AlphaSphere sphere : spheres) {
            x += sphere.center().x();
            y += sphere.center().y();
            z += sphere.center().z();
        }
        return new Point3D(
                x / spheres.size(),
                y / spheres.size(),
                z / spheres.size());
    }

    public static double alphaSphereCenterDistance(
            Pocket first,
            Pocket second) {
        Objects.requireNonNull(first, "first");
        Objects.requireNonNull(second, "second");
        return alphaSphereCentroid(requireSphereSet(first))
                .distance(alphaSphereCentroid(requireSphereSet(second)));
    }

    public static BoundingBox alphaSphereBounds(
            AlphaSphereSet sphereSet,
            boolean includeRadii) {
        List<AlphaSphere> spheres = requireSpheres(sphereSet);
        double minX = Double.POSITIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double minZ = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        double maxZ = Double.NEGATIVE_INFINITY;
        for (AlphaSphere sphere : spheres) {
            double radius = includeRadii ? sphere.radius() : 0.0;
            Point3D center = sphere.center();
            minX = Math.min(minX, center.x() - radius);
            minY = Math.min(minY, center.y() - radius);
            minZ = Math.min(minZ, center.z() - radius);
            maxX = Math.max(maxX, center.x() + radius);
            maxY = Math.max(maxY, center.y() + radius);
            maxZ = Math.max(maxZ, center.z() + radius);
        }
        return new BoundingBox(
                new Point3D(minX, minY, minZ),
                new Point3D(maxX, maxY, maxZ));
    }

    public static Point3D dimensions(BoundingBox bounds) {
        Objects.requireNonNull(bounds, "bounds");
        return new Point3D(
                bounds.width(), bounds.height(), bounds.depth());
    }

    public static double volume(BoundingBox bounds) {
        return Objects.requireNonNull(bounds, "bounds").volume();
    }

    public static double axisOverlap(
            double firstMin,
            double firstMax,
            double secondMin,
            double secondMax) {
        requireInterval(firstMin, firstMax, "first");
        requireInterval(secondMin, secondMax, "second");
        return Math.max(
                0.0,
                Math.min(firstMax, secondMax)
                        - Math.max(firstMin, secondMin));
    }

    public static double intersectionVolume(
            BoundingBox first,
            BoundingBox second) {
        Objects.requireNonNull(first, "first");
        return first.intersectionVolume(
                Objects.requireNonNull(second, "second"));
    }

    public static double intersectionOverUnion(
            BoundingBox first,
            BoundingBox second) {
        Objects.requireNonNull(first, "first");
        return first.intersectionOverUnion(
                Objects.requireNonNull(second, "second"));
    }

    public static PocketGeometryResult geometry(
            Structure structure,
            Pocket pocket) {
        Objects.requireNonNull(structure, "structure");
        Objects.requireNonNull(pocket, "pocket");
        PocketGeometryStrategy strategy = pocket.alphaSphereSet()
                .filter(set -> !set.spheres().isEmpty())
                .<PocketGeometryStrategy>map(ignored ->
                        ALPHA_SPHERE_GEOMETRY)
                .orElse(RESIDUE_ATOM_GEOMETRY);
        List<totah.lab.gaia.structure.ResidueId> unresolved =
                new totah.lab.athena.pocket.selection.PocketResidueSelection()
                        .unresolvedResidues(structure, pocket);
        return new PocketGeometryResult(
                strategy.bounds(structure, pocket),
                strategy.centroid(structure, pocket),
                strategy.basis(),
                unresolved);
    }

    public static PocketOverlapResult overlap(
            PocketGeometryResult first,
            PocketGeometryResult second) {
        Objects.requireNonNull(first, "first");
        Objects.requireNonNull(second, "second");
        return new PocketOverlapResult(
                first.bounds().intersectionVolume(second.bounds()),
                first.bounds().intersectionOverUnion(second.bounds()),
                first.basis(),
                second.basis());
    }

    public static double calculateDistance(
            Residue first,
            Residue second) {
        Objects.requireNonNull(first, "first");
        Objects.requireNonNull(second, "second");
        return minimumHeavyAtomDistance(
                first.getAtoms(), second.getAtoms(),
                "Both residues must contain at least one heavy atom");
    }

    public static double calculateDistance(
            Residue first,
            String firstAtomName,
            Residue second,
            String secondAtomName) {
        Objects.requireNonNull(first, "first");
        Objects.requireNonNull(second, "second");
        Atom firstAtom = first.findAtom(
                        Objects.requireNonNull(firstAtomName, "firstAtomName"))
                .orElseThrow(() -> new IllegalArgumentException(
                        "Atom " + firstAtomName
                                + " is not present in residue "
                                + first.getName() + first.getNumber()));
        Atom secondAtom = second.findAtom(
                        Objects.requireNonNull(secondAtomName, "secondAtomName"))
                .orElseThrow(() -> new IllegalArgumentException(
                        "Atom " + secondAtomName
                                + " is not present in residue "
                                + second.getName() + second.getNumber()));
        return firstAtom.getPosition().distance(secondAtom.getPosition());
    }

    public static double calculateDistance(
            Residue residue,
            Ligand ligand) {
        Objects.requireNonNull(residue, "residue");
        Objects.requireNonNull(ligand, "ligand");
        return minimumHeavyAtomDistance(
                residue.getAtoms(),
                ligandAtoms(ligand),
                "Residue and ligand must contain heavy atoms");
    }

    public static boolean areNeighbors(
            Residue first,
            Residue second,
            double cutoffAngstroms) {
        validateCutoff(cutoffAngstroms);
        return hasContact(
                first.getAtoms(), second.getAtoms(), cutoffAngstroms);
    }

    public static boolean areNeighbors(
            Residue residue,
            Ligand ligand,
            double cutoffAngstroms) {
        return contactingAtomPairCount(
                residue, ligand, cutoffAngstroms) > 0;
    }

    public static int contactingAtomPairCount(
            Residue residue,
            Ligand ligand,
            double cutoffAngstroms) {
        Objects.requireNonNull(residue, "residue");
        Objects.requireNonNull(ligand, "ligand");
        validateCutoff(cutoffAngstroms);
        double cutoffSquared = cutoffAngstroms * cutoffAngstroms;
        int count = 0;
        for (Atom residueAtom : residue.getAtoms()) {
            if (!residueAtom.isHeavyAtom()) {
                continue;
            }
            for (Atom ligandAtom : ligandAtoms(ligand)) {
                if (ligandAtom.isHeavyAtom()
                        && residueAtom.getPosition().distanceSquared(
                                ligandAtom.getPosition()) <= cutoffSquared) {
                    count++;
                }
            }
        }
        return count;
    }

    public static List<Residue> ligandNeighbors(
            Structure structure,
            Ligand ligand,
            double cutoffAngstroms) {
        Objects.requireNonNull(structure, "structure");
        Objects.requireNonNull(ligand, "ligand");
        validateCutoff(cutoffAngstroms);
        return structure.getChains().stream()
                .flatMap(chain -> chain.residues().stream())
                .filter(residue -> areNeighbors(
                        residue, ligand, cutoffAngstroms))
                .toList();
    }

    private static AlphaSphereSet requireSphereSet(Pocket pocket) {
        return pocket.alphaSphereSet()
                .filter(set -> !set.spheres().isEmpty())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Pocket has no alpha spheres: " + pocket.id()));
    }

    private static List<AlphaSphere> requireSpheres(
            AlphaSphereSet sphereSet) {
        Objects.requireNonNull(sphereSet, "sphereSet");
        List<AlphaSphere> spheres = sphereSet.spheres();
        if (spheres.isEmpty()) {
            throw new IllegalArgumentException(
                    "Alpha-sphere set must not be empty");
        }
        return spheres;
    }

    private static double minimumHeavyAtomDistance(
            List<Atom> first,
            List<Atom> second,
            String emptyMessage) {
        double minimumSquared = Double.POSITIVE_INFINITY;
        for (Atom firstAtom : first) {
            if (!firstAtom.isHeavyAtom()) {
                continue;
            }
            for (Atom secondAtom : second) {
                if (secondAtom.isHeavyAtom()) {
                    minimumSquared = Math.min(
                            minimumSquared,
                            firstAtom.getPosition().distanceSquared(
                                    secondAtom.getPosition()));
                }
            }
        }
        if (!Double.isFinite(minimumSquared)) {
            throw new IllegalArgumentException(emptyMessage);
        }
        return Math.sqrt(minimumSquared);
    }

    private static boolean hasContact(
            List<Atom> first,
            List<Atom> second,
            double cutoffAngstroms) {
        double cutoffSquared = cutoffAngstroms * cutoffAngstroms;
        for (Atom firstAtom : first) {
            if (!firstAtom.isHeavyAtom()) {
                continue;
            }
            for (Atom secondAtom : second) {
                if (secondAtom.isHeavyAtom()
                        && firstAtom.getPosition().distanceSquared(
                                secondAtom.getPosition()) <= cutoffSquared) {
                    return true;
                }
            }
        }
        return false;
    }

    private static List<Atom> ligandAtoms(Ligand ligand) {
        return ligand.structure().getChains().stream()
                .flatMap(chain -> chain.residues().stream())
                .flatMap(residue -> residue.getAtoms().stream())
                .toList();
    }

    private static void validateCutoff(double cutoffAngstroms) {
        if (!Double.isFinite(cutoffAngstroms)
                || cutoffAngstroms <= 0.0) {
            throw new IllegalArgumentException(
                    "Cutoff must be finite and greater than zero");
        }
    }

    private static void requireInterval(
            double min,
            double max,
            String name) {
        if (!Double.isFinite(min)
                || !Double.isFinite(max)
                || min > max) {
            throw new IllegalArgumentException(
                    name + " interval must be finite and ordered");
        }
    }
}
