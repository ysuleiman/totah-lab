package totah.lab.athena.sasa;

import totah.lab.gaia.chemistry.Element;
import totah.lab.gaia.geometry.Point3D;
import totah.lab.gaia.structure.Atom;
import totah.lab.gaia.structure.AtomReference;
import totah.lab.gaia.structure.Chain;
import totah.lab.gaia.structure.Residue;
import totah.lab.gaia.structure.Structure;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;

/**
 * Solvent-accessible surface area (SASA) calculated using the
 * Shrake-Rupley algorithm.
 *
 * <p>Each atom surface is sampled using a fixed, deterministic set of
 * approximately uniform points distributed over a unit sphere using a
 * Fibonacci lattice. The unit-sphere points are projected onto the atom's
 * solvent-expanded sphere, whose radius is:</p>
 *
 * <pre>
 * van der Waals radius + probe radius
 * </pre>
 *
 * <p>A projected point is solvent-accessible when it is not inside the
 * solvent-expanded sphere of another atom. The accessible area of an atom is
 * calculated as:</p>
 *
 * <pre>
 * 4 * pi * radius^2 * accessiblePointCount / spherePointCount
 * </pre>
 *
 * <p>Van der Waals radii are obtained from
 * {@link Element#getVanDerWaalsRadiusOrDefault(double)}. Atoms whose element
 * is unavailable are skipped. Elements without a tabulated radius use
 * {@link #DEFAULT_UNKNOWN_RADIUS}.</p>
 *
 * <p>The implementation preserves structure traversal order and produces
 * deterministic results for a given structure, probe radius, and sphere-point
 * count.</p>
 */
public final class ShrakeRupleySasa {

    /** Default probe radius in angstroms, approximating a water molecule. */
    public static final double DEFAULT_PROBE_RADIUS = 1.4;

    /** Fallback van der Waals radius for an element without a known radius. */
    public static final double DEFAULT_UNKNOWN_RADIUS = 1.70;

    /** Default number of Fibonacci sample points per atom sphere. */
    public static final int DEFAULT_SPHERE_POINT_COUNT = 96;

    private static final double FOUR_PI = 4.0 * Math.PI;

    private static final double GOLDEN_ANGLE =
            Math.PI * (3.0 - Math.sqrt(5.0));

    private ShrakeRupleySasa() {
    }

    /**
     * Per-atom solvent-accessible surface areas.
     *
     * @param areaByAtom per-atom areas in square angstroms
     * @param probeRadius probe radius in angstroms
     * @param spherePointCount number of sphere sample points per atom
     */
    public record SasaResult(
            Map<AtomReference, Double> areaByAtom,
            double probeRadius,
            int spherePointCount) {

        public SasaResult {
            Objects.requireNonNull(areaByAtom, "areaByAtom");

            if (!Double.isFinite(probeRadius) || probeRadius < 0.0) {
                throw new IllegalArgumentException(
                        "probeRadius must be finite and non-negative");
            }

            if (spherePointCount < 1) {
                throw new IllegalArgumentException(
                        "spherePointCount must be at least 1");
            }

            LinkedHashMap<AtomReference, Double> copy =
                    new LinkedHashMap<>(areaByAtom.size());

            for (Map.Entry<AtomReference, Double> entry
                    : areaByAtom.entrySet()) {

                AtomReference reference = Objects.requireNonNull(
                        entry.getKey(),
                        "areaByAtom must not contain null references");

                Double area = Objects.requireNonNull(
                        entry.getValue(),
                        "areaByAtom must not contain null areas");

                if (!Double.isFinite(area) || area < 0.0) {
                    throw new IllegalArgumentException(
                            "SASA area must be finite and non-negative for "
                                    + reference
                                    + ": "
                                    + area);
                }

                copy.put(reference, area);
            }

            areaByAtom = Collections.unmodifiableMap(copy);
        }

        /**
         * Returns the sum of all per-atom areas in square angstroms.
         */
        public double totalArea() {
            double total = 0.0;

            for (double area : areaByAtom.values()) {
                total += area;
            }

            return total;
        }

        /**
         * Returns the solvent-accessible area of one atom.
         *
         * @param reference atom reference
         * @return atom SASA in square angstroms
         * @throws NoSuchElementException if the atom is not present in this
         *                                result
         */
        public double areaOf(AtomReference reference) {
            Objects.requireNonNull(reference, "reference");

            Double area = areaByAtom.get(reference);

            if (area == null) {
                throw new NoSuchElementException(
                        "Atom not present in SASA result: " + reference);
            }

            return area;
        }
    }

    /**
     * Calculates per-atom SASA using the default probe radius and sphere-point
     * count.
     *
     * @param structure structure to measure
     * @return immutable per-atom SASA result
     */
    public static SasaResult calculate(Structure structure) {
        return calculate(
                structure,
                DEFAULT_PROBE_RADIUS,
                DEFAULT_SPHERE_POINT_COUNT);
    }

    /**
     * Calculates per-atom SASA using the specified probe radius and default
     * sphere-point count.
     *
     * @param structure structure to measure
     * @param probeRadius probe radius in angstroms
     * @return immutable per-atom SASA result
     */
    public static SasaResult calculate(
            Structure structure,
            double probeRadius) {

        return calculate(
                structure,
                probeRadius,
                DEFAULT_SPHERE_POINT_COUNT);
    }

    /**
     * Calculates per-atom SASA.
     *
     * @param structure structure to measure
     * @param probeRadius probe radius in angstroms; must be finite and
     *                    non-negative
     * @param spherePointCount sample points per atom sphere; must be at least
     *                         one
     * @return immutable per-atom areas in structure traversal order
     */
    public static SasaResult calculate(
            Structure structure,
            double probeRadius,
            int spherePointCount) {

        Objects.requireNonNull(structure, "structure");
        validateProbeRadius(probeRadius);
        validateSpherePointCount(spherePointCount);

        Map<AtomReference, Atom> atoms = collectAtoms(structure);

        if (atoms.isEmpty()) {
            return new SasaResult(
                    Collections.emptyMap(),
                    probeRadius,
                    spherePointCount);
        }

        AtomData atomData = createAtomData(atoms, probeRadius);

        double[][] spherePoints =
                fibonacciSphere(spherePointCount);

        int[][] possibleOccluders =
                buildPossibleOccluders(
                        atomData.centers(),
                        atomData.radii());

        Map<AtomReference, Double> areas =
                new LinkedHashMap<>(atoms.size());

        int atomIndex = 0;

        for (AtomReference reference : atoms.keySet()) {
            int accessible = countAccessible(
                    atomIndex,
                    atomData.centers(),
                    atomData.radii(),
                    possibleOccluders[atomIndex],
                    spherePoints);

            double radius = atomData.radii()[atomIndex];

            double area = FOUR_PI
                    * radius
                    * radius
                    * ((double) accessible / spherePointCount);

            areas.put(reference, area);
            atomIndex++;
        }

        return new SasaResult(
                areas,
                probeRadius,
                spherePointCount);
    }

    /**
     * Returns the total solvent-accessible area using the default probe radius
     * and sphere-point count.
     *
     * @param structure structure to measure
     * @return total SASA in square angstroms
     */
    public static double total(Structure structure) {
        return calculate(structure).totalArea();
    }

    /**
     * Returns the total solvent-accessible area using the specified probe
     * radius and default sphere-point count.
     *
     * @param structure structure to measure
     * @param probeRadius probe radius in angstroms
     * @return total SASA in square angstroms
     */
    public static double total(
            Structure structure,
            double probeRadius) {

        return calculate(structure, probeRadius).totalArea();
    }

    private static Map<AtomReference, Atom> collectAtoms(
            Structure structure) {

        Map<AtomReference, Atom> atoms =
                new LinkedHashMap<>();

        List<Chain> chains = Objects.requireNonNull(
                structure.getChains(),
                "structure chains");

        for (Chain chain : chains) {
            Objects.requireNonNull(
                    chain,
                    "Structure must not contain null chains");

            String chainId = Objects.requireNonNull(
                    chain.id(),
                    "Chain ID");

            List<Residue> residues = Objects.requireNonNull(
                    chain.residues(),
                    "chain residues");

            for (Residue residue : residues) {
                Objects.requireNonNull(
                        residue,
                        "Chain must not contain null residues");

                Character residueInsertionCode =
                        residue.getInsertionCode();

                char insertionCode =
                        residueInsertionCode == null
                                ? ' '
                                : residueInsertionCode;

                List<Atom> residueAtoms = Objects.requireNonNull(
                        residue.getAtoms(),
                        "residue atoms");

                for (Atom atom : residueAtoms) {
                    if (atom == null || atom.getElement() == null) {
                        continue;
                    }

                    String atomName = Objects.requireNonNull(
                            atom.getName(),
                            "Atom name");

                    Point3D position = Objects.requireNonNull(
                            atom.getPosition(),
                            "Atom position");

                    validatePosition(position);

                    AtomReference reference = new AtomReference(
                            chainId,
                            residue.getNumber(),
                            insertionCode,
                            atomName);

                    Atom previous = atoms.putIfAbsent(
                            reference,
                            atom);

                    if (previous != null) {
                        throw new IllegalArgumentException(
                                "Duplicate atom reference while calculating "
                                        + "SASA: "
                                        + reference);
                    }
                }
            }
        }

        return atoms;
    }

    private static AtomData createAtomData(
            Map<AtomReference, Atom> atoms,
            double probeRadius) {

        int atomCount = atoms.size();

        double[][] centers =
                new double[atomCount][3];

        double[] radii =
                new double[atomCount];

        int index = 0;

        for (Map.Entry<AtomReference, Atom> entry
                : atoms.entrySet()) {

            AtomReference reference = entry.getKey();
            Atom atom = entry.getValue();

            Point3D position = atom.getPosition();

            centers[index][0] = position.x();
            centers[index][1] = position.y();
            centers[index][2] = position.z();

            double vanDerWaalsRadius =
                    atom.getElement()
                            .getVanDerWaalsRadiusOrDefault(
                                    DEFAULT_UNKNOWN_RADIUS);

            if (!Double.isFinite(vanDerWaalsRadius)
                    || vanDerWaalsRadius <= 0.0) {
                throw new IllegalArgumentException(
                        "Invalid van der Waals radius for "
                                + reference
                                + ": "
                                + vanDerWaalsRadius);
            }

            double expandedRadius =
                    vanDerWaalsRadius + probeRadius;

            if (!Double.isFinite(expandedRadius)
                    || expandedRadius <= 0.0) {
                throw new IllegalArgumentException(
                        "Invalid solvent-expanded radius for "
                                + reference
                                + ": "
                                + expandedRadius);
            }

            radii[index] = expandedRadius;
            index++;
        }

        return new AtomData(centers, radii);
    }

    /**
     * Computes, for every atom, the atoms that could possibly occlude one of
     * its sampled surface points.
     *
     * <p>If the distance between two centers is greater than or equal to the
     * sum of their solvent-expanded radii, their spheres do not overlap and
     * neither can cover surface points on the other.</p>
     */
    private static int[][] buildPossibleOccluders(
            double[][] centers,
            double[] radii) {

        int atomCount = centers.length;

        @SuppressWarnings("unchecked")
        List<Integer>[] neighbors =
                new List[atomCount];

        for (int index = 0; index < atomCount; index++) {
            neighbors[index] = new ArrayList<>();
        }

        for (int first = 0; first < atomCount; first++) {
            for (int second = first + 1;
                 second < atomCount;
                 second++) {

                double dx =
                        centers[first][0] - centers[second][0];

                double dy =
                        centers[first][1] - centers[second][1];

                double dz =
                        centers[first][2] - centers[second][2];

                double distanceSquared =
                        dx * dx + dy * dy + dz * dz;

                double maximumOccludingDistance =
                        radii[first] + radii[second];

                if (distanceSquared
                        < maximumOccludingDistance
                        * maximumOccludingDistance) {

                    neighbors[first].add(second);
                    neighbors[second].add(first);
                }
            }
        }

        int[][] possibleOccluders =
                new int[atomCount][];

        for (int index = 0; index < atomCount; index++) {
            List<Integer> atomNeighbors =
                    neighbors[index];

            int[] indices =
                    new int[atomNeighbors.size()];

            for (int neighborIndex = 0;
                 neighborIndex < atomNeighbors.size();
                 neighborIndex++) {

                indices[neighborIndex] =
                        atomNeighbors.get(neighborIndex);
            }

            possibleOccluders[index] = indices;
        }

        return possibleOccluders;
    }

    private static int countAccessible(
            int atomIndex,
            double[][] centers,
            double[] radii,
            int[] possibleOccluders,
            double[][] spherePoints) {

        double radius = radii[atomIndex];

        double centerX = centers[atomIndex][0];
        double centerY = centers[atomIndex][1];
        double centerZ = centers[atomIndex][2];

        int accessible = 0;

        for (double[] direction : spherePoints) {
            double pointX =
                    centerX + radius * direction[0];

            double pointY =
                    centerY + radius * direction[1];

            double pointZ =
                    centerZ + radius * direction[2];

            boolean covered = false;

            for (int other : possibleOccluders) {
                double dx =
                        pointX - centers[other][0];

                double dy =
                        pointY - centers[other][1];

                double dz =
                        pointZ - centers[other][2];

                double distanceSquared =
                        dx * dx + dy * dy + dz * dz;

                double otherRadius =
                        radii[other];

                if (distanceSquared
                        < otherRadius * otherRadius) {

                    covered = true;
                    break;
                }
            }

            if (!covered) {
                accessible++;
            }
        }

        return accessible;
    }

    /**
     * Generates an approximately uniform deterministic point distribution on
     * a unit sphere using a Fibonacci lattice.
     */
    private static double[][] fibonacciSphere(
            int count) {

        double[][] points =
                new double[count][3];

        for (int index = 0; index < count; index++) {
            double z =
                    1.0 - (2.0 * index + 1.0) / count;

            double radial =
                    Math.sqrt(Math.max(
                            0.0,
                            1.0 - z * z));

            double phi =
                    index * GOLDEN_ANGLE;

            points[index][0] =
                    radial * Math.cos(phi);

            points[index][1] =
                    radial * Math.sin(phi);

            points[index][2] =
                    z;
        }

        return points;
    }

    private static void validateProbeRadius(
            double probeRadius) {

        if (!Double.isFinite(probeRadius)
                || probeRadius < 0.0) {
            throw new IllegalArgumentException(
                    "probeRadius must be finite and non-negative");
        }
    }

    private static void validateSpherePointCount(
            int spherePointCount) {

        if (spherePointCount < 1) {
            throw new IllegalArgumentException(
                    "spherePointCount must be at least 1");
        }
    }

    private static void validatePosition(
            Point3D position) {

        if (!Double.isFinite(position.x())
                || !Double.isFinite(position.y())
                || !Double.isFinite(position.z())) {
            throw new IllegalArgumentException(
                    "Atom position must contain finite coordinates: "
                            + position);
        }
    }

    private record AtomData(
            double[][] centers,
            double[] radii) {

        private AtomData {
            Objects.requireNonNull(centers, "centers");
            Objects.requireNonNull(radii, "radii");

            if (centers.length != radii.length) {
                throw new IllegalArgumentException(
                        "Atom center and radius arrays must have equal length");
            }
        }
    }
}
