package totah.lab.athena.pocket.architecture;

import totah.lab.gaia.geometry.Point3D;
import totah.lab.gaia.geometry.RigidTransform;
import totah.lab.gaia.molecule.Ligand;
import totah.lab.gaia.pocket.AlphaSphere;
import totah.lab.gaia.pocket.AlphaSphereSet;
import totah.lab.gaia.pocket.Pocket;
import totah.lab.gaia.structure.Atom;
import totah.lab.gaia.structure.Structure;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Builds a {@link LigandSpaceComparison} from two poses and the
 * pre-computed pocket architectures, alignment transform and
 * pocket-A component structure (so the report facade computes each
 * once). Pocket alignment is NOT re-implemented here.
 *
 * <p>Occupancy criterion (see {@link LigandSpaceOptions}): a sphere
 * is occupied when a ligand heavy-atom center lies within
 * {@code fraction * radius + tolerance} of the sphere center; the
 * defaults mean "atom inside the sphere". The verdict decomposes the
 * aligned pose-centroid displacement onto pocket A's principal axes:
 * a displacement dominated by the lateral u2/u3 components yields
 * {@link DominantArchitectureDifference#LATERAL_SHIFT}, one dominated
 * by the depth axis u1 yields
 * {@link DominantArchitectureDifference#DIFFERENT_DEPTH}.
 */
public final class LigandSpaceComparator {

    private final LigandSpaceOptions options;
    private final LigandSpaceAnalyzer spaceAnalyzer;

    public LigandSpaceComparator() {
        this(LigandSpaceOptions.defaults());
    }

    public LigandSpaceComparator(LigandSpaceOptions options) {
        this.options = Objects.requireNonNull(options, "options");
        this.spaceAnalyzer = new LigandSpaceAnalyzer(options);
    }

    public LigandSpaceComparison compare(
            Structure receptorA,
            Pocket pocketA,
            Ligand poseA,
            Structure receptorB,
            Pocket pocketB,
            Ligand poseB,
            PocketArchitecture architectureA,
            PocketArchitecture architectureB,
            RigidTransform transformBtoA,
            AlphaSphereArchitectureComparison.SphereComponents
                    componentsA
    ) {
        Objects.requireNonNull(receptorA, "receptorA");
        Objects.requireNonNull(pocketA, "pocketA");
        Objects.requireNonNull(poseA, "poseA");
        Objects.requireNonNull(receptorB, "receptorB");
        Objects.requireNonNull(pocketB, "pocketB");
        Objects.requireNonNull(poseB, "poseB");
        Objects.requireNonNull(architectureA, "architectureA");
        Objects.requireNonNull(architectureB, "architectureB");
        Objects.requireNonNull(transformBtoA, "transformBtoA");
        Objects.requireNonNull(componentsA, "componentsA");

        LigandSpaceAnalysis analysisA =
                spaceAnalyzer.analyze(receptorA, pocketA, poseA);
        LigandSpaceAnalysis analysisB =
                spaceAnalyzer.analyze(receptorB, pocketB, poseB);

        List<Point3D> atomsA = heavyAtomPositions(poseA);
        List<Point3D> atomsBAligned =
                transformBtoA.apply(heavyAtomPositions(poseB));

        Point3D centroidA = centroid(atomsA);
        Point3D centroidBAligned = centroid(atomsBAligned);

        List<AlphaSphere> spheresA = spheres(pocketA);
        List<AlphaSphere> spheresBAligned = spheres(pocketB).stream()
                .map(sphere -> new AlphaSphere(
                        sphere.id(),
                        transformBtoA.apply(sphere.center()),
                        sphere.radius()
                ))
                .toList();

        List<Integer> occupiedIndicesByPoseA =
                occupiedIndices(spheresA, atomsA);
        List<Integer> occupiedIndicesByPoseB =
                occupiedIndices(spheresA, atomsBAligned);

        Set<Long> occupiedA = new HashSet<>();
        for (int index : occupiedIndicesByPoseA) {
            occupiedA.add(spheresA.get(index).id());
        }

        Set<Long> occupiedB = new HashSet<>();
        List<AlphaSphere> spheresB = spheres(pocketB);
        List<Integer> occupiedIndicesB = occupiedIndices(
                spheresBAligned,
                atomsBAligned
        );
        for (int index : occupiedIndicesB) {
            occupiedB.add(spheresB.get(index).id());
        }

        Set<Integer> componentsPoseA =
                componentsOf(occupiedIndicesByPoseA, componentsA);
        Set<Integer> componentsPoseB =
                componentsOf(occupiedIndicesByPoseB, componentsA);

        double occupancyJaccard = jaccard(
                occupiedIndicesByPoseA,
                occupiedIndicesByPoseB
        );

        // Aligned centroid displacement decomposed onto pocket A's
        // principal axes (u1 = depth axis).
        PrincipalComponents pcaA =
                architectureA.principalComponents();
        double alongU1 = pcaA.offsetAlong(
                centroidBAligned, centroidA, 0);
        double alongU2 = pcaA.offsetAlong(
                centroidBAligned, centroidA, 1);
        double alongU3 = pcaA.offsetAlong(
                centroidBAligned, centroidA, 2);
        double lateral =
                Math.sqrt(alongU2 * alongU2 + alongU3 * alongU3);
        double totalDisplacement = Math.sqrt(
                alongU1 * alongU1 + lateral * lateral);

        double depthA = depth(architectureA, centroidA);
        double depthB = depth(architectureB,
                centroid(heavyAtomPositions(poseB)));

        double mouthDistanceA =
                centroidA.distance(architectureA.mouthCenter());
        double mouthDistanceB = centroid(heavyAtomPositions(poseB))
                .distance(architectureB.mouthCenter());

        return verdict(
                analysisA,
                analysisB,
                occupiedA,
                occupiedB,
                componentsPoseA,
                componentsPoseB,
                occupancyJaccard,
                totalDisplacement,
                alongU1,
                alongU2,
                alongU3,
                lateral,
                depthA,
                depthB,
                mouthDistanceA,
                mouthDistanceB
        );
    }

    private LigandSpaceComparison verdict(
            LigandSpaceAnalysis analysisA,
            LigandSpaceAnalysis analysisB,
            Set<Long> occupiedA,
            Set<Long> occupiedB,
            Set<Integer> componentsPoseA,
            Set<Integer> componentsPoseB,
            double occupancyJaccard,
            double totalDisplacement,
            double alongU1,
            double alongU2,
            double alongU3,
            double lateral,
            double depthA,
            double depthB,
            double mouthDistanceA,
            double mouthDistanceB
    ) {
        DominantArchitectureDifference difference;
        String reason;

        Set<Integer> sharedComponents = new HashSet<>(componentsPoseA);
        sharedComponents.retainAll(componentsPoseB);

        double absoluteU1 = Math.abs(alongU1);

        if (!componentsPoseA.isEmpty()
                && !componentsPoseB.isEmpty()
                && sharedComponents.isEmpty()) {
            difference =
                    DominantArchitectureDifference.DIFFERENT_COMPARTMENT;
            reason = String.format(
                    "the poses occupy disjoint components of the "
                            + "reference pocket (A occupies %s, "
                            + "aligned B occupies %s)",
                    componentsPoseA,
                    componentsPoseB
            );
        } else if (lateral > options.lateralShiftAngstroms()
                && lateral > absoluteU1) {
            difference = DominantArchitectureDifference.LATERAL_SHIFT;
            reason = String.format(
                    "aligned pose centroids displaced %.2f A total; "
                            + "lateral component %.2f A (u2 %+.2f, "
                            + "u3 %+.2f) dominates the depth-axis "
                            + "component %+.2f A along u1",
                    totalDisplacement,
                    lateral,
                    alongU2,
                    alongU3,
                    alongU1
            );
        } else if (absoluteU1 > options.depthDifferenceAngstroms()
                && absoluteU1 >= lateral) {
            difference = DominantArchitectureDifference.DIFFERENT_DEPTH;
            reason = String.format(
                    "aligned pose centroids displaced %+.2f A along "
                            + "the pocket depth axis u1 (lateral "
                            + "component %.2f A, total %.2f A)",
                    alongU1,
                    lateral,
                    totalDisplacement
            );
        } else if (Math.abs(mouthDistanceA - mouthDistanceB)
                > options.mouthDifferenceAngstroms()) {
            difference =
                    DominantArchitectureDifference.DIFFERENT_MOUTH_REGION;
            reason = String.format(
                    "pose centroid mouth-center distances differ by "
                            + "%.2f A (%.2f vs %.2f)",
                    Math.abs(mouthDistanceA - mouthDistanceB),
                    mouthDistanceA,
                    mouthDistanceB
            );
        } else if (!Double.isNaN(analysisA.meanWallDistance())
                && !Double.isNaN(analysisB.meanWallDistance())
                && Math.abs(analysisA.meanWallDistance()
                        - analysisB.meanWallDistance())
                        > options.wallShiftAngstroms()) {
            difference = DominantArchitectureDifference.SHIFTED_WALL;
            reason = String.format(
                    "mean wall distances differ by %.2f A "
                            + "(%.2f vs %.2f)",
                    Math.abs(analysisA.meanWallDistance()
                            - analysisB.meanWallDistance()),
                    analysisA.meanWallDistance(),
                    analysisB.meanWallDistance()
            );
        } else {
            difference = DominantArchitectureDifference.NONE;
            reason = "no documented geometric difference exceeded "
                    + "its threshold";
        }

        return new LigandSpaceComparison(
                analysisA,
                analysisB,
                occupiedA,
                occupiedB,
                componentsPoseA,
                componentsPoseB,
                occupancyJaccard,
                totalDisplacement,
                alongU1,
                alongU2,
                alongU3,
                lateral,
                depthA,
                depthB,
                mouthDistanceA,
                mouthDistanceB,
                difference,
                reason
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

    private List<Integer> occupiedIndices(
            List<AlphaSphere> spheres,
            List<Point3D> atoms
    ) {
        List<Integer> occupied = new ArrayList<>();

        for (int index = 0; index < spheres.size(); index++) {
            AlphaSphere sphere = spheres.get(index);
            double limit = options.sphereOccupancyRadiusFraction()
                    * sphere.radius()
                    + options.sphereOccupancyToleranceAngstroms();

            for (Point3D atom : atoms) {
                if (atom.distance(sphere.center()) <= limit) {
                    occupied.add(index);
                    break;
                }
            }
        }

        return occupied;
    }

    private static Set<Integer> componentsOf(
            List<Integer> sphereIndices,
            AlphaSphereArchitectureComparison.SphereComponents
                    components
    ) {
        Set<Integer> occupied = new HashSet<>();

        for (int index : sphereIndices) {
            occupied.add(components.componentBySphereIndex().get(index));
        }

        return occupied;
    }

    private static double jaccard(
            List<Integer> first,
            List<Integer> second
    ) {
        Set<Integer> union = new HashSet<>(first);
        union.addAll(second);

        if (union.isEmpty()) {
            return 0.0;
        }

        Set<Integer> intersection = new HashSet<>(first);
        intersection.retainAll(second);

        return intersection.size() / (double) union.size();
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

    private static List<Point3D> heavyAtomPositions(Ligand pose) {
        return pose.structure().getChains().stream()
                .flatMap(chain -> chain.residues().stream())
                .flatMap(residue -> residue.getAtoms().stream())
                .filter(Atom::isHeavyAtom)
                .map(Atom::getPosition)
                .toList();
    }

    private static List<AlphaSphere> spheres(Pocket pocket) {
        return pocket.alphaSphereSet()
                .map(AlphaSphereSet::spheres)
                .orElse(List.of());
    }
}
