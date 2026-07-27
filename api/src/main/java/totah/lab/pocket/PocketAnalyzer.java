package totah.lab.pocket;


import java.util.*;
import java.util.stream.Collectors;

public final class PocketAnalyzer {

    private PocketAnalyzer() {
    }

    /**
     * Pocket center computed from alpha spheres.
     */
    public static double[] center(Pocket pocket) {
        return PocketGeometryUtil.calculateCenter(
                pocket.getAlphaSpheres());
    }

    /**
     * Centroid of a residue.
     */
    public static double[] residueCentroid(Residue residue) {

        List<Atom> atoms = residue.getAtoms();

        if (atoms.isEmpty()) {
            return new double[]{0,0,0};
        }

        double x = 0;
        double y = 0;
        double z = 0;

        for (Atom atom : atoms) {
            x += atom.getX();
            y += atom.getY();
            z += atom.getZ();
        }

        return new double[]{
                x / atoms.size(),
                y / atoms.size(),
                z / atoms.size()
        };
    }

    /**
     * Distance from residue centroid to pocket center.
     */
    public static double distanceToCenter(
            Pocket pocket,
            Residue residue) {

        return distance(
                center(pocket),
                residueCentroid(residue));
    }

    /**
     * Residues ordered from nearest to farthest.
     */
    public static List<Residue> getResiduesSortedByDistanceToCenter(
            Pocket pocket) {

        return pocket.getResidues().stream()
                .sorted(Comparator.comparingDouble(
                        r -> distanceToCenter(pocket, r)))
                .toList();
    }

    /**
     * Map every residue to its distance.
     */
    public static Map<Residue, Double> residueDistances(
            Pocket pocket) {

        return pocket.getResidues().stream()
                .collect(Collectors.toMap(
                        r -> r,
                        r -> distanceToCenter(pocket, r),
                        (a,b)->a,
                        LinkedHashMap::new));
    }

    /**
     * Closest residue.
     */
    public static Residue closestResidue(
            Pocket pocket) {

        return pocket.getResidues().stream()
                .min(Comparator.comparingDouble(
                        r -> distanceToCenter(pocket, r)))
                .orElse(null);
    }

    /**
     * Farthest residue.
     */
    public static Residue farthestResidue(
            Pocket pocket) {

        return pocket.getResidues().stream()
                .max(Comparator.comparingDouble(
                        r -> distanceToCenter(pocket, r)))
                .orElse(null);
    }

    /**
     * Euclidean distance.
     */
    private static double distance(
            double[] a,
            double[] b) {

        double dx = a[0]-b[0];
        double dy = a[1]-b[1];
        double dz = a[2]-b[2];

        return Math.sqrt(
                dx*dx +
                        dy*dy +
                        dz*dz);
    }

    public static PocketProfile profile(Pocket pocket) {
        double[] center =
                PocketGeometryUtil.calculateCenter(
                        pocket.getAlphaSpheres());
        Map<Residue, Double> distances =
                residueDistances(pocket);
        double meanDistance =
                distances.values()
                        .stream()
                        .mapToDouble(Double::doubleValue)
                        .average()
                        .orElse(0);
        List<Residue> core =
                distances.entrySet()
                        .stream()
                        .filter(e -> e.getValue() <= 4.0)
                        .map(Map.Entry::getKey)
                        .toList();

        return PocketProfile.builder()
                .center(center)
                .volume(pocket.getVolume())
                .alphaSphereCount(
                        pocket.getAlphaSpheres().size())
                .residueCount(
                        pocket.getResidues().size())
                .meanResidueDistance(meanDistance)
                .closestResidue(
                        closestResidue(pocket))
                .farthestResidue(
                        farthestResidue(pocket))
                .coreResidues(core)
                .residueDistances(distances)
                .build();
    }
}