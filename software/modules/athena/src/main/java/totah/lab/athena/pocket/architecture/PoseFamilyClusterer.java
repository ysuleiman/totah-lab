package totah.lab.athena.pocket.architecture;

import totah.lab.athena.ligand.contact.LigandContact;
import totah.lab.athena.pocket.compare.KabschRigidPointAligner;
import totah.lab.gaia.geometry.Point3D;
import totah.lab.gaia.geometry.RigidTransform;
import totah.lab.gaia.molecule.Ligand;
import totah.lab.gaia.pocket.AlphaSphere;
import totah.lab.gaia.pocket.AlphaSphereSet;
import totah.lab.gaia.pocket.Pocket;
import totah.lab.gaia.structure.Atom;
import totah.lab.gaia.structure.ResidueId;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Deterministic greedy clustering of N poses of the same ligand in
 * ONE receptor frame (no alignment — the poses already share the
 * frame). Poses are visited in input order; a pose joins the first
 * family whose representative (the family's first member) is within
 * the RMSD threshold, otherwise it starts a new family. The number
 * of families is an output, never an input.
 *
 * <p>Pairwise RMSD uses Kabsch superposition over the atom
 * correspondence verified by {@link LigandAtomCorrespondence}
 * against pose 0; a pose that cannot be mapped always forms its own
 * singleton family and its matrix entries are {@code NaN}.
 */
public final class PoseFamilyClusterer {

    private final PoseFamilyClusteringOptions options;
    private final KabschRigidPointAligner kabschAligner =
            new KabschRigidPointAligner();

    public PoseFamilyClusterer() {
        this(PoseFamilyClusteringOptions.defaults());
    }

    public PoseFamilyClusterer(PoseFamilyClusteringOptions options) {
        this.options = Objects.requireNonNull(options, "options");
    }

    public PoseFamilyClustering cluster(List<Ligand> poses) {
        return cluster(poses, null, null);
    }

    /**
     * @param contactsOrNull per-pose contact lists (parallel to
     *        {@code poses}), or {@code null}
     * @param pocketOrNull pocket whose alpha spheres define the
     *        occupancy sets, or {@code null}
     */
    public PoseFamilyClustering cluster(
            List<Ligand> poses,
            List<List<LigandContact>> contactsOrNull,
            Pocket pocketOrNull
    ) {
        Objects.requireNonNull(poses, "poses");

        if (poses.isEmpty()) {
            throw new IllegalArgumentException(
                    "poses must not be empty"
            );
        }

        if (contactsOrNull != null
                && contactsOrNull.size() != poses.size()) {
            throw new IllegalArgumentException(
                    "contacts must be parallel to poses"
            );
        }

        List<List<Point3D>> positionsByPose = poses.stream()
                .map(PoseFamilyClusterer::heavyAtomPositions)
                .toList();

        for (List<Point3D> positions : positionsByPose) {
            if (positions.isEmpty()) {
                throw new IllegalArgumentException(
                        "Every pose must contain heavy atoms"
                );
            }
        }

        // Verify each pose's correspondence against pose 0 once.
        List<LigandAtomCorrespondence.Mapping> mappings =
                new ArrayList<>();
        List<int[]> poseZeroOrder = new ArrayList<>();

        for (Ligand pose : poses) {
            LigandAtomCorrespondence.Mapping mapping =
                    LigandAtomCorrespondence.map(poses.get(0), pose);
            mappings.add(mapping);
            poseZeroOrder.add(mapping.bToAIndexView());
        }

        int count = poses.size();

        List<List<Double>> rmsdMatrix = matrix(count, Double.NaN);
        List<List<Double>> centroidMatrix = matrix(count, 0.0);
        List<List<Double>> contactMatrix = contactsOrNull == null
                ? null
                : matrix(count, 0.0);
        List<List<Double>> occupancyMatrix = pocketOrNull == null
                ? null
                : matrix(count, 0.0);

        List<Point3D> centroids = positionsByPose.stream()
                .map(PoseFamilyClusterer::centroid)
                .toList();

        List<Set<Long>> occupiedByPose = pocketOrNull == null
                ? null
                : positionsByPose.stream()
                        .map(positions -> occupiedSpheres(
                                pocketOrNull,
                                positions
                        ))
                        .toList();

        for (int first = 0; first < count; first++) {
            for (int second = first + 1; second < count; second++) {
                centroidMatrix.get(first).set(second,
                        centroids.get(first).distance(
                                centroids.get(second)));
                centroidMatrix.get(second).set(first,
                        centroidMatrix.get(first).get(second));

                if (mappings.get(first).method()
                        != LigandAtomCorrespondence.Method.NONE
                        && mappings.get(second).method()
                        != LigandAtomCorrespondence.Method.NONE) {
                    double rmsd = superimposedRmsd(
                            positionsByPose.get(first),
                            poseZeroOrder.get(first),
                            positionsByPose.get(second),
                            poseZeroOrder.get(second)
                    );
                    rmsdMatrix.get(first).set(second, rmsd);
                    rmsdMatrix.get(second).set(first, rmsd);
                }

                if (contactMatrix != null) {
                    double jaccard = jaccard(
                            contactResidues(
                                    contactsOrNull.get(first)),
                            contactResidues(
                                    contactsOrNull.get(second))
                    );
                    contactMatrix.get(first).set(second, jaccard);
                    contactMatrix.get(second).set(first, jaccard);
                }

                if (occupancyMatrix != null) {
                    double jaccard = jaccard(
                            occupiedByPose.get(first),
                            occupiedByPose.get(second)
                    );
                    occupancyMatrix.get(first).set(second, jaccard);
                    occupancyMatrix.get(second).set(first, jaccard);
                }
            }
        }

        for (int index = 0; index < count; index++) {
            rmsdMatrix.get(index).set(
                    index,
                    mappings.get(index).method()
                            == LigandAtomCorrespondence.Method.NONE
                            ? Double.NaN
                            : 0.0
            );

            if (contactMatrix != null) {
                contactMatrix.get(index).set(index, 1.0);
            }

            if (occupancyMatrix != null) {
                occupancyMatrix.get(index).set(
                        index,
                        occupiedByPose.get(index).isEmpty()
                                ? 0.0
                                : 1.0
                );
            }
        }

        return new PoseFamilyClustering(
                assignFamilies(poses, mappings, rmsdMatrix),
                rmsdMatrix,
                centroidMatrix,
                contactMatrix,
                occupancyMatrix,
                mappings.stream()
                        .map(LigandAtomCorrespondence.Mapping::method)
                        .toList()
        );
    }

    /**
     * Greedy input-order clustering: join the first family whose
     * representative is within the RMSD threshold, else start a new
     * family. Unmappable poses always start a singleton family.
     */
    private List<PoseFamilyClustering.PoseFamily> assignFamilies(
            List<Ligand> poses,
            List<LigandAtomCorrespondence.Mapping> mappings,
            List<List<Double>> rmsdMatrix
    ) {
        List<PoseFamilyClustering.PoseFamily> families =
                new ArrayList<>();

        for (int index = 0; index < poses.size(); index++) {
            boolean assigned = false;

            if (mappings.get(index).method()
                    != LigandAtomCorrespondence.Method.NONE) {
                for (int familyIndex = 0;
                        familyIndex < families.size(); familyIndex++) {
                    PoseFamilyClustering.PoseFamily family =
                            families.get(familyIndex);
                    double rmsd = rmsdMatrix.get(index).get(
                            family.representativeIndex());

                    if (!Double.isNaN(rmsd)
                            && rmsd <= options
                                    .rmsdThresholdAngstroms()) {
                        List<Integer> members = new ArrayList<>(
                                family.memberIndices());
                        members.add(index);
                        families.set(familyIndex,
                                new PoseFamilyClustering.PoseFamily(
                                        family.representativeIndex(),
                                        members
                                ));
                        assigned = true;
                        break;
                    }
                }
            }

            if (!assigned) {
                families.add(new PoseFamilyClustering.PoseFamily(
                        index,
                        List.of(index)
                ));
            }
        }

        return List.copyOf(families);
    }

    /**
     * RMSD after Kabsch superposition of pose B onto pose A, over the
     * pose-0-indexed correspondence of both poses.
     */
    private double superimposedRmsd(
            List<Point3D> positionsA,
            int[] orderA,
            List<Point3D> positionsB,
            int[] orderB
    ) {
        // orderX[i] is the pose-0 index of pose-X heavy atom i.
        // Build both point sets in pose-0 index order.
        Point3D[] orderedA = new Point3D[positionsA.size()];
        Point3D[] orderedB = new Point3D[positionsB.size()];

        for (int index = 0; index < orderA.length; index++) {
            orderedA[orderA[index]] = positionsA.get(index);
        }

        for (int index = 0; index < orderB.length; index++) {
            orderedB[orderB[index]] = positionsB.get(index);
        }

        List<Point3D> a = List.of(orderedA);
        List<Point3D> b = List.of(orderedB);

        try {
            RigidTransform fit = kabschAligner.align(b, a);
            List<Point3D> fittedB = fit.apply(b);

            double sum = 0.0;
            for (int index = 0; index < a.size(); index++) {
                sum += a.get(index).distanceSquared(
                        fittedB.get(index));
            }

            return Math.sqrt(sum / a.size());
        } catch (RuntimeException exception) {
            // Degenerate point set (e.g. collinear): fall back to the
            // plain correspondence RMSD without superposition.
            double sum = 0.0;
            for (int index = 0; index < a.size(); index++) {
                sum += a.get(index).distanceSquared(b.get(index));
            }

            return Math.sqrt(sum / a.size());
        }
    }

    private Set<Long> occupiedSpheres(
            Pocket pocket,
            List<Point3D> positions
    ) {
        Set<Long> occupied = new HashSet<>();

        List<AlphaSphere> spheres = pocket.alphaSphereSet()
                .map(AlphaSphereSet::spheres)
                .orElse(List.of());

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

    private static Set<ResidueId> contactResidues(
            List<LigandContact> contacts
    ) {
        Set<ResidueId> residues = new HashSet<>();

        for (LigandContact contact : contacts) {
            residues.add(contact.residue());
        }

        return residues;
    }

    private static <T> double jaccard(Set<T> first, Set<T> second) {
        Set<T> union = new HashSet<>(first);
        union.addAll(second);

        if (union.isEmpty()) {
            return 0.0;
        }

        Set<T> intersection = new HashSet<>(first);
        intersection.retainAll(second);

        return intersection.size() / (double) union.size();
    }

    private static List<List<Double>> matrix(
            int size,
            double fill
    ) {
        List<List<Double>> matrix = new ArrayList<>(size);

        for (int row = 0; row < size; row++) {
            List<Double> values = new ArrayList<>(size);

            for (int column = 0; column < size; column++) {
                values.add(fill);
            }

            matrix.add(values);
        }

        return matrix;
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
}
