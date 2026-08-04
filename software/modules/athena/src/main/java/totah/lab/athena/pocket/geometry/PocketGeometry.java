package totah.lab.athena.pocket.geometry;

import totah.lab.athena.pocket.selection.PocketResidueSelection;
import totah.lab.gaia.geometry.BoundingBox;
import totah.lab.gaia.geometry.Point3D;
import totah.lab.gaia.molecule.Ligand;
import totah.lab.gaia.pocket.AlphaSphere;
import totah.lab.gaia.pocket.AlphaSphereSet;
import totah.lab.gaia.pocket.Pocket;
import totah.lab.gaia.structure.Atom;
import totah.lab.gaia.structure.Residue;
import totah.lab.gaia.structure.ResidueId;
import totah.lab.gaia.structure.Structure;

import java.util.List;
import java.util.Objects;

/**
 * Source-independent geometric calculations for pocket representations.
 *
 * <p>Distances involving residues and ligands are calculated using heavy atoms
 * unless specific atom names are supplied.</p>
 *
 * <p>Pocket overlap is calculated using the axis-aligned bounding boxes
 * produced by the selected pocket geometry strategy.</p>
 */
public final class PocketGeometry {

    private static final PocketGeometryStrategy ALPHA_SPHERE_GEOMETRY =
            new AlphaSpherePocketGeometry();

    private static final PocketGeometryStrategy RESIDUE_ATOM_GEOMETRY =
            new ResidueAtomPocketGeometry();

    private static final PocketGeometryStrategy REPORTED_CENTER_GEOMETRY =
            new ReportedCenterPocketGeometry();

    private static final PocketResidueSelection RESIDUE_SELECTION =
            new PocketResidueSelection();

    private PocketGeometry() {
    }

    public static Point3D alphaSphereCentroid(
            AlphaSphereSet sphereSet) {

        List<AlphaSphere> spheres = requireSpheres(sphereSet);

        double x = 0.0;
        double y = 0.0;
        double z = 0.0;

        for (AlphaSphere sphere : spheres) {
            Objects.requireNonNull(
                    sphere,
                    "Alpha-sphere set must not contain null spheres");

            Point3D center = Objects.requireNonNull(
                    sphere.center(),
                    "Alpha sphere center");

            requireFinitePoint(center, "Alpha sphere center");

            x += center.x();
            y += center.y();
            z += center.z();
        }

        double count = spheres.size();

        return new Point3D(
                x / count,
                y / count,
                z / count);
    }

    public static double alphaSphereCenterDistance(
            Pocket first,
            Pocket second) {

        Objects.requireNonNull(first, "first");
        Objects.requireNonNull(second, "second");

        Point3D firstCentroid =
                alphaSphereCentroid(requireSphereSet(first));

        Point3D secondCentroid =
                alphaSphereCentroid(requireSphereSet(second));

        return firstCentroid.distance(secondCentroid);
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
            Objects.requireNonNull(
                    sphere,
                    "Alpha-sphere set must not contain null spheres");

            Point3D center = Objects.requireNonNull(
                    sphere.center(),
                    "Alpha sphere center");

            requireFinitePoint(center, "Alpha sphere center");

            double radius = includeRadii
                    ? requireValidRadius(sphere.radius())
                    : 0.0;

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

    public static Point3D dimensions(
            BoundingBox bounds) {

        Objects.requireNonNull(bounds, "bounds");

        return new Point3D(
                bounds.width(),
                bounds.height(),
                bounds.depth());
    }

    public static double volume(
            BoundingBox bounds) {

        return Objects.requireNonNull(bounds, "bounds")
                .volume();
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
        Objects.requireNonNull(second, "second");

        return first.intersectionVolume(second);
    }

    public static double intersectionOverUnion(
            BoundingBox first,
            BoundingBox second) {

        Objects.requireNonNull(first, "first");
        Objects.requireNonNull(second, "second");

        return first.intersectionOverUnion(second);
    }

    public static PocketGeometryResult geometry(
            Structure structure,
            Pocket pocket) {

        Objects.requireNonNull(structure, "structure");
        Objects.requireNonNull(pocket, "pocket");

        PocketGeometryStrategy strategy =
                strategyFor(structure, pocket);

        List<ResidueId> unresolved =
                RESIDUE_SELECTION.unresolvedResidues(
                        structure,
                        pocket);

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

        if (first.basis() != second.basis()) {
            throw new IllegalArgumentException(
                    "Cannot compute overlap between different geometry "
                            + "bases: "
                            + first.basis()
                            + " vs "
                            + second.basis());
        }

        BoundingBox firstBounds = Objects.requireNonNull(
                first.bounds(),
                "first.bounds");

        BoundingBox secondBounds = Objects.requireNonNull(
                second.bounds(),
                "second.bounds");

        return new PocketOverlapResult(
                firstBounds.intersectionVolume(secondBounds),
                firstBounds.intersectionOverUnion(secondBounds),
                first.basis(),
                second.basis());
    }

    public static double calculateDistance(
            Residue first,
            Residue second) {

        Objects.requireNonNull(first, "first");
        Objects.requireNonNull(second, "second");

        return minimumHeavyAtomDistance(
                requireAtoms(first.getAtoms(), "first residue atoms"),
                requireAtoms(second.getAtoms(), "second residue atoms"),
                "Both residues must contain at least one heavy atom");
    }

    public static double calculateDistance(
            Residue first,
            String firstAtomName,
            Residue second,
            String secondAtomName) {

        Objects.requireNonNull(first, "first");
        Objects.requireNonNull(second, "second");

        String requiredFirstAtomName =
                Objects.requireNonNull(firstAtomName, "firstAtomName");

        String requiredSecondAtomName =
                Objects.requireNonNull(secondAtomName, "secondAtomName");

        Atom firstAtom = first.findAtom(requiredFirstAtomName)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Atom "
                                + requiredFirstAtomName
                                + " is not present in residue "
                                + residueDescription(first)));

        Atom secondAtom = second.findAtom(requiredSecondAtomName)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Atom "
                                + requiredSecondAtomName
                                + " is not present in residue "
                                + residueDescription(second)));

        Point3D firstPosition = requireAtomPosition(
                firstAtom,
                "first atom");

        Point3D secondPosition = requireAtomPosition(
                secondAtom,
                "second atom");

        return firstPosition.distance(secondPosition);
    }

    public static double calculateDistance(
            Residue residue,
            Ligand ligand) {

        Objects.requireNonNull(residue, "residue");
        Objects.requireNonNull(ligand, "ligand");

        List<Atom> heavyLigandAtoms =
                heavyLigandAtoms(ligand);

        return minimumHeavyAtomDistance(
                requireAtoms(residue.getAtoms(), "residue atoms"),
                heavyLigandAtoms,
                "Residue and ligand must contain heavy atoms");
    }

    public static boolean areNeighbors(
            Residue first,
            Residue second,
            double cutoffAngstroms) {

        Objects.requireNonNull(first, "first");
        Objects.requireNonNull(second, "second");
        validateCutoff(cutoffAngstroms);

        return hasContact(
                requireAtoms(first.getAtoms(), "first residue atoms"),
                requireAtoms(second.getAtoms(), "second residue atoms"),
                cutoffAngstroms);
    }

    public static boolean areNeighbors(
            Residue residue,
            Ligand ligand,
            double cutoffAngstroms) {

        Objects.requireNonNull(residue, "residue");
        Objects.requireNonNull(ligand, "ligand");
        validateCutoff(cutoffAngstroms);

        return hasContact(
                requireAtoms(residue.getAtoms(), "residue atoms"),
                heavyLigandAtoms(ligand),
                cutoffAngstroms);
    }

    public static int contactingAtomPairCount(
            Residue residue,
            Ligand ligand,
            double cutoffAngstroms) {

        Objects.requireNonNull(residue, "residue");
        Objects.requireNonNull(ligand, "ligand");
        validateCutoff(cutoffAngstroms);

        List<Atom> residueAtoms =
                requireAtoms(residue.getAtoms(), "residue atoms");

        List<Atom> ligandAtoms =
                heavyLigandAtoms(ligand);

        double cutoffSquared =
                cutoffAngstroms * cutoffAngstroms;

        int count = 0;

        for (Atom residueAtom : residueAtoms) {
            Objects.requireNonNull(
                    residueAtom,
                    "Residue atom list must not contain null atoms");

            if (!residueAtom.isHeavyAtom()) {
                continue;
            }

            Point3D residuePosition =
                    requireAtomPosition(residueAtom, "residue atom");

            for (Atom ligandAtom : ligandAtoms) {
                Point3D ligandPosition =
                        requireAtomPosition(ligandAtom, "ligand atom");

                if (residuePosition.distanceSquared(ligandPosition)
                        <= cutoffSquared) {
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

        List<Atom> heavyLigandAtoms =
                heavyLigandAtoms(ligand);

        return structure.getChains().stream()
                .flatMap(chain -> chain.residues().stream())
                .filter(Objects::nonNull)
                .filter(residue -> hasContact(
                        requireAtoms(
                                residue.getAtoms(),
                                "residue atoms"),
                        heavyLigandAtoms,
                        cutoffAngstroms))
                .toList();
    }

    private static AlphaSphereSet requireSphereSet(
            Pocket pocket) {

        Objects.requireNonNull(pocket, "pocket");

        return pocket.alphaSphereSet()
                .filter(set -> set.spheres() != null)
                .filter(set -> !set.spheres().isEmpty())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Pocket has no alpha spheres: "
                                + pocket.id()));
    }

    private static PocketGeometryStrategy strategyFor(
            Structure structure,
            Pocket pocket) {

        if (pocket.alphaSphereSet()
                .filter(set -> set.spheres() != null)
                .filter(set -> !set.spheres().isEmpty())
                .isPresent()) {
            return ALPHA_SPHERE_GEOMETRY;
        }

        boolean hasResolvedHeavyAtoms =
                RESIDUE_SELECTION
                        .resolvedResidues(structure, pocket)
                        .stream()
                        .filter(Objects::nonNull)
                        .flatMap(residue ->
                                requireAtoms(
                                        residue.getAtoms(),
                                        "residue atoms")
                                        .stream())
                        .filter(Objects::nonNull)
                        .anyMatch(Atom::isHeavyAtom);

        return hasResolvedHeavyAtoms
                ? RESIDUE_ATOM_GEOMETRY
                : REPORTED_CENTER_GEOMETRY;
    }

    private static List<AlphaSphere> requireSpheres(
            AlphaSphereSet sphereSet) {

        Objects.requireNonNull(sphereSet, "sphereSet");

        List<AlphaSphere> spheres = Objects.requireNonNull(
                sphereSet.spheres(),
                "sphereSet.spheres");

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

        Objects.requireNonNull(first, "first");
        Objects.requireNonNull(second, "second");
        Objects.requireNonNull(emptyMessage, "emptyMessage");

        double minimumSquared =
                Double.POSITIVE_INFINITY;

        for (Atom firstAtom : first) {
            Objects.requireNonNull(
                    firstAtom,
                    "First atom list must not contain null atoms");

            if (!firstAtom.isHeavyAtom()) {
                continue;
            }

            Point3D firstPosition =
                    requireAtomPosition(firstAtom, "first atom");

            for (Atom secondAtom : second) {
                Objects.requireNonNull(
                        secondAtom,
                        "Second atom list must not contain null atoms");

                if (!secondAtom.isHeavyAtom()) {
                    continue;
                }

                Point3D secondPosition =
                        requireAtomPosition(secondAtom, "second atom");

                minimumSquared = Math.min(
                        minimumSquared,
                        firstPosition.distanceSquared(secondPosition));
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

        Objects.requireNonNull(first, "first");
        Objects.requireNonNull(second, "second");
        validateCutoff(cutoffAngstroms);

        double cutoffSquared =
                cutoffAngstroms * cutoffAngstroms;

        for (Atom firstAtom : first) {
            Objects.requireNonNull(
                    firstAtom,
                    "First atom list must not contain null atoms");

            if (!firstAtom.isHeavyAtom()) {
                continue;
            }

            Point3D firstPosition =
                    requireAtomPosition(firstAtom, "first atom");

            for (Atom secondAtom : second) {
                Objects.requireNonNull(
                        secondAtom,
                        "Second atom list must not contain null atoms");

                if (!secondAtom.isHeavyAtom()) {
                    continue;
                }

                Point3D secondPosition =
                        requireAtomPosition(secondAtom, "second atom");

                if (firstPosition.distanceSquared(secondPosition)
                        <= cutoffSquared) {
                    return true;
                }
            }
        }

        return false;
    }

    private static List<Atom> ligandAtoms(
            Ligand ligand) {

        Objects.requireNonNull(ligand, "ligand");

        Structure ligandStructure = Objects.requireNonNull(
                ligand.structure(),
                "ligand.structure");

        return ligandStructure.getChains().stream()
                .flatMap(chain -> chain.residues().stream())
                .filter(Objects::nonNull)
                .flatMap(residue ->
                        requireAtoms(
                                residue.getAtoms(),
                                "ligand residue atoms")
                                .stream())
                .filter(Objects::nonNull)
                .toList();
    }

    private static List<Atom> heavyLigandAtoms(
            Ligand ligand) {

        List<Atom> heavyAtoms = ligandAtoms(ligand).stream()
                .filter(Atom::isHeavyAtom)
                .toList();

        if (heavyAtoms.isEmpty()) {
            throw new IllegalArgumentException(
                    "Ligand must contain at least one heavy atom");
        }

        return heavyAtoms;
    }

    private static List<Atom> requireAtoms(
            List<Atom> atoms,
            String name) {

        return Objects.requireNonNull(atoms, name);
    }

    private static Point3D requireAtomPosition(
            Atom atom,
            String description) {

        Objects.requireNonNull(atom, description);

        Point3D position = Objects.requireNonNull(
                atom.getPosition(),
                description + " position");

        requireFinitePoint(
                position,
                description + " position");

        return position;
    }

    private static void requireFinitePoint(
            Point3D point,
            String description) {

        if (!Double.isFinite(point.x())
                || !Double.isFinite(point.y())
                || !Double.isFinite(point.z())) {
            throw new IllegalArgumentException(
                    description
                            + " must contain finite coordinates");
        }
    }

    private static double requireValidRadius(
            double radius) {

        if (!Double.isFinite(radius) || radius < 0.0) {
            throw new IllegalArgumentException(
                    "Alpha-sphere radius must be finite "
                            + "and non-negative: "
                            + radius);
        }

        return radius;
    }

    private static void validateCutoff(
            double cutoffAngstroms) {

        if (!Double.isFinite(cutoffAngstroms)
                || cutoffAngstroms <= 0.0) {
            throw new IllegalArgumentException(
                    "Cutoff must be finite and greater than zero");
        }

        double cutoffSquared =
                cutoffAngstroms * cutoffAngstroms;

        if (!Double.isFinite(cutoffSquared)) {
            throw new IllegalArgumentException(
                    "Cutoff is too large to square safely: "
                            + cutoffAngstroms);
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
                    name
                            + " interval must be finite and ordered");
        }
    }

    private static String residueDescription(
            Residue residue) {

        return String.valueOf(residue.getName())
                + residue.getNumber();
    }
}