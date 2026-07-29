package totah.lab.pocket.geometry;

import totah.lab.pocket.Dimensions;
import totah.lab.pocket.Pocket;
import totah.lab.pocket.PocketBox;
import totah.lab.pocket.Sphere;
import totah.lab.ligand.Ligand;
import totah.lab.protein.*;

import java.util.*;

public final class PocketGeometry {

    private PocketGeometry() {
    }

    public static Point3D calculateHeavyAtomCentroid(Pocket pocket) {
        Objects.requireNonNull(pocket, "pocket");
        double sumX = 0.0;
        double sumY = 0.0;
        double sumZ = 0.0;
        int atomCount = 0;
        for (Residue residue : pocket.getResidues()) {
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
                    "Cannot calculate pocket centroid: pocket contains no heavy atoms");
        }

        return new Point3D(
                sumX / atomCount,
                sumY / atomCount,
                sumZ / atomCount
        );
    }

    public static PocketBox calculateHeavyAtomBox(Pocket pocket) {
        Objects.requireNonNull(pocket, "pocket");

        double minX = Double.POSITIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double minZ = Double.POSITIVE_INFINITY;

        double maxX = Double.NEGATIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        double maxZ = Double.NEGATIVE_INFINITY;

        int atomCount = 0;

        for (Residue residue : pocket.getResidues()) {
            for (Atom atom : residue.getAtoms()) {
                if (!atom.isHeavyAtom()) {
                    continue;
                }

                Point3D p = atom.getPosition();

                minX = Math.min(minX, p.x());
                minY = Math.min(minY, p.y());
                minZ = Math.min(minZ, p.z());

                maxX = Math.max(maxX, p.x());
                maxY = Math.max(maxY, p.y());
                maxZ = Math.max(maxZ, p.z());

                atomCount++;
            }
        }

        if (atomCount == 0) {
            throw new IllegalArgumentException(
                    "Cannot calculate pocket box: pocket contains no heavy atoms");
        }

        return new PocketBox(
                new Point3D(minX, minY, minZ),
                new Point3D(maxX, maxY, maxZ)
        );
    }

    public static PocketBox calculateDockingBox(Pocket pocket, double padding) {
        if (!Double.isFinite(padding) || padding < 0.0) {
            throw new IllegalArgumentException(
                    "Padding must be finite and non-negative");
        }
        return expand(calculateHeavyAtomBox(pocket), padding);
    }

    public static PocketBox expand(
            PocketBox box,
            double padding) {

        Objects.requireNonNull(box, "box");

        if (!Double.isFinite(padding) || padding < 0.0) {
            throw new IllegalArgumentException(
                    "Padding must be finite and non-negative");
        }

        Point3D min = box.getMin();
        Point3D max = box.getMax();

        return new PocketBox(
                new Point3D(
                        min.x() - padding,
                        min.y() - padding,
                        min.z() - padding),
                new Point3D(
                        max.x() + padding,
                        max.y() + padding,
                        max.z() + padding)
        );
    }

    public static Point3D boxSize(PocketBox box) {
        Objects.requireNonNull(box, "box");

        Point3D min = box.getMin();
        Point3D max = box.getMax();

        return new Point3D(
                max.x() - min.x(),
                max.y() - min.y(),
                max.z() - min.z()
        );
    }

    public static Point3D boxCenter(PocketBox box) {
        Objects.requireNonNull(box, "box");

        Point3D min = box.getMin();
        Point3D max = box.getMax();

        return new Point3D(
                (min.x() + max.x()) / 2.0,
                (min.y() + max.y()) / 2.0,
                (min.z() + max.z()) / 2.0
        );
    }

    public static double boxVolume(PocketBox box) {
        Dimensions dimensions = boxDimensions(box);

        return dimensions.getBoundingVolume();
    }

    public static Dimensions boxDimensions(PocketBox box) {
        Objects.requireNonNull(box, "box");

        Point3D min = box.getMin();
        Point3D max = box.getMax();

        return new Dimensions(
                max.x() - min.x(),
                max.y() - min.y(),
                max.z() - min.z()
        );
    }

    public static Point3D calculateAlphaSphereCentroid(
            Pocket pocket) {

        Objects.requireNonNull(pocket, "pocket");
        List<Sphere> spheres = pocket.getAttribute("alpha_spheres");

        if (spheres == null || spheres.isEmpty()) {
            throw new IllegalArgumentException(
                    "Cannot calculate alpha-sphere centroid: pocket has no alpha spheres");
        }

        double sumX = 0.0;
        double sumY = 0.0;
        double sumZ = 0.0;

        for (Sphere sphere : spheres) {
            sumX += sphere.x();
            sumY += sphere.y();
            sumZ += sphere.z();
        }

        int count = spheres.size();

        return new Point3D(
                sumX / count,
                sumY / count,
                sumZ / count
        );
    }

    public static Point3D calculateRadiusWeightedAlphaSphereCentroid(
            Pocket pocket) {

        Objects.requireNonNull(pocket, "pocket");

        List<Sphere> spheres = pocket.getAttribute("alpha_spheres");
        if(spheres==null||spheres.isEmpty()){
            throw new IllegalArgumentException(
                    "Pocket has no alpha spheres");
        }

        double weightedX = 0.0;
        double weightedY = 0.0;
        double weightedZ = 0.0;
        double totalWeight = 0.0;

        for (Sphere sphere : spheres) {
            double radius = sphere.radius();
            double weight = radius * radius * radius;

            weightedX += sphere.x() * weight;
            weightedY += sphere.y() * weight;
            weightedZ += sphere.z() * weight;
            totalWeight += weight;
        }

        if (totalWeight == 0.0) {
            throw new IllegalArgumentException(
                    "Alpha spheres have zero total volume");
        }

        return new Point3D(
                weightedX / totalWeight,
                weightedY / totalWeight,
                weightedZ / totalWeight
        );
    }

    public static PocketBox calculateAlphaSphereBox(
            Pocket pocket,
            boolean includeSphereRadius) {

        Objects.requireNonNull(pocket, "pocket");

        List<Sphere> spheres = pocket.getAttribute("alpha_spheres");

        if (spheres == null || spheres.isEmpty()) {
            throw new IllegalArgumentException(
                    "Cannot calculate alpha-sphere box: pocket has no alpha spheres");
        }

        double minX = Double.POSITIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double minZ = Double.POSITIVE_INFINITY;

        double maxX = Double.NEGATIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        double maxZ = Double.NEGATIVE_INFINITY;

        for (Sphere sphere : spheres) {
            double radius = includeSphereRadius
                    ? sphere.radius()
                    : 0.0;

            minX = Math.min(minX, sphere.x() - radius);
            minY = Math.min(minY, sphere.y() - radius);
            minZ = Math.min(minZ, sphere.z() - radius);

            maxX = Math.max(maxX, sphere.x() + radius);
            maxY = Math.max(maxY, sphere.y() + radius);
            maxZ = Math.max(maxZ, sphere.z() + radius);
        }

        return new PocketBox(
                new Point3D(minX, minY, minZ),
                new Point3D(maxX, maxY, maxZ)
        );
    }

    public static double maximumHeavyAtomDistance(
            Pocket pocket,
            Point3D center) {

        Objects.requireNonNull(pocket, "pocket");
        Objects.requireNonNull(center, "center");

        double maximumSquared = 0.0;
        boolean found = false;

        for (Residue residue : pocket.getResidues()) {
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
                    "Pocket contains no heavy atoms");
        }

        return Math.sqrt(maximumSquared);
    }

    public static double heavyAtomRadiusOfGyration(
            Pocket pocket) {

        Point3D centroid = calculateHeavyAtomCentroid(pocket);

        double sumSquaredDistance = 0.0;
        int count = 0;

        for (Residue residue : pocket.getResidues()) {
            for (Atom atom : residue.getAtoms()) {
                if (!atom.isHeavyAtom()) {
                    continue;
                }

                sumSquaredDistance += squaredDistance(
                        atom.getPosition(),
                        centroid);

                count++;
            }
        }

        if (count == 0) {
            throw new IllegalArgumentException(
                    "Pocket contains no heavy atoms");
        }

        return Math.sqrt(sumSquaredDistance / count);
    }

    public static double centerDistance(
            Pocket first,
            Pocket second) {

        Point3D firstCenter =
                calculateAlphaSphereCentroid(first);

        Point3D secondCenter =
                calculateAlphaSphereCentroid(second);

        return Math.sqrt(
                squaredDistance(firstCenter, secondCenter));
    }

    public static double intersectionVolume(
            PocketBox first,
            PocketBox second) {

        double overlapX = Math.max(
                0.0,
                Math.min(first.getMax().x(), second.getMax().x())
                        - Math.max(first.getMin().x(), second.getMin().x()));

        double overlapY = Math.max(
                0.0,
                Math.min(first.getMax().y(), second.getMax().y())
                        - Math.max(first.getMin().y(), second.getMin().y()));

        double overlapZ = Math.max(
                0.0,
                Math.min(first.getMax().z(), second.getMax().z())
                        - Math.max(first.getMin().z(), second.getMin().z()));

        return overlapX * overlapY * overlapZ;
    }

    public static double intersectionOverUnion(
            PocketBox first,
            PocketBox second) {

        double intersection = intersectionVolume(first, second);

        double union =
                boxVolume(first)
                        + boxVolume(second)
                        - intersection;

        return union == 0.0
                ? 0.0
                : intersection / union;
    }

    public static List<Residue> residueNeighbors(
            Pocket pocket,
            Residue target,
            double cutoff) {
        Objects.requireNonNull(pocket, "pocket");
        Objects.requireNonNull(target, "target");
        if (!Double.isFinite(cutoff) || cutoff <= 0.0) {
            throw new IllegalArgumentException(
                    "Cutoff must be finite and greater than zero");
        }
        List<Residue> neighbors = new ArrayList<>();
        for (Residue residue : pocket.getResidues()) {
            if (sameResidue(target, residue)) {
                continue;
            }
            if (areNeighbors(target, residue, cutoff)) {
                neighbors.add(residue);
            }
        }
        return neighbors;
    }

    public static List<Residue> residueNeighbors(
            List<Residue> residues,
            Residue target,
            double cutoff) {

        Objects.requireNonNull(residues, "residues");
        Objects.requireNonNull(target, "target");
        if (!Double.isFinite(cutoff) || cutoff <= 0.0) {
            throw new IllegalArgumentException(
                    "Cutoff must be finite and greater than zero");
        }
        List<Residue> neighbors = new ArrayList<>();
        for (Residue residue : residues) {
            if (sameResidue(target, residue)) {
                continue;
            }
            if (areNeighbors(target, residue, cutoff)) {
                neighbors.add(residue);
            }
        }
        return neighbors;
    }

    public static List<Residue> ligandNeighbors(
            Structure structure,
            Ligand ligand,
            double cutoff) {
        Objects.requireNonNull(structure, "structure");
        Objects.requireNonNull(ligand, "ligand");
        if (!Double.isFinite(cutoff) || cutoff <= 0.0) {
            throw new IllegalArgumentException(
                    "Cutoff must be finite and greater than zero");
        }
        return structure.getResidues().stream()
                .filter(residue -> areNeighbors(residue, ligand, cutoff))
                .toList();
    }

    public static boolean areNeighbors(
            Residue residue,
            Ligand ligand,
            double cutoff) {
        return contactingAtomPairCount(residue, ligand, cutoff) > 0;
    }

    public static int contactingAtomPairCount(
            Residue residue,
            Ligand ligand,
            double cutoff) {
        Objects.requireNonNull(residue, "residue");
        Objects.requireNonNull(ligand, "ligand");
        if (!Double.isFinite(cutoff) || cutoff <= 0.0) {
            throw new IllegalArgumentException(
                    "Cutoff must be finite and greater than zero");
        }
        double cutoffSquared = cutoff * cutoff;
        int count = 0;
        for (Atom residueAtom : residue.getAtoms()) {
            if (!residueAtom.isHeavyAtom()) {
                continue;
            }
            for (Atom ligandAtom : ligand.atoms()) {
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
            Residue residue,
            Ligand ligand) {
        Objects.requireNonNull(residue, "residue");
        Objects.requireNonNull(ligand, "ligand");
        double minimumSquared = Double.POSITIVE_INFINITY;
        for (Atom residueAtom : residue.getAtoms()) {
            if (!residueAtom.isHeavyAtom()) {
                continue;
            }
            for (Atom ligandAtom : ligand.atoms()) {
                if (!ligandAtom.isHeavyAtom()) {
                    continue;
                }
                minimumSquared = Math.min(
                        minimumSquared,
                        squaredDistance(
                                residueAtom.getPosition(),
                                ligandAtom.getPosition()));
            }
        }
        if (!Double.isFinite(minimumSquared)) {
            throw new IllegalArgumentException(
                    "Residue and ligand must contain heavy atoms");
        }
        return Math.sqrt(minimumSquared);
    }

    private static boolean sameResidue(
            Residue first,
            Residue second) {
        return first.getNumber() == second.getNumber()
                && Objects.equals(
                first.getChain(),
                second.getChain())
                && Objects.equals(
                first.getInsertionCode(),
                second.getInsertionCode());
    }


    public static boolean areNeighbors(
            Residue first,
            Residue second,
            double cutoff) {

        if (cutoff <= 0.0) {
            throw new IllegalArgumentException(
                    "Cutoff must be greater than zero");
        }
        double cutoffSquared = cutoff * cutoff;
        for (Atom firstAtom : first.getAtoms()) {
            if (!firstAtom.isHeavyAtom()) {
                continue;
            }
            for (Atom secondAtom : second.getAtoms()) {
                if (!secondAtom.isHeavyAtom()) {
                    continue;
                }
                Point3D a = firstAtom.getPosition();
                Point3D b = secondAtom.getPosition();
                double dx = a.x() - b.x();
                double dy = a.y() - b.y();
                double dz = a.z() - b.z();
                double distanceSquared =
                        dx * dx + dy * dy + dz * dz;
                if (distanceSquared <= cutoffSquared) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Returns structure residues that are not assigned to the pocket but have
     * at least one heavy atom within {@code cutoff} Å of a pocket heavy atom.
     */
    public static List<Residue> pocketNeighbors(
            Structure structure, Pocket pocket, double cutoff) {
        Objects.requireNonNull(structure, "structure");
        Objects.requireNonNull(pocket, "pocket");

        if (!Double.isFinite(cutoff) || cutoff <= 0.0) {
            throw new IllegalArgumentException(
                    "Cutoff must be finite and greater than zero");
        }
        Set<ResidueKey> pocketResidueKeys = new HashSet<>();
        for (Residue residue : pocket.getResidues()) {
            pocketResidueKeys.add(ResidueKey.from(residue));
        }
        List<Residue> neighbors = new ArrayList<>();
        for (Residue candidate : structure.getResidues()) {
            if (pocketResidueKeys.contains(ResidueKey.from(candidate))) {
                continue;
            }
            if (isPocketNeighbor(candidate, pocket, cutoff)) {
                neighbors.add(candidate);
            }
        }
        return neighbors;
    }

    private static boolean isPocketNeighbor(
            Residue candidate,
            Pocket pocket,
            double cutoff) {
        double cutoffSquared = cutoff * cutoff;
        for (Atom candidateAtom : candidate.getAtoms()) {
            if (!candidateAtom.isHeavyAtom()) {
                continue;
            }
            for (Residue pocketResidue : pocket.getResidues()) {
                for (Atom pocketAtom : pocketResidue.getAtoms()) {
                    if (!pocketAtom.isHeavyAtom()) {
                        continue;
                    }
                    if (squaredDistance(
                            candidateAtom.getPosition(),
                            pocketAtom.getPosition()) <= cutoffSquared) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static double squaredDistance(Point3D a, Point3D b) {
        double dx = a.x() - b.x();
        double dy = a.y() - b.y();
        double dz = a.z() - b.z();
        return dx * dx + dy * dy + dz * dz;
    }


    private record ResidueKey(
            String chain,
            int number,
            char insertionCode) {

        private static ResidueKey from(Residue residue) {
            return new ResidueKey(
                    residue.getChain(),
                    residue.getNumber(),
                    residue.getInsertionCode()
            );
        }
    }


}
