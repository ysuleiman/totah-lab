package totah.lab.athena.pocket.architecture;

import totah.lab.gaia.geometry.Point3D;
import totah.lab.gaia.geometry.RigidTransform;
import totah.lab.gaia.molecule.Ligand;
import totah.lab.gaia.pocket.AlphaSphere;
import totah.lab.gaia.pocket.AlphaSphereSet;
import totah.lab.gaia.pocket.Pocket;
import totah.lab.gaia.structure.Atom;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Builds a {@link SubpocketOccupancyComparison}. The common frame is
 * the REFERENCE pocket's frame (typically the superpocket); the
 * caller supplies the transform that moves pose A (and pocket A)
 * into it. Occupancy against the reference cloud is measured for
 * both poses in that frame; pose A's occupancy of its OWN subsite
 * cloud is measured in its own frame (transform-invariant).
 */
public final class SubpocketOccupancyAnalyzer {

    private final SubpocketOccupancyOptions options;

    public SubpocketOccupancyAnalyzer() {
        this(SubpocketOccupancyOptions.defaults());
    }

    public SubpocketOccupancyAnalyzer(
            SubpocketOccupancyOptions options
    ) {
        this.options = Objects.requireNonNull(options, "options");
    }

    /**
     * @param poseA pose in the subsite (pocket A)
     * @param poseB pose in the reference pocket (already in the
     *        reference frame)
     * @param pocketA the subsite pocket (own frame)
     * @param referencePocket the superpocket whose cloud is the
     *        common reference
     * @param transformAtoReference moves pose A into the reference
     *        frame
     */
    public SubpocketOccupancyComparison compare(
            Ligand poseA,
            Ligand poseB,
            Pocket pocketA,
            Pocket referencePocket,
            RigidTransform transformAtoReference
    ) {
        Objects.requireNonNull(poseA, "poseA");
        Objects.requireNonNull(poseB, "poseB");
        Objects.requireNonNull(pocketA, "pocketA");
        Objects.requireNonNull(referencePocket, "referencePocket");
        Objects.requireNonNull(
                transformAtoReference,
                "transformAtoReference"
        );

        List<Atom> atomsA = heavyAtoms(poseA);
        List<Atom> atomsB = heavyAtoms(poseB);

        if (atomsA.isEmpty() || atomsB.isEmpty()) {
            throw new IllegalArgumentException(
                    "Both poses must contain heavy atoms"
            );
        }

        List<AlphaSphere> referenceSpheres =
                spheres(referencePocket);

        List<Point3D> positionsA = transformAtoReference.apply(
                positions(atomsA));
        List<Point3D> positionsB = positions(atomsB);

        Set<Long> occupiedA =
                occupied(referenceSpheres, positionsA);
        Set<Long> occupiedB =
                occupied(referenceSpheres, positionsB);

        Set<Long> both = new HashSet<>(occupiedA);
        both.retainAll(occupiedB);

        Set<Long> onlyA = new HashSet<>(occupiedA);
        onlyA.removeAll(occupiedB);

        Set<Long> onlyB = new HashSet<>(occupiedB);
        onlyB.removeAll(occupiedA);

        PocketArchitecture referenceArchitecture =
                PocketArchitecture.of(referencePocket);

        Point3D centroidA = centroid(positionsA);
        Point3D centroidB = centroid(positionsB);

        return new SubpocketOccupancyComparison(
                occupiedA,
                occupiedB,
                both,
                onlyA,
                onlyB,
                occupied(spheres(pocketA), positions(atomsA)),
                cloudOf(referenceSpheres, onlyA),
                cloudOf(referenceSpheres, onlyB),
                atomContexts(atomsA, positionsA, referenceSpheres),
                atomContexts(atomsB, positionsB, referenceSpheres),
                depth(referenceArchitecture, centroidA),
                depth(referenceArchitecture, centroidB),
                referenceArchitecture.principalComponents()
                        .projection(centroidA, 1),
                referenceArchitecture.principalComponents()
                        .projection(centroidA, 2),
                referenceArchitecture.principalComponents()
                        .projection(centroidB, 1),
                referenceArchitecture.principalComponents()
                        .projection(centroidB, 2),
                referenceSpheres.size()
        );
    }

    private double depth(
            PocketArchitecture architecture,
            Point3D poseCentroid
    ) {
        return architecture.mouthPlaneProjection()
                - architecture.principalComponents()
                        .projection(poseCentroid, 0);
    }

    private Set<Long> occupied(
            List<AlphaSphere> spheres,
            List<Point3D> positions
    ) {
        Set<Long> occupied = new HashSet<>();

        for (AlphaSphere sphere : spheres) {
            double limit = options.sphereOccupancyRadiusFraction()
                    * sphere.radius()
                    + options.sphereOccupancyToleranceAngstroms();

            for (Point3D position : positions) {
                if (position.distance(sphere.center()) <= limit) {
                    occupied.add(sphere.id());
                    break;
                }
            }
        }

        return occupied;
    }

    private static List<Point3D> cloudOf(
            List<AlphaSphere> spheres,
            Set<Long> ids
    ) {
        return spheres.stream()
                .filter(sphere -> ids.contains(sphere.id()))
                .map(AlphaSphere::center)
                .toList();
    }

    private List<SubpocketOccupancyComparison.AtomSphereContext>
            atomContexts(
            List<Atom> atoms,
            List<Point3D> positions,
            List<AlphaSphere> referenceSpheres
    ) {
        List<SubpocketOccupancyComparison.AtomSphereContext> contexts =
                new ArrayList<>();

        for (int index = 0; index < atoms.size(); index++) {
            Point3D position = positions.get(index);

            long nearestId = -1;
            double nearestCenter = Double.MAX_VALUE;
            double nearestSurface = Double.MAX_VALUE;
            int localCount = 0;

            for (AlphaSphere sphere : referenceSpheres) {
                double centerDistance =
                        position.distance(sphere.center());

                if (centerDistance < nearestCenter) {
                    nearestCenter = centerDistance;
                    nearestId = sphere.id();
                    nearestSurface = Math.max(
                            0.0,
                            centerDistance - sphere.radius()
                    );
                }

                if (centerDistance
                        <= options.localDensityRadiusAngstroms()) {
                    localCount++;
                }
            }

            contexts.add(
                    new SubpocketOccupancyComparison.AtomSphereContext(
                            atoms.get(index).getName(),
                            nearestId,
                            nearestCenter,
                            nearestSurface,
                            localCount
                    ));
        }

        return contexts;
    }

    private static Point3D centroid(List<Point3D> points) {
        double x = 0.0;
        double y = 0.0;
        double z = 0.0;

        for (Point3D point : points) {
            x += point.x();
            y += point.y();
            z += point.z();
        }

        double n = points.size();

        return new Point3D(x / n, y / n, z / n);
    }

    private static List<Atom> heavyAtoms(Ligand pose) {
        return pose.structure().getChains().stream()
                .flatMap(chain -> chain.residues().stream())
                .flatMap(residue -> residue.getAtoms().stream())
                .filter(Atom::isHeavyAtom)
                .toList();
    }

    private static List<Point3D> positions(List<Atom> atoms) {
        return atoms.stream().map(Atom::getPosition).toList();
    }

    private static List<AlphaSphere> spheres(Pocket pocket) {
        List<AlphaSphere> spheres = pocket.alphaSphereSet()
                .map(AlphaSphereSet::spheres)
                .orElse(List.of());

        if (spheres.isEmpty()) {
            throw new IllegalArgumentException(
                    "Pocket has no alpha spheres: " + pocket.id()
            );
        }

        return spheres;
    }
}
