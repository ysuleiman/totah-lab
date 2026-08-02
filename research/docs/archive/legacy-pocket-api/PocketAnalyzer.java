package totah.lab.pocket;

import totah.lab.protein.Structure;
import java.util.*;
import java.util.stream.Collectors;

public final class PocketAnalyzer {

    private static final Set<String> HYDROGEN_NAMES = Set.of(
            "H", "H1", "H2", "H3", "HA", "HA2", "HA3",
            "HB", "HB1", "HB2", "HB3",
            "HG", "HG1", "HG2", "HG3",
            "HD", "HD1", "HD2", "HD3",
            "HE", "HE1", "HE2", "HE3",
            "HZ", "HZ1", "HZ2", "HZ3"
    );

    private PocketAnalyzer() {
    }

    /**
     * Pocket center computed from alpha spheres.
     */
    public static double[] center(Pocket pocket) {
        Objects.requireNonNull(pocket, "pocket");

        return PocketGeometryUtil.calculateCenter(
                pocket.getAlphaSpheres());
    }

    /**
     * Centroid of all atoms in a residue.
     */
    public static double[] residueCentroid(Residue residue) {
        Objects.requireNonNull(residue, "residue");

        return centroid(residue.getAtoms());
    }

    /**
     * Centroid calculated from heavy atoms only.
     */
    public static double[] heavyAtomCentroid(Residue residue) {
        Objects.requireNonNull(residue, "residue");

        List<Atom> atoms = residue.getAtoms()
                .stream()
                .filter(PocketAnalyzer::isHeavyAtom)
                .toList();

        return centroid(atoms);
    }

    private static double[] centroid(Collection<Atom> atoms) {
        if (atoms == null || atoms.isEmpty()) {
            throw new IllegalArgumentException(
                    "Cannot calculate centroid from an empty atom collection");
        }

        double x = 0.0;
        double y = 0.0;
        double z = 0.0;

        for (Atom atom : atoms) {
            x += atom.getX();
            y += atom.getY();
            z += atom.getZ();
        }

        double count = atoms.size();

        return new double[]{
                x / count,
                y / count,
                z / count
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
                heavyAtomCentroid(residue));
    }

    /**
     * Residues ordered from nearest to farthest from the pocket center.
     */
    public static List<Residue> getResiduesSortedByDistanceToCenter(
            Pocket pocket) {

        return pocket.getResidues()
                .stream()
                .sorted(Comparator.comparingDouble(
                        residue -> distanceToCenter(pocket, residue)))
                .toList();
    }

    /**
     * Map every pocket residue to its centroid distance from the pocket center.
     */
    public static Map<Residue, Double> residueDistances(
            Pocket pocket) {

        return pocket.getResidues()
                .stream()
                .collect(Collectors.toMap(
                        residue -> residue,
                        residue -> distanceToCenter(pocket, residue),
                        (first, second) -> first,
                        LinkedHashMap::new));
    }

    public static Optional<Residue> closestResidue(
            Pocket pocket) {

        return pocket.getResidues()
                .stream()
                .min(Comparator.comparingDouble(
                        residue -> distanceToCenter(pocket, residue)));
    }

    public static Optional<Residue> farthestResidue(
            Pocket pocket) {

        return pocket.getResidues()
                .stream()
                .max(Comparator.comparingDouble(
                        residue -> distanceToCenter(pocket, residue)));
    }

    /**
     * Minimum distance between any heavy atom in residue A and
     * any heavy atom in residue B.
     *
     * This is the measurement to use for residue-neighbor counts.
     */
    public static double minimumHeavyAtomDistance(
            Residue first,
            Residue second) {

        Objects.requireNonNull(first, "first");
        Objects.requireNonNull(second, "second");

        List<Atom> firstAtoms = first.getAtoms()
                .stream()
                .filter(PocketAnalyzer::isHeavyAtom)
                .toList();

        List<Atom> secondAtoms = second.getAtoms()
                .stream()
                .filter(PocketAnalyzer::isHeavyAtom)
                .toList();

        if (firstAtoms.isEmpty() || secondAtoms.isEmpty()) {
            return Double.POSITIVE_INFINITY;
        }

        double minimum = Double.POSITIVE_INFINITY;

        for (Atom firstAtom : firstAtoms) {
            for (Atom secondAtom : secondAtoms) {
                double current = distance(firstAtom, secondAtom);

                if (current < minimum) {
                    minimum = current;
                }
            }
        }

        return minimum;
    }

    /**
     * Finds all structural residues within a heavy-atom cutoff of
     * the selected residue.
     *
     * Passing Structure matters because the neighboring residues may
     * extend beyond the residues explicitly assigned to the pocket.
     */
    public static List<ResidueNeighbor> neighbors(
            Structure structure,
            Residue target,
            double cutoff) {

        validateCutoff(cutoff);
        Objects.requireNonNull(structure, "structure");
        Objects.requireNonNull(target, "target");

        return structure.getResidues()
                .stream()
                .filter(candidate -> !sameResidue(target, candidate))
                .map(candidate -> new ResidueNeighbor(
                        candidate,
                        minimumHeavyAtomDistance(target, candidate)))
                .filter(neighbor -> neighbor.distance() <= cutoff)
                .sorted(Comparator.comparingDouble(
                        ResidueNeighbor::distance))
                .toList();
    }

    /**
     * Counts the structural neighbors of each pocket residue.
     *
     * Each residue is counted at most once, regardless of how many
     * atom pairs are within the cutoff.
     */
    public static Map<Residue, Integer> neighborCounts(
            Pocket pocket,
            Structure structure,
            double cutoff) {

        Objects.requireNonNull(pocket, "pocket");
        Objects.requireNonNull(structure, "structure");
        validateCutoff(cutoff);

        Map<Residue, Integer> counts = new LinkedHashMap<>();

        for (Residue pocketResidue : pocket.getResidues()) {
            Residue structuralResidue =
                    resolveStructureResidue(structure, pocketResidue)
                            .orElse(pocketResidue);

            int count = neighbors(
                    structure,
                    structuralResidue,
                    cutoff).size();

            counts.put(pocketResidue, count);
        }

        return counts.entrySet()
                .stream()
                .sorted(
                        Map.Entry.<Residue, Integer>comparingByValue()
                                .reversed())
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (first, second) -> first,
                        LinkedHashMap::new));
    }

    /**
     * Neighbor counts for multiple cutoffs.
     *
     * Useful for determining whether CYS202 remains a high-degree
     * residue at 3.5, 4.0, 4.5 and 5.0 Å.
     */
    public static Map<Residue, Map<Double, Integer>>
    neighborCountsByCutoff(
            Pocket pocket,
            Structure structure,
            Collection<Double> cutoffs) {

        Objects.requireNonNull(cutoffs, "cutoffs");

        List<Double> sortedCutoffs = cutoffs.stream()
                .distinct()
                .sorted()
                .toList();

        Map<Residue, Map<Double, Integer>> result =
                new LinkedHashMap<>();

        for (Residue pocketResidue : pocket.getResidues()) {
            Residue structuralResidue =
                    resolveStructureResidue(structure, pocketResidue)
                            .orElse(pocketResidue);

            Map<Double, Integer> counts = new LinkedHashMap<>();

            for (double cutoff : sortedCutoffs) {
                validateCutoff(cutoff);

                counts.put(
                        cutoff,
                        neighbors(
                                structure,
                                structuralResidue,
                                cutoff).size());
            }

            result.put(pocketResidue, counts);
        }

        return result;
    }

    /**
     * Returns the most highly connected residues in the pocket.
     */
    public static List<ResidueConnectivity> connectivityRanking(
            Pocket pocket,
            Structure structure,
            double cutoff) {

        return neighborCounts(pocket, structure, cutoff)
                .entrySet()
                .stream()
                .map(entry -> new ResidueConnectivity(
                        entry.getKey(),
                        entry.getValue(),
                        distanceToCenter(pocket, entry.getKey())))
                .sorted(
                        Comparator.comparingInt(
                                        ResidueConnectivity::neighborCount)
                                .reversed()
                                .thenComparingDouble(
                                        ResidueConnectivity::distanceToCenter))
                .toList();
    }

    /**
     * Angle between two residue vectors measured from the pocket center.
     *
     * 0 degrees: same direction
     * 90 degrees: perpendicular directions
     * 180 degrees: opposite directions
     */
    public static double angleFromPocketCenter(
            Pocket pocket,
            Residue first,
            Residue second) {

        double[] pocketCenter = center(pocket);

        double[] firstPosition =
                representativePosition(first);

        double[] secondPosition =
                representativePosition(second);

        double[] firstVector =
                subtract(firstPosition, pocketCenter);

        double[] secondVector =
                subtract(secondPosition, pocketCenter);

        return angleDegrees(firstVector, secondVector);
    }

    /**
     * Creates a geometric comparison of two residues.
     */
    public static ResiduePairGeometry compareResidues(
            Pocket pocket,
            Residue first,
            Residue second) {

        double[] pocketCenter = center(pocket);
        double[] firstPosition =
                representativePosition(first);
        double[] secondPosition =
                representativePosition(second);

        return new ResiduePairGeometry(
                first,
                second,
                distance(firstPosition, pocketCenter),
                distance(secondPosition, pocketCenter),
                distance(firstPosition, secondPosition),
                angleDegrees(
                        subtract(firstPosition, pocketCenter),
                        subtract(secondPosition, pocketCenter)));
    }

    /**
     * Uses SG for cysteine when available; otherwise uses the
     * heavy-atom centroid.
     */
    public static double[] representativePosition(
            Residue residue) {

        if ("CYS".equalsIgnoreCase(residue.getName())) {
            Optional<Atom> sulfur = residue.getAtoms()
                    .stream()
                    .filter(atom ->
                            "SG".equalsIgnoreCase(atom.getName()))
                    .findFirst();

            if (sulfur.isPresent()) {
                Atom atom = sulfur.get();

                return new double[]{
                        atom.getX(),
                        atom.getY(),
                        atom.getZ()
                };
            }
        }

        return heavyAtomCentroid(residue);
    }

    /**
     * Finds a residue in the complete structure corresponding to a
     * residue assigned by the pocket detector.
     */
    public static Optional<Residue> resolveStructureResidue(
            Structure structure,
            Residue pocketResidue) {

        return structure.getResidues()
                .stream()
                .filter(candidate ->
                        sameResidue(candidate, pocketResidue))
                .findFirst();
    }

    /**
     * Finds a residue by chain and residue number.
     */
    public static Optional<Residue> findResidue(
            Structure structure,
            String chainId,
            int residueNumber) {

        return structure.getResidues()
                .stream()
                .filter(residue ->
                        Objects.equals(
                                normalizeChain(residue.getChainId()),
                                normalizeChain(chainId)))
                .filter(residue ->
                        residue.getNumber() == residueNumber)
                .findFirst();
    }

    /**
     * Finds cysteines among the pocket residues.
     */
    public static List<Residue> pocketCysteines(
            Pocket pocket) {

        return pocket.getResidues()
                .stream()
                .filter(residue ->
                        "CYS".equalsIgnoreCase(residue.getName()))
                .toList();
    }

    /**
     * Finds structure cysteines close to the pocket.
     *
     * A structure residue is considered near the pocket when any
     * heavy atom is within the cutoff of an alpha-sphere center.
     */
    public static List<Residue> cysteinesNearPocket(
            Pocket pocket,
            Structure structure,
            double cutoff) {

        validateCutoff(cutoff);

        return structure.getResidues()
                .stream()
                .filter(residue ->
                        "CYS".equalsIgnoreCase(residue.getName()))
                .filter(residue ->
                        minimumDistanceToPocket(
                                pocket,
                                residue) <= cutoff)
                .sorted(Comparator.comparingDouble(
                        residue -> minimumDistanceToPocket(
                                pocket,
                                residue)))
                .toList();
    }

    /**
     * Minimum heavy-atom distance from a residue to any alpha-sphere center.
     */
    public static double minimumDistanceToPocket(
            Pocket pocket,
            Residue residue) {

        double minimum = Double.POSITIVE_INFINITY;

        for (Atom atom : residue.getAtoms()) {
            if (!isHeavyAtom(atom)) {
                continue;
            }

            for (AlphaSphere sphere : pocket.getAlphaSpheres()) {
                double[] sphereCenter = {
                        sphere.getX(),
                        sphere.getY(),
                        sphere.getZ()
                };

                double current = distance(
                        new double[]{
                                atom.getX(),
                                atom.getY(),
                                atom.getZ()
                        },
                        sphereCenter);

                if (current < minimum) {
                    minimum = current;
                }
            }
        }

        return minimum;
    }

    public static PocketProfile profile(
            Pocket pocket) {

        double[] pocketCenter = center(pocket);

        Map<Residue, Double> distances =
                residueDistances(pocket);

        double meanDistance = distances.values()
                .stream()
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(0.0);

        /*
         * These are residues whose centroids are near the pocket center.
         * This is not a residue-neighbor measurement.
         */
        List<Residue> centerProximalResidues =
                distances.entrySet()
                        .stream()
                        .filter(entry ->
                                entry.getValue() <= 4.0)
                        .map(Map.Entry::getKey)
                        .toList();

        return PocketProfile.builder()
                .center(pocketCenter)
                .volume(pocket.getVolume())
                .alphaSphereCount(
                        pocket.getAlphaSpheres().size())
                .residueCount(
                        pocket.getResidues().size())
                .meanResidueDistance(meanDistance)
                .closestResidue(
                        closestResidue(pocket).orElse(null))
                .farthestResidue(
                        farthestResidue(pocket).orElse(null))
                .coreResidues(centerProximalResidues)
                .residueDistances(distances)
                .build();
    }

    private static boolean sameResidue(
            Residue first,
            Residue second) {

        return first.getNumber() == second.getNumber()
                && Objects.equals(
                normalizeChain(first.getChainId()),
                normalizeChain(second.getChainId()));
    }

    private static String normalizeChain(String chainId) {
        return chainId == null
                ? ""
                : chainId.trim().toUpperCase(Locale.ROOT);
    }

    private static boolean isHeavyAtom(Atom atom) {
        String atomName = atom.getName();

        if (atomName == null || atomName.isBlank()) {
            return true;
        }

        String normalized =
                atomName.trim().toUpperCase(Locale.ROOT);

        /*
         * Prefer atom.getElement() here if your Atom class has an
         * element property. Name-based detection is only a fallback.
         */
        return !normalized.startsWith("H")
                && !HYDROGEN_NAMES.contains(normalized);
    }

    private static double distance(
            Atom first,
            Atom second) {

        double dx = first.getX() - second.getX();
        double dy = first.getY() - second.getY();
        double dz = first.getZ() - second.getZ();

        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private static double distance(
            double[] first,
            double[] second) {

        double dx = first[0] - second[0];
        double dy = first[1] - second[1];
        double dz = first[2] - second[2];

        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private static double[] subtract(
            double[] first,
            double[] second) {

        return new double[]{
                first[0] - second[0],
                first[1] - second[1],
                first[2] - second[2]
        };
    }

    private static double angleDegrees(
            double[] first,
            double[] second) {

        double firstMagnitude = magnitude(first);
        double secondMagnitude = magnitude(second);

        if (firstMagnitude == 0.0 || secondMagnitude == 0.0) {
            throw new IllegalArgumentException(
                    "Cannot calculate an angle from a zero-length vector");
        }

        double cosine = dot(first, second)
                / (firstMagnitude * secondMagnitude);

        /*
         * Protect against floating-point values such as 1.0000000001.
         */
        cosine = Math.max(-1.0, Math.min(1.0, cosine));

        return Math.toDegrees(Math.acos(cosine));
    }

    private static double dot(
            double[] first,
            double[] second) {

        return first[0] * second[0]
                + first[1] * second[1]
                + first[2] * second[2];
    }

    private static double magnitude(double[] vector) {
        return Math.sqrt(dot(vector, vector));
    }

    private static void validateCutoff(double cutoff) {
        if (!Double.isFinite(cutoff) || cutoff <= 0.0) {
            throw new IllegalArgumentException(
                    "Cutoff must be a positive finite number: "
                            + cutoff);
        }
    }

    public record ResidueNeighbor(
            Residue residue,
            double distance) {
    }

    public record ResidueConnectivity(
            Residue residue,
            int neighborCount,
            double distanceToCenter) {
    }

    public record ResiduePairGeometry(
            Residue first,
            Residue second,
            double firstDistanceToCenter,
            double secondDistanceToCenter,
            double distanceBetweenResidues,
            double angleFromPocketCenterDegrees) {
    }
}