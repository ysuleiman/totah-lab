package totah.lab.athena.pocket.architecture;

import totah.lab.athena.sequence.AlignedResiduePair;
import totah.lab.athena.sequence.NeedlemanWunschSequenceAligner;
import totah.lab.athena.sequence.SequenceAlignment;
import totah.lab.athena.sequence.StructureSequences;
import totah.lab.gaia.geometry.Point3D;
import totah.lab.gaia.geometry.RigidTransform;
import totah.lab.gaia.molecule.Ligand;
import totah.lab.gaia.pocket.AlphaSphere;
import totah.lab.gaia.pocket.AlphaSphereSet;
import totah.lab.gaia.pocket.Pocket;
import totah.lab.gaia.structure.Atom;
import totah.lab.gaia.structure.Chain;
import totah.lab.gaia.structure.Residue;
import totah.lab.gaia.structure.ResidueId;
import totah.lab.gaia.structure.Structure;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Analyzes one residue range of two receptors against their docked
 * poses, in the receptor-backbone frame: the supplied transform
 * (B&rarr;A) is the CA-Kabsch transform computed by
 * {@link BackboneArchitectureAnalyzer}, and pose B is aligned with it
 * here. Residue correspondence comes from the sequence alignment,
 * never from raw residue numbers across receptors.
 *
 * <p>Side-chain quantities use the side-chain heavy atoms (N/CA/C/O/
 * OXT excluded); a residue without side-chain heavy atoms (e.g. GLY)
 * falls back to its CA. The burial proxy counts receptor heavy atoms
 * within the burial radius of the side-chain centroid, excluding the
 * residue's own atoms — a deterministic geometric estimate, not a
 * SASA measurement. The verdict measures the pose displacement
 * relative to the loop centroid; it does not claim the loop causes
 * the displacement.
 */
public final class LoopRegionAnalyzer {

    private static final Set<String> BACKBONE_ATOMS =
            Set.of("N", "CA", "C", "O", "OXT");

    private static final double DIRECTION_EPSILON = 1.0e-9;

    private final LoopRegionOptions options;

    public LoopRegionAnalyzer() {
        this(LoopRegionOptions.defaults());
    }

    public LoopRegionAnalyzer(LoopRegionOptions options) {
        this.options = Objects.requireNonNull(options, "options");
    }

    public LoopRegionAnalysis analyze(
            Structure receptorA,
            Structure receptorB,
            RigidTransform transformBtoA,
            Ligand poseA,
            Ligand poseB,
            Pocket pocketA,
            Pocket pocketB
    ) {
        Objects.requireNonNull(receptorA, "receptorA");
        Objects.requireNonNull(receptorB, "receptorB");
        Objects.requireNonNull(transformBtoA, "transformBtoA");
        Objects.requireNonNull(poseA, "poseA");
        Objects.requireNonNull(poseB, "poseB");
        Objects.requireNonNull(pocketA, "pocketA");
        Objects.requireNonNull(pocketB, "pocketB");

        List<Point3D> poseAtomsA = heavyAtomPositions(poseA);
        List<Point3D> poseAtomsBAligned =
                transformBtoA.apply(heavyAtomPositions(poseB));

        if (poseAtomsA.isEmpty() || poseAtomsBAligned.isEmpty()) {
            throw new IllegalArgumentException(
                    "Both poses must contain heavy atoms"
            );
        }

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

        List<NumberedAtom> receptorAtomsA = receptorAtoms(receptorA);
        List<NumberedAtom> receptorAtomsB = receptorAtoms(receptorB);

        List<Point3D> sphereCentersA = sphereCenters(pocketA);
        List<Point3D> sphereCentersBAligned = sphereCenters(
                pocketB
        ).stream().map(transformBtoA::apply).toList();

        List<LoopRegionAnalysis.LoopRegionResidueRow> rows =
                new ArrayList<>();
        List<Point3D> loopCaA = new ArrayList<>();

        for (AlignedResiduePair pair : alignment.pairs()) {
            int numberA = pair.queryResidueNumber();
            int numberB = pair.candidateResidueNumber();

            boolean inRangeA = numberA >= options.rangeStart()
                    && numberA <= options.rangeEnd();
            boolean inRangeB = numberB >= options.rangeStart()
                    && numberB <= options.rangeEnd();

            if (!inRangeA && !inRangeB) {
                continue;
            }

            ResidueContext residueA = residuesA.get(numberA);
            ResidueContext residueB = residuesB.get(numberB);

            if (residueA == null || residueB == null
                    || residueA.alphaCarbon() == null
                    || residueB.alphaCarbon() == null
                    || residueA.sideChainCentroid() == null
                    || residueB.sideChainCentroid() == null) {
                continue;
            }

            loopCaA.add(residueA.alphaCarbon());

            rows.add(row(
                    pair,
                    residueA,
                    residueB,
                    transformBtoA,
                    poseAtomsA,
                    poseAtomsBAligned,
                    receptorAtomsA,
                    receptorAtomsB,
                    pocketNumbersA,
                    pocketNumbersB,
                    sphereCentersA,
                    sphereCentersBAligned
            ));
        }

        return regionAnalysis(
                rows,
                loopCaA,
                centroid(poseAtomsA),
                centroid(poseAtomsBAligned)
        );
    }

    private LoopRegionAnalysis.LoopRegionResidueRow row(
            AlignedResiduePair pair,
            ResidueContext residueA,
            ResidueContext residueB,
            RigidTransform transformBtoA,
            List<Point3D> poseAtomsA,
            List<Point3D> poseAtomsBAligned,
            List<NumberedAtom> receptorAtomsA,
            List<NumberedAtom> receptorAtomsB,
            Set<Integer> pocketNumbersA,
            Set<Integer> pocketNumbersB,
            List<Point3D> sphereCentersA,
            List<Point3D> sphereCentersBAligned
    ) {
        double caDisplacement = residueA.alphaCarbon().distance(
                transformBtoA.apply(residueB.alphaCarbon()));

        double backboneDisplacement = rmsdOfPairs(
                residueA.residue(),
                residueB.residue(),
                BACKBONE_ATOMS,
                transformBtoA
        );

        double sideChainCentroidDisplacement =
                residueA.sideChainCentroid().distance(
                        transformBtoA.apply(
                                residueB.sideChainCentroid()));

        Double sideChainRmsd = null;
        double sideChainSum = squaredDisplacementSum(
                residueA.sideChainAtoms(),
                residueB.sideChainAtoms(),
                transformBtoA
        );
        int sideChainPairs = sharedAtomCount(
                residueA.sideChainAtoms(),
                residueB.sideChainAtoms()
        );
        if (sideChainPairs > 0) {
            sideChainRmsd =
                    Math.sqrt(sideChainSum / sideChainPairs);
        }

        double minPoseA = nearestDistance(
                residueA.sideChainAtoms(),
                poseAtomsA,
                null
        );
        double minPoseB = nearestDistance(
                residueB.sideChainAtoms(),
                poseAtomsBAligned,
                transformBtoA
        );

        int burialA = burial(
                residueA,
                pair.queryResidueNumber(),
                receptorAtomsA
        );
        int burialB = burial(
                residueB,
                pair.candidateResidueNumber(),
                receptorAtomsB
        );

        double freeA = ShellFreeVolume.freeFraction(
                residueA.sideChainCentroid(),
                positionsExcluding(
                        receptorAtomsA,
                        pair.queryResidueNumber()
                ),
                options.probeRadiusAngstroms()
        );
        double freeB = ShellFreeVolume.freeFraction(
                residueB.sideChainCentroid(),
                positionsExcluding(
                        receptorAtomsB,
                        pair.candidateResidueNumber()
                ),
                options.probeRadiusAngstroms()
        );

        return new LoopRegionAnalysis.LoopRegionResidueRow(
                residueA.id(),
                residueB.id(),
                residueA.residue().getName(),
                residueB.residue().getName(),
                caDisplacement,
                backboneDisplacement,
                sideChainCentroidDisplacement,
                sideChainRmsd,
                minPoseA,
                minPoseB,
                minPoseA <= options.contactCutoffAngstroms(),
                minPoseB <= options.contactCutoffAngstroms(),
                pocketNumbersA.contains(pair.queryResidueNumber()),
                pocketNumbersB.contains(pair.candidateResidueNumber()),
                burialA,
                burialB,
                localCavityDisplacement(
                        residueA.sideChainCentroid(),
                        sphereCentersA,
                        sphereCentersBAligned
                ),
                freeB - freeA
        );
    }

    private LoopRegionAnalysis regionAnalysis(
            List<LoopRegionAnalysis.LoopRegionResidueRow> rows,
            List<Point3D> loopCaA,
            Point3D poseCentroidA,
            Point3D poseCentroidBAligned
    ) {
        if (loopCaA.isEmpty()) {
            return new LoopRegionAnalysis(
                    options.rangeStart(),
                    options.rangeEnd(),
                    rows,
                    null,
                    null,
                    null,
                    0.0,
                    LoopShiftVerdict.ORTHOGONAL_OR_NEGLIGIBLE,
                    "no aligned residue pairs in the range"
            );
        }

        Point3D loopCentroid = centroid(loopCaA);

        double distanceA = poseCentroidA.distance(loopCentroid);
        double distanceB = poseCentroidBAligned.distance(loopCentroid);

        double dx = loopCentroid.x() - poseCentroidA.x();
        double dy = loopCentroid.y() - poseCentroidA.y();
        double dz = loopCentroid.z() - poseCentroidA.z();
        double norm = Math.sqrt(dx * dx + dy * dy + dz * dz);

        double displacementX = poseCentroidBAligned.x()
                - poseCentroidA.x();
        double displacementY = poseCentroidBAligned.y()
                - poseCentroidA.y();
        double displacementZ = poseCentroidBAligned.z()
                - poseCentroidA.z();

        double toward = norm < DIRECTION_EPSILON
                ? 0.0
                : (displacementX * dx + displacementY * dy
                        + displacementZ * dz) / norm;

        LoopShiftVerdict verdict;
        if (toward > options.towardLoopSignificanceAngstroms()) {
            verdict = LoopShiftVerdict.POSE_SHIFTED_TOWARD_LOOP;
        } else if (toward < -options.towardLoopSignificanceAngstroms()) {
            verdict = LoopShiftVerdict.POSE_SHIFTED_AWAY_FROM_LOOP;
        } else {
            verdict = LoopShiftVerdict.ORTHOGONAL_OR_NEGLIGIBLE;
        }

        String reason = String.format(
                "pose displacement toward the loop centroid is %+.2f "
                        + "A (significance threshold %.2f A); pose "
                        + "centroid to loop centroid: A %.2f A, "
                        + "aligned B %.2f A",
                toward,
                options.towardLoopSignificanceAngstroms(),
                distanceA,
                distanceB
        );

        return new LoopRegionAnalysis(
                options.rangeStart(),
                options.rangeEnd(),
                rows,
                loopCentroid,
                distanceA,
                distanceB,
                toward,
                verdict,
                reason
        );
    }

    /**
     * Mean displacement of the A-side alpha spheres within the
     * locality cutoff of the residue centroid to their nearest
     * aligned B-side sphere; {@code null} when no such spheres exist.
     */
    private Double localCavityDisplacement(
            Point3D residueCentroid,
            List<Point3D> sphereCentersA,
            List<Point3D> sphereCentersBAligned
    ) {
        double sum = 0.0;
        int count = 0;

        for (Point3D sphereA : sphereCentersA) {
            if (sphereA.distance(residueCentroid)
                    > options.sphereLocalityCutoffAngstroms()) {
                continue;
            }

            double nearest = Double.MAX_VALUE;
            for (Point3D sphereB : sphereCentersBAligned) {
                nearest = Math.min(nearest, sphereA.distance(sphereB));
            }

            if (nearest < Double.MAX_VALUE) {
                sum += nearest;
                count++;
            }
        }

        return count == 0 ? null : sum / count;
    }

    private int burial(
            ResidueContext residue,
            int residueNumber,
            List<NumberedAtom> receptorAtoms
    ) {
        int count = 0;
        double radiusSquared = options.burialRadiusAngstroms()
                * options.burialRadiusAngstroms();

        for (NumberedAtom atom : receptorAtoms) {
            if (atom.residueNumber() == residueNumber) {
                continue;
            }

            if (residue.sideChainCentroid().distanceSquared(
                    atom.position()) <= radiusSquared) {
                count++;
            }
        }

        return count;
    }

    private static List<Point3D> positionsExcluding(
            List<NumberedAtom> receptorAtoms,
            int residueNumber
    ) {
        return receptorAtoms.stream()
                .filter(atom -> atom.residueNumber() != residueNumber)
                .map(NumberedAtom::position)
                .toList();
    }

    /**
     * RMS displacement over same-named atoms of the given name set
     * present on both residues; 0.0 when there are none.
     */
    private static double rmsdOfPairs(
            Residue residueA,
            Residue residueB,
            Set<String> atomNames,
            RigidTransform transformBtoA
    ) {
        double sum = 0.0;
        int count = 0;

        for (Atom atomA : residueA.getAtoms()) {
            if (atomA == null || !atomA.isHeavyAtom()
                    || !atomNames.contains(atomA.getName())) {
                continue;
            }

            var atomB = residueB.findAtom(atomA.getName());

            if (atomB.isEmpty() || !atomB.get().isHeavyAtom()) {
                continue;
            }

            sum += atomA.getPosition().distanceSquared(
                    transformBtoA.apply(atomB.get().getPosition()));
            count++;
        }

        return count == 0 ? 0.0 : Math.sqrt(sum / count);
    }

    private static double squaredDisplacementSum(
            List<Atom> atomsA,
            List<Atom> atomsB,
            RigidTransform transformBtoA
    ) {
        double sum = 0.0;

        for (Atom atomA : atomsA) {
            for (Atom atomB : atomsB) {
                if (atomA.getName().equals(atomB.getName())) {
                    sum += atomA.getPosition().distanceSquared(
                            transformBtoA.apply(atomB.getPosition()));
                }
            }
        }

        return sum;
    }

    private static int sharedAtomCount(
            List<Atom> atomsA,
            List<Atom> atomsB
    ) {
        int count = 0;

        for (Atom atomA : atomsA) {
            for (Atom atomB : atomsB) {
                if (atomA.getName().equals(atomB.getName())) {
                    count++;
                }
            }
        }

        return count;
    }

    private static double nearestDistance(
            List<Atom> sideChainAtoms,
            List<Point3D> poseAtoms,
            RigidTransform transformOrNull
    ) {
        double nearest = Double.MAX_VALUE;

        for (Atom atom : sideChainAtoms) {
            Point3D position = transformOrNull == null
                    ? atom.getPosition()
                    : transformOrNull.apply(atom.getPosition());

            for (Point3D poseAtom : poseAtoms) {
                nearest = Math.min(
                        nearest,
                        position.distance(poseAtom)
                );
            }
        }

        return nearest;
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

    private static Map<Integer, ResidueContext> residuesByNumber(
            Structure receptor
    ) {
        Map<Integer, ResidueContext> index = new LinkedHashMap<>();

        for (Chain chain : receptor.getChains()) {
            for (Residue residue : chain.residues()) {
                List<Atom> sideChain = sideChainAtoms(residue);

                index.putIfAbsent(
                        residue.getNumber(),
                        new ResidueContext(
                                new ResidueId(
                                        chain.id(),
                                        residue.getNumber(),
                                        residue.getInsertionCode()
                                ),
                                residue,
                                residue.getAlphaCarbonPosition()
                                        .orElse(null),
                                sideChain,
                                sideChainCentroid(sideChain)
                        )
                );
            }
        }

        return index;
    }

    /**
     * Side-chain heavy atoms (N/CA/C/O/OXT excluded); falls back to
     * the CA atom when the residue has none (e.g. GLY).
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

    private static Point3D sideChainCentroid(List<Atom> sideChain) {
        if (sideChain.isEmpty()) {
            return null;
        }

        double x = 0.0;
        double y = 0.0;
        double z = 0.0;

        for (Atom atom : sideChain) {
            x += atom.getPosition().x();
            y += atom.getPosition().y();
            z += atom.getPosition().z();
        }

        double n = sideChain.size();

        return new Point3D(x / n, y / n, z / n);
    }

    private static List<NumberedAtom> receptorAtoms(
            Structure receptor
    ) {
        List<NumberedAtom> atoms = new ArrayList<>();

        for (Chain chain : receptor.getChains()) {
            for (Residue residue : chain.residues()) {
                for (Atom atom : residue.getAtoms()) {
                    if (atom == null || !atom.isHeavyAtom()) {
                        continue;
                    }

                    atoms.add(new NumberedAtom(
                            residue.getNumber(),
                            atom.getPosition()
                    ));
                }
            }
        }

        return atoms;
    }

    private static List<Point3D> sphereCenters(Pocket pocket) {
        return pocket.alphaSphereSet()
                .map(AlphaSphereSet::spheres)
                .orElse(List.of())
                .stream()
                .map(AlphaSphere::center)
                .toList();
    }

    private static Set<Integer> pocketResidueNumbers(Pocket pocket) {
        return pocket.residues().stream()
                .map(ResidueId::residueNumber)
                .collect(Collectors.toSet());
    }

    private static List<Point3D> heavyAtomPositions(Ligand pose) {
        return pose.structure().getChains().stream()
                .flatMap(chain -> chain.residues().stream())
                .flatMap(residue -> residue.getAtoms().stream())
                .filter(Atom::isHeavyAtom)
                .map(Atom::getPosition)
                .toList();
    }

    private record ResidueContext(
            ResidueId id,
            Residue residue,
            Point3D alphaCarbon,
            List<Atom> sideChainAtoms,
            Point3D sideChainCentroid
    ) {
    }

    private record NumberedAtom(
            int residueNumber,
            Point3D position
    ) {
    }
}
