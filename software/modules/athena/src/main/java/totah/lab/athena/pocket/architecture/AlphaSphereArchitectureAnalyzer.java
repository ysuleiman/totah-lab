package totah.lab.athena.pocket.architecture;

import totah.lab.athena.pocket.compare.PocketAlignmentResult;
import totah.lab.gaia.geometry.Point3D;
import totah.lab.gaia.geometry.RigidTransform;
import totah.lab.gaia.geometry.Vector3D;
import totah.lab.gaia.pocket.AlphaSphere;
import totah.lab.gaia.pocket.AlphaSphereSet;
import totah.lab.gaia.pocket.Pocket;
import totah.lab.gaia.structure.Structure;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Compares the alpha-sphere architecture of two pockets after
 * structural alignment. See {@link AlphaSphereArchitectureComparison}
 * for the exact metric definitions.
 */
public final class AlphaSphereArchitectureAnalyzer {

    private final AlphaSphereArchitectureOptions options;

    public AlphaSphereArchitectureAnalyzer() {
        this(AlphaSphereArchitectureOptions.defaults());
    }

    public AlphaSphereArchitectureAnalyzer(
            AlphaSphereArchitectureOptions options
    ) {
        this.options = Objects.requireNonNull(options, "options");
    }

    public AlphaSphereArchitectureComparison compare(
            Structure receptorA,
            Pocket pocketA,
            Structure receptorB,
            Pocket pocketB
    ) {
        PocketAlignmentResult alignment =
                PocketArchitectureSupport.alignPockets(
                        receptorA,
                        pocketA,
                        receptorB,
                        pocketB
                );

        return compareAligned(receptorA, pocketA, receptorB, pocketB,
                alignment);
    }

    /**
     * Computes the comparison from a pre-computed pocket alignment
     * (used by the report facade so the aligner runs once).
     */
    public AlphaSphereArchitectureComparison compareAligned(
            Structure receptorA,
            Pocket pocketA,
            Structure receptorB,
            Pocket pocketB,
            PocketAlignmentResult alignment
    ) {
        Objects.requireNonNull(alignment, "alignment");

        List<AlphaSphere> spheresA = spheres(pocketA);
        List<AlphaSphere> spheresB = spheres(pocketB);

        RigidTransform transformBtoA =
                alignment.alignment().transform();

        List<Point3D> centersA =
                spheresA.stream().map(AlphaSphere::center).toList();
        List<Point3D> centersB = spheresB.stream()
                .map(sphere -> transformBtoA.apply(sphere.center()))
                .toList();

        return new AlphaSphereArchitectureComparison(
                alignment,
                nearestDistances(centersA, centersB),
                nearestDistances(centersB, centersA),
                components(spheresA),
                components(spheresB),
                axisAngle(spheresA, spheresB, transformBtoA),
                uniqueSpheres(spheresA, centersB),
                uniqueSpheres(spheresB, centersA, transformBtoA),
                volumeSum(spheresA),
                volumeSum(spheresB),
                volumeSum(spheresB) - volumeSum(spheresA)
        );
    }

    /**
     * Connected components of one sphere set: union-find with an edge
     * when two sphere surfaces are within
     * {@code componentGapAngstroms}.
     */
    public AlphaSphereArchitectureComparison.SphereComponents
            components(List<AlphaSphere> spheres) {

        int[] parent = new int[spheres.size()];
        for (int index = 0; index < parent.length; index++) {
            parent[index] = index;
        }

        for (int first = 0; first < spheres.size(); first++) {
            for (int second = first + 1;
                    second < spheres.size(); second++) {
                double surfaceGap = spheres.get(first).center()
                        .distance(spheres.get(second).center())
                        - spheres.get(first).radius()
                        - spheres.get(second).radius();

                if (surfaceGap <= options.componentGapAngstroms()) {
                    union(parent, first, second);
                }
            }
        }

        List<Integer> componentByIndex = new ArrayList<>(spheres.size());
        List<Integer> sizes = new ArrayList<>();
        int[] rootToComponent = new int[spheres.size()];
        Arrays.fill(rootToComponent, -1);

        // Component ids are assigned in order of first discovery.
        for (int index = 0; index < spheres.size(); index++) {
            int root = find(parent, index);

            if (rootToComponent[root] < 0) {
                rootToComponent[root] = sizes.size();
                sizes.add(0);
            }

            int component = rootToComponent[root];
            componentByIndex.add(component);
            sizes.set(component, sizes.get(component) + 1);
        }

        int componentCount = sizes.size();
        sizes.sort(Comparator.reverseOrder());

        return new AlphaSphereArchitectureComparison.SphereComponents(
                componentCount,
                sizes,
                componentByIndex
        );
    }

    private static int find(int[] parent, int index) {
        int root = index;

        while (parent[root] != root) {
            root = parent[root];
        }

        while (parent[index] != root) {
            int next = parent[index];
            parent[index] = root;
            index = next;
        }

        return root;
    }

    private static void union(int[] parent, int first, int second) {
        parent[find(parent, first)] = find(parent, second);
    }

    private double axisAngle(
            List<AlphaSphere> spheresA,
            List<AlphaSphere> spheresB,
            RigidTransform transformBtoA
    ) {
        Vector3D axisA = PrincipalComponents.of(
                spheresA.stream().map(AlphaSphere::center).toList()
        ).axes().get(0);

        PrincipalComponents pcaB = PrincipalComponents.of(
                spheresB.stream().map(AlphaSphere::center).toList()
        );

        // Rotate B's first axis into the A frame.
        Point3D rotatedOrigin =
                transformBtoA.apply(pcaB.centroid());
        Point3D rotatedTip = transformBtoA.apply(
                pcaB.centroid().add(pcaB.axes().get(0)));
        Vector3D axisB = rotatedTip.vectorFrom(rotatedOrigin)
                .normalize();

        double cosine = Math.min(1.0, Math.abs(axisA.dot(axisB)));

        return Math.toDegrees(Math.acos(cosine));
    }

    private List<Long> uniqueSpheres(
            List<AlphaSphere> spheres,
            List<Point3D> otherCentersAligned
    ) {
        List<Long> unique = new ArrayList<>();

        for (AlphaSphere sphere : spheres) {
            double nearest = Double.MAX_VALUE;

            for (Point3D other : otherCentersAligned) {
                nearest = Math.min(
                        nearest,
                        sphere.center().distance(other)
                );
            }

            if (nearest > options.uniqueSphereDistanceAngstroms()) {
                unique.add(sphere.id());
            }
        }

        return List.copyOf(unique);
    }

    private List<Long> uniqueSpheres(
            List<AlphaSphere> spheres,
            List<Point3D> otherCenters,
            RigidTransform transform
    ) {
        List<Point3D> alignedOwn = spheres.stream()
                .map(sphere -> transform.apply(sphere.center()))
                .toList();

        List<Long> unique = new ArrayList<>();

        for (int index = 0; index < spheres.size(); index++) {
            double nearest = Double.MAX_VALUE;

            for (Point3D other : otherCenters) {
                nearest = Math.min(
                        nearest,
                        alignedOwn.get(index).distance(other)
                );
            }

            if (nearest > options.uniqueSphereDistanceAngstroms()) {
                unique.add(spheres.get(index).id());
            }
        }

        return List.copyOf(unique);
    }

    private static List<Double> nearestDistances(
            List<Point3D> from,
            List<Point3D> to
    ) {
        List<Double> distances = new ArrayList<>(from.size());

        for (Point3D point : from) {
            double nearest = Double.MAX_VALUE;

            for (Point3D target : to) {
                nearest = Math.min(nearest, point.distance(target));
            }

            distances.add(nearest);
        }

        return List.copyOf(distances);
    }

    private static double volumeSum(List<AlphaSphere> spheres) {
        double sum = 0.0;

        for (AlphaSphere sphere : spheres) {
            double radius = sphere.radius();
            sum += 4.0 / 3.0 * Math.PI * radius * radius * radius;
        }

        return sum;
    }

    private static List<AlphaSphere> spheres(Pocket pocket) {
        Objects.requireNonNull(pocket, "pocket");

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
