package totah.lab.athena.pocket.architecture;

import totah.lab.athena.sequence.AlignedResiduePair;
import totah.lab.athena.sequence.NeedlemanWunschSequenceAligner;
import totah.lab.athena.sequence.SequenceAlignment;
import totah.lab.athena.sequence.StructureSequences;
import totah.lab.gaia.geometry.Point3D;
import totah.lab.gaia.geometry.RigidTransform;
import totah.lab.gaia.geometry.Vector3D;
import totah.lab.gaia.pocket.AlphaSphere;
import totah.lab.gaia.pocket.AlphaSphereSet;
import totah.lab.gaia.pocket.Pocket;
import totah.lab.gaia.structure.Atom;
import totah.lab.gaia.structure.Chain;
import totah.lab.gaia.structure.Residue;
import totah.lab.gaia.structure.ResidueId;
import totah.lab.gaia.structure.Structure;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Compares the pocket wall by geometry: side-chain heavy-atom
 * positions of pocket residues in the aligned frame. See
 * {@link WallGeometryComparison} for the exact metric definitions.
 *
 * <p>The supplied transform (candidate/B &rarr; query/A) must be
 * receptor-derived — the facade passes the backbone CA-Kabsch
 * transform, so wall displacements are measured in the same frame as
 * the backbone CA displacements. A sphere-cloud-derived transform is
 * the wrong frame for residue geometry when the two sphere sets
 * differ substantially or when spheres and residues originate from
 * different coordinate artifacts.</p>
 */
public final class WallGeometryAnalyzer {

    private static final Set<String> BACKBONE_ATOMS =
            Set.of("N", "CA", "C", "O", "OXT");

    private final WallGeometryOptions options;

    public WallGeometryAnalyzer() {
        this(WallGeometryOptions.defaults());
    }

    public WallGeometryAnalyzer(WallGeometryOptions options) {
        this.options = Objects.requireNonNull(options, "options");
    }

    public WallGeometryComparison compare(
            Structure receptorA,
            Pocket pocketA,
            Structure receptorB,
            Pocket pocketB,
            RigidTransform transformBtoA
    ) {
        Objects.requireNonNull(receptorA, "receptorA");
        Objects.requireNonNull(pocketA, "pocketA");
        Objects.requireNonNull(receptorB, "receptorB");
        Objects.requireNonNull(pocketB, "pocketB");
        Objects.requireNonNull(transformBtoA, "transformBtoA");

        SequenceAlignment alignment =
                new NeedlemanWunschSequenceAligner().align(
                        StructureSequences.sequenceResidues(receptorA),
                        StructureSequences.sequenceResidues(receptorB)
                );

        Map<Integer, ResidueContext> residuesA =
                residuesByNumber(receptorA);
        Map<Integer, ResidueContext> residuesB =
                residuesByNumber(receptorB);

        Set<Integer> pocketNumbersA = pocketResidueNumbers(pocketA);
        Set<Integer> pocketNumbersB = pocketResidueNumbers(pocketB);

        List<WallGeometryComparison.SideChainDisplacement>
                displacements = new ArrayList<>();

        for (AlignedResiduePair pair : alignment.pairs()) {
            if (!pocketNumbersA.contains(pair.queryResidueNumber())
                    && !pocketNumbersB.contains(
                            pair.candidateResidueNumber())) {
                continue;
            }

            ResidueContext residueA =
                    residuesA.get(pair.queryResidueNumber());
            ResidueContext residueB =
                    residuesB.get(pair.candidateResidueNumber());

            if (residueA == null || residueB == null
                    || residueA.sideChainCentroid() == null
                    || residueB.sideChainCentroid() == null) {
                continue;
            }

            displacements.add(
                    new WallGeometryComparison.SideChainDisplacement(
                            residueA.id(),
                            residueB.id(),
                            residueA.residue().getName(),
                            residueB.residue().getName(),
                            residueA.sideChainCentroid().distance(
                                    transformBtoA.apply(
                                            residueB.sideChainCentroid()))
                    ));
        }

        displacements.sort(Comparator.comparingDouble(
                WallGeometryComparison.SideChainDisplacement
                        ::centroidDisplacement
        ).reversed());

        List<Point3D> wallAtomsA =
                wallAtoms(receptorA, pocketNumbersA);
        List<Point3D> wallAtomsBAligned = wallAtoms(
                receptorB,
                pocketNumbersB
        ).stream().map(transformBtoA::apply).toList();

        if (wallAtomsA.size() < 3 || wallAtomsBAligned.size() < 3) {
            throw new IllegalArgumentException(
                    "Wall geometry requires at least 3 wall side-chain "
                            + "heavy atoms per pocket (A: "
                            + wallAtomsA.size() + ", B: "
                            + wallAtomsBAligned.size() + ")"
            );
        }

        List<AlphaSphere> spheresA = spheres(pocketA);
        List<Point3D> centersBAligned = spheres(pocketB).stream()
                .map(sphere -> transformBtoA.apply(sphere.center()))
                .toList();

        List<Double> fieldA = new ArrayList<>();
        List<Double> normalAngles = new ArrayList<>();
        List<Double> roughnessA = new ArrayList<>();
        List<Double> roughnessB = new ArrayList<>();

        for (AlphaSphere sphere : spheresA) {
            Point3D center = sphere.center();
            fieldA.add(nearestDistance(center, wallAtomsA));

            LocalPlane planeA = localPlane(center, wallAtomsA);
            if (planeA == null) {
                continue;
            }
            roughnessA.add(planeA.roughness());

            int nearestB = nearestIndex(center, centersBAligned);
            LocalPlane planeB = localPlane(
                    centersBAligned.get(nearestB),
                    wallAtomsBAligned
            );
            if (planeB != null) {
                normalAngles.add(acuteAngleDegrees(
                        planeA.normal(),
                        planeB.normal()
                ));
            }
        }

        List<Double> fieldB = new ArrayList<>();
        List<AlphaSphere> spheresB = spheres(pocketB);
        List<Point3D> wallAtomsB =
                wallAtoms(receptorB, pocketNumbersB);

        for (int index = 0; index < spheresB.size(); index++) {
            Point3D center = spheresB.get(index).center();
            fieldB.add(nearestDistance(center, wallAtomsB));

            LocalPlane planeB = localPlane(center, wallAtomsB);
            if (planeB != null) {
                roughnessB.add(planeB.roughness());
            }
        }

        WallGeometryComparison.SideChainDisplacement top =
                displacements.isEmpty() ? null : displacements.get(0);

        return new WallGeometryComparison(
                displacements,
                top == null ? null : top.residueA(),
                top == null ? null : top.residueB(),
                top == null ? 0.0 : top.centroidDisplacement(),
                fieldA,
                fieldB,
                mean(fieldA),
                mean(fieldB),
                mean(normalAngles),
                normalAngles.stream()
                        .mapToDouble(Double::doubleValue)
                        .max()
                        .orElse(0.0),
                mean(roughnessA),
                mean(roughnessB)
        );
    }

    /**
     * PCA fit plane over the {@code normalNeighbourCount} wall atoms
     * nearest to {@code center}: the normal is the smallest-eigenvalue
     * eigenvector; roughness is the RMS distance of those atoms from
     * the plane. {@code null} when fewer than 3 neighbours exist.
     */
    private LocalPlane localPlane(
            Point3D center,
            List<Point3D> wallAtoms
    ) {
        List<Point3D> neighbours = new ArrayList<>(wallAtoms);
        neighbours.sort(Comparator.comparingDouble(
                point -> point.distanceSquared(center)
        ));

        int count = Math.min(
                options.normalNeighbourCount(),
                neighbours.size()
        );

        if (count < 3) {
            return null;
        }

        List<Point3D> local = neighbours.subList(0, count);
        PrincipalComponents pca = PrincipalComponents.of(local);

        Vector3D normal = pca.axes().get(2);

        double squaredSum = 0.0;
        for (Point3D point : local) {
            double distance = Math.abs(
                    point.vectorFrom(pca.centroid()).dot(normal));
            squaredSum += distance * distance;
        }

        return new LocalPlane(normal, Math.sqrt(squaredSum / count));
    }

    private static double acuteAngleDegrees(
            Vector3D first,
            Vector3D second
    ) {
        double cosine = Math.min(
                1.0,
                Math.abs(first.normalize().dot(second.normalize()))
        );

        return Math.toDegrees(Math.acos(cosine));
    }

    private static double nearestDistance(
            Point3D center,
            List<Point3D> atoms
    ) {
        double nearest = Double.MAX_VALUE;

        for (Point3D atom : atoms) {
            nearest = Math.min(nearest, center.distance(atom));
        }

        return nearest;
    }

    private static int nearestIndex(
            Point3D center,
            List<Point3D> points
    ) {
        int nearest = 0;
        double nearestDistance = Double.MAX_VALUE;

        for (int index = 0; index < points.size(); index++) {
            double distance = center.distanceSquared(points.get(index));

            if (distance < nearestDistance) {
                nearestDistance = distance;
                nearest = index;
            }
        }

        return nearest;
    }

    private static double mean(List<Double> values) {
        return values.stream()
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(0.0);
    }

    private static List<Point3D> wallAtoms(
            Structure receptor,
            Set<Integer> pocketNumbers
    ) {
        List<Point3D> atoms = new ArrayList<>();

        for (Chain chain : receptor.getChains()) {
            for (Residue residue : chain.residues()) {
                if (!pocketNumbers.contains(residue.getNumber())) {
                    continue;
                }

                for (Atom atom : sideChainAtoms(residue)) {
                    atoms.add(atom.getPosition());
                }
            }
        }

        return atoms;
    }

    /**
     * Side-chain heavy atoms of a residue; falls back to CA when the
     * residue has no side-chain heavy atoms (e.g. GLY).
     */
    private static List<Atom> sideChainAtoms(Residue residue) {
        List<Atom> sideChain = residue.getAtoms().stream()
                .filter(Objects::nonNull)
                .filter(Atom::isHeavyAtom)
                .filter(atom -> !BACKBONE_ATOMS.contains(atom.getName()))
                .toList();

        if (!sideChain.isEmpty()) {
            return sideChain;
        }

        return residue.findAtom("CA")
                .map(List::of)
                .orElse(List.of());
    }

    private static Point3D sideChainCentroid(Residue residue) {
        List<Atom> atoms = sideChainAtoms(residue);

        if (atoms.isEmpty()) {
            return null;
        }

        double x = 0.0;
        double y = 0.0;
        double z = 0.0;

        for (Atom atom : atoms) {
            x += atom.getPosition().x();
            y += atom.getPosition().y();
            z += atom.getPosition().z();
        }

        double n = atoms.size();

        return new Point3D(x / n, y / n, z / n);
    }

    private static Map<Integer, ResidueContext> residuesByNumber(
            Structure receptor
    ) {
        Map<Integer, ResidueContext> index = new LinkedHashMap<>();

        for (Chain chain : receptor.getChains()) {
            for (Residue residue : chain.residues()) {
                index.putIfAbsent(
                        residue.getNumber(),
                        new ResidueContext(
                                new ResidueId(
                                        chain.id(),
                                        residue.getNumber(),
                                        residue.getInsertionCode()
                                ),
                                residue,
                                sideChainCentroid(residue)
                        )
                );
            }
        }

        return index;
    }

    private static Set<Integer> pocketResidueNumbers(Pocket pocket) {
        return pocket.residues().stream()
                .map(ResidueId::residueNumber)
                .collect(Collectors.toSet());
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

    private record ResidueContext(
            ResidueId id,
            Residue residue,
            Point3D sideChainCentroid
    ) {
    }

    private record LocalPlane(
            Vector3D normal,
            double roughness
    ) {
    }
}
