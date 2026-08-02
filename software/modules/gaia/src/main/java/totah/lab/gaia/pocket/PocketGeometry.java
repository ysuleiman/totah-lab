package totah.lab.gaia.pocket;

import totah.lab.gaia.geometry.BoundingBox;
import totah.lab.gaia.geometry.Point3D;
import totah.lab.gaia.molecule.Ligand;
import totah.lab.gaia.structure.Atom;
import totah.lab.gaia.structure.Residue;
import totah.lab.gaia.structure.ResidueId;
import totah.lab.gaia.structure.Structure;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Generic geometry operations for Gaia pockets.
 *
 * <p>This class operates on pocket residue identities resolved against a
 * concrete structure. Predictor-specific geometry, such as fpocket alpha
 * spheres, must not be implemented here.</p>
 */
public final class PocketGeometry {

    private PocketGeometry() {
    }

    /**
     * Resolves all pocket residue IDs.
     *
     * @throws IllegalArgumentException when one or more pocket residue IDs
     *                                  cannot be resolved
     */
    public static List<Residue> resolveResidues(
            Structure structure,
            Pocket pocket) {

        Objects.requireNonNull(structure, "structure");
        Objects.requireNonNull(pocket, "pocket");

        List<Residue> resolved = new ArrayList<>();
        List<ResidueId> unresolved = new ArrayList<>();

        for (ResidueId residueId : pocket.residues()) {
            structure.findResidue(residueId)
                    .ifPresentOrElse(
                            resolved::add,
                            () -> unresolved.add(residueId));
        }

        if (!unresolved.isEmpty()) {
            throw new IllegalArgumentException(
                    "Cannot resolve pocket residues: " + unresolved);
        }

        return List.copyOf(resolved);
    }

    public static List<ResidueId> unresolvedResidues(
            Structure structure,
            Pocket pocket) {

        Objects.requireNonNull(structure, "structure");
        Objects.requireNonNull(pocket, "pocket");

        return pocket.residues().stream()
                .filter(id -> structure.findResidue(id).isEmpty())
                .toList();
    }

    public static Point3D calculateHeavyAtomCentroid(
            Structure structure,
            Pocket pocket) {

        return calculateHeavyAtomCentroid(
                resolveResidues(structure, pocket));
    }

    public static Point3D calculateHeavyAtomCentroid(
            List<Residue> residues) {

        Objects.requireNonNull(residues, "residues");

        double sumX = 0.0;
        double sumY = 0.0;
        double sumZ = 0.0;
        int atomCount = 0;

        for (Residue residue : residues) {
            Objects.requireNonNull(residue, "residues contains null");

            for (Atom atom : residue.getAtoms()) {
                if (!atom.isHeavyAtom()) {
                    continue;
                }

                Point3D position = atom.getPosition();

                sumX += position.x();
                sumY += position.y();
                sumZ += position.z();
                atomCount++;
            }
        }

        if (atomCount == 0) {
            throw new IllegalArgumentException(
                    "Cannot calculate centroid: no heavy atoms were found");
        }

        return new Point3D(
                sumX / atomCount,
                sumY / atomCount,
                sumZ / atomCount);
    }

    public static BoundingBox calculateHeavyAtomBounds(
            Structure structure,
            Pocket pocket) {

        return calculateHeavyAtomBounds(
                resolveResidues(structure, pocket));
    }

    public static BoundingBox calculateHeavyAtomBounds(
            List<Residue> residues) {

        Objects.requireNonNull(residues, "residues");

        double minX = Double.POSITIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double minZ = Double.POSITIVE_INFINITY;

        double maxX = Double.NEGATIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        double maxZ = Double.NEGATIVE_INFINITY;

        int atomCount = 0;

        for (Residue residue : residues) {
            Objects.requireNonNull(residue, "residues contains null");

            for (Atom atom : residue.getAtoms()) {
                if (!atom.isHeavyAtom()) {
                    continue;
                }

                Point3D position = atom.getPosition();

                minX = Math.min(minX, position.x());
                minY = Math.min(minY, position.y());
                minZ = Math.min(minZ, position.z());

                maxX = Math.max(maxX, position.x());
                maxY = Math.max(maxY, position.y());
                maxZ = Math.max(maxZ, position.z());

                atomCount++;
            }
        }

        if (atomCount == 0) {
            throw new IllegalArgumentException(
                    "Cannot calculate bounds: no heavy atoms were found");
        }

        return new BoundingBox(
                new Point3D(minX, minY, minZ),
                new Point3D(maxX, maxY, maxZ));
    }

    public static BoundingBox calculateDockingBox(
            Structure structure,
            Pocket pocket,
            double padding) {

        return expand(
                calculateHeavyAtomBounds(structure, pocket),
                padding);
    }

    public static BoundingBox expand(
            BoundingBox box,
            double padding) {

        Objects.requireNonNull(box, "box");
        validateNonNegativeFinite(padding, "padding");

        Point3D min = box.min();
        Point3D max = box.max();

        return new BoundingBox(
                new Point3D(
                        min.x() - padding,
                        min.y() - padding,
                        min.z() - padding),
                new Point3D(
                        max.x() + padding,
                        max.y() + padding,
                        max.z() + padding));
    }

    public static Point3D boxSize(BoundingBox box) {
        Objects.requireNonNull(box, "box");

        Point3D min = box.min();
        Point3D max = box.max();

        return new Point3D(
                max.x() - min.x(),
                max.y() - min.y(),
                max.z() - min.z());
    }

    public static Point3D boxCenter(BoundingBox box) {
        Objects.requireNonNull(box, "box");

        Point3D min = box.min();
        Point3D max = box.max();

        return new Point3D(
                (min.x() + max.x()) / 2.0,
                (min.y() + max.y()) / 2.0,
                (min.z() + max.z()) / 2.0);
    }

    public static double boxVolume(BoundingBox box) {
        Point3D size = boxSize(box);
        return size.x() * size.y() * size.z();
    }

    public static double maximumHeavyAtomDistance(
            Structure structure,
            Pocket pocket,
            Point3D center) {

        Objects.requireNonNull(center, "center");

        double maximumSquared = 0.0;
        boolean found = false;

        for (Residue residue : resolveResidues(structure, pocket)) {
            for (Atom atom : residue.getAtoms()) {
                if (!atom.isHeavyAtom()) {
                    continue;
                }

                maximumSquared = Math.max(
                        maximumSquared,
                        squaredDistance(center, atom.getPosition()));

                found = true;
            }
        }

        if (!found) {
            throw new IllegalArgumentException(
                    "Pocket residues contain no heavy atoms");
        }

        return Math.sqrt(maximumSquared);
    }

    public static double heavyAtomRadiusOfGyration(
            Structure structure,
            Pocket pocket) {

        List<Residue> residues = resolveResidues(structure, pocket);
        Point3D centroid = calculateHeavyAtomCentroid(residues);

        double sumSquaredDistance = 0.0;
        int atomCount = 0;

        for (Residue residue : residues) {
            for (Atom atom : residue.getAtoms()) {
                if (!atom.isHeavyAtom()) {
                    continue;
                }

                sumSquaredDistance += squaredDistance(
                        atom.getPosition(),
                        centroid);

                atomCount++;
            }
        }

        if (atomCount == 0) {
            throw new IllegalArgumentException(
                    "Pocket residues contain no heavy atoms");
        }

        return Math.sqrt(sumSquaredDistance / atomCount);
    }

    public static double centerDistance(
            BoundingBox first,
            BoundingBox second) {

        Objects.requireNonNull(first, "first");
        Objects.requireNonNull(second, "second");

        return Math.sqrt(
                squaredDistance(
                        boxCenter(first),
                        boxCenter(second)));
    }

    public static double intersectionVolume(
            BoundingBox first,
            BoundingBox second) {

        Objects.requireNonNull(first, "first");
        Objects.requireNonNull(second, "second");

        double overlapX = Math.max(
                0.0,
                Math.min(first.max().x(), second.max().x())
                        - Math.max(first.min().x(), second.min().x()));

        double overlapY = Math.max(
                0.0,
                Math.min(first.max().y(), second.max().y())
                        - Math.max(first.min().y(), second.min().y()));

        double overlapZ = Math.max(
                0.0,
                Math.min(first.max().z(), second.max().z())
                        - Math.max(first.min().z(), second.min().z()));

        return overlapX * overlapY * overlapZ;
    }

    public static double intersectionOverUnion(
            BoundingBox first,
            BoundingBox second) {

        Objects.requireNonNull(first, "first");
        Objects.requireNonNull(second, "second");

        double intersection = intersectionVolume(first, second);
        double union =
                boxVolume(first)
                        + boxVolume(second)
                        - intersection;

        return union == 0.0 ? 0.0 : intersection / union;
    }

    public static List<Residue> residueNeighbors(
            List<Residue> residues,
            Residue target,
            double cutoff) {

        Objects.requireNonNull(residues, "residues");
        Objects.requireNonNull(target, "target");
        validatePositiveFinite(cutoff, "cutoff");

        return residues.stream()
                .filter(Objects::nonNull)
                .filter(residue -> !sameResidue(target, residue))
                .filter(residue -> areNeighbors(target, residue, cutoff))
                .toList();
    }

    public static List<Residue> pocketNeighbors(
            Structure structure,
            Pocket pocket,
            double cutoff) {

        Objects.requireNonNull(structure, "structure");
        Objects.requireNonNull(pocket, "pocket");
        validatePositiveFinite(cutoff, "cutoff");

        List<Residue> pocketResidues =
                resolveResidues(structure, pocket);

        return structureResidues(structure).stream()
                .filter(residue -> !pocketResidues.contains(residue))
                .filter(candidate ->
                        isNeighborOfAny(
                                candidate,
                                pocketResidues,
                                cutoff))
                .toList();
    }

    public static List<Residue> ligandNeighbors(
            Structure structure,
            Ligand ligand,
            double cutoff) {

        Objects.requireNonNull(structure, "structure");
        Objects.requireNonNull(ligand, "ligand");
        validatePositiveFinite(cutoff, "cutoff");

        return structureResidues(structure).stream()
                .filter(residue ->
                        areNeighbors(residue, ligand, cutoff))
                .toList();
    }

    public static boolean areNeighbors(
            Residue first,
            Residue second,
            double cutoff) {

        return calculateDistance(first, second) <= cutoff;
    }

    public static boolean areNeighbors(
            Residue residue,
            Ligand ligand,
            double cutoff) {

        return calculateDistance(residue, ligand) <= cutoff;
    }

    public static int contactingAtomPairCount(
            Residue residue,
            Ligand ligand,
            double cutoff) {

        Objects.requireNonNull(residue, "residue");
        Objects.requireNonNull(ligand, "ligand");
        validatePositiveFinite(cutoff, "cutoff");

        double cutoffSquared = cutoff * cutoff;
        int count = 0;

        for (Atom residueAtom : residue.getAtoms()) {
            if (!residueAtom.isHeavyAtom()) {
                continue;
            }

            for (Atom ligandAtom : ligandAtoms(ligand)) {
                if (!ligandAtom.isHeavyAtom()) {
                    continue;
                }

                if (squaredDistance(
                        residueAtom.getPosition(),
                        ligandAtom.getPosition()) <= cutoffSquared) {
                    count++;
                }
            }
        }

        return count;
    }

    public static double calculateDistance(
            Residue first,
            Residue second) {

        Objects.requireNonNull(first, "first");
        Objects.requireNonNull(second, "second");

        return minimumHeavyAtomDistance(
                first.getAtoms(),
                second.getAtoms(),
                "Both residues must contain at least one heavy atom");
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

    public static double calculateDistance(
            Residue first,
            String firstAtomName,
            Residue second,
            String secondAtomName) {

        Objects.requireNonNull(first, "first");
        Objects.requireNonNull(firstAtomName, "firstAtomName");
        Objects.requireNonNull(second, "second");
        Objects.requireNonNull(secondAtomName, "secondAtomName");

        Atom firstAtom = first.findAtom(firstAtomName)
                .orElseThrow(() -> missingAtom(first, firstAtomName));

        Atom secondAtom = second.findAtom(secondAtomName)
                .orElseThrow(() -> missingAtom(second, secondAtomName));

        return Math.sqrt(
                squaredDistance(
                        firstAtom.getPosition(),
                        secondAtom.getPosition()));
    }

    private static boolean isNeighborOfAny(
            Residue candidate,
            List<Residue> pocketResidues,
            double cutoff) {

        for (Residue pocketResidue : pocketResidues) {
            if (areNeighbors(candidate, pocketResidue, cutoff)) {
                return true;
            }
        }

        return false;
    }

    private static double minimumHeavyAtomDistance(
            List<Atom> firstAtoms,
            List<Atom> secondAtoms,
            String emptyMessage) {

        double minimumSquared = Double.POSITIVE_INFINITY;

        for (Atom first : firstAtoms) {
            if (!first.isHeavyAtom()) {
                continue;
            }

            for (Atom second : secondAtoms) {
                if (!second.isHeavyAtom()) {
                    continue;
                }

                minimumSquared = Math.min(
                        minimumSquared,
                        squaredDistance(
                                first.getPosition(),
                                second.getPosition()));
            }
        }

        if (!Double.isFinite(minimumSquared)) {
            throw new IllegalArgumentException(emptyMessage);
        }

        return Math.sqrt(minimumSquared);
    }

    private static boolean sameResidue(
            Residue first,
            Residue second) {

        return first == second;
    }

    private static IllegalArgumentException missingAtom(
            Residue residue,
            String atomName) {

        return new IllegalArgumentException(
                "Atom %s is not present in residue %s%d"
                        .formatted(atomName, residue.getName(), residue.getNumber()));
    }

    private static List<Residue> structureResidues(Structure structure) {
        return structure.getChains().stream()
                .flatMap(chain -> chain.residues().stream())
                .toList();
    }

    private static List<Atom> ligandAtoms(Ligand ligand) {
        return ligand.structure().getChains().stream()
                .flatMap(chain -> chain.residues().stream())
                .flatMap(residue -> residue.getAtoms().stream())
                .toList();
    }

    private static void validatePositiveFinite(
            double value,
            String name) {

        if (!Double.isFinite(value) || value <= 0.0) {
            throw new IllegalArgumentException(
                    name + " must be finite and greater than zero");
        }
    }

    private static void validateNonNegativeFinite(
            double value,
            String name) {

        if (!Double.isFinite(value) || value < 0.0) {
            throw new IllegalArgumentException(
                    name + " must be finite and non-negative");
        }
    }

    private static double squaredDistance(
            Point3D first,
            Point3D second) {

        double dx = first.x() - second.x();
        double dy = first.y() - second.y();
        double dz = first.z() - second.z();

        return dx * dx + dy * dy + dz * dz;
    }
}
