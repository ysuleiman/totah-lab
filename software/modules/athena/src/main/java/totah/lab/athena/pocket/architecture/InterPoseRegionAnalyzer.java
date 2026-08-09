package totah.lab.athena.pocket.architecture;

import totah.lab.athena.pocket.compare.residue.ResidueChemistry;
import totah.lab.athena.pocket.compare.residue.ResidueChemistryClassifier;
import totah.lab.athena.sequence.AlignedResiduePair;
import totah.lab.athena.sequence.NeedlemanWunschSequenceAligner;
import totah.lab.athena.sequence.SequenceAlignment;
import totah.lab.athena.sequence.StructureSequences;
import totah.lab.gaia.geometry.Point3D;
import totah.lab.gaia.geometry.RigidTransform;
import totah.lab.gaia.molecule.Ligand;
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

/**
 * Finds the receptor residues lying between two aligned poses and
 * reports their aligned displacements, pose distances, free-volume
 * difference and chemistry relationship. Region definition and row
 * ordering are documented on {@link InterPoseRegionOptions} and
 * {@link InterPoseRegionAnalysis}. Guardrail: this ranks geometric
 * features near the pose-transition region; it does not establish
 * mechanism.
 */
public final class InterPoseRegionAnalyzer {

    private static final Set<String> BACKBONE_ATOMS =
            Set.of("N", "CA", "C", "O", "OXT");

    private final InterPoseRegionOptions options;
    private final ResidueChemistryClassifier chemistryClassifier =
            new ResidueChemistryClassifier();

    public InterPoseRegionAnalyzer() {
        this(InterPoseRegionOptions.defaults());
    }

    public InterPoseRegionAnalyzer(InterPoseRegionOptions options) {
        this.options = Objects.requireNonNull(options, "options");
    }

    public InterPoseRegionAnalysis analyze(
            Structure receptorA,
            Structure receptorB,
            RigidTransform transformBtoA,
            Ligand poseA,
            Ligand poseB
    ) {
        Objects.requireNonNull(receptorA, "receptorA");
        Objects.requireNonNull(receptorB, "receptorB");
        Objects.requireNonNull(transformBtoA, "transformBtoA");
        Objects.requireNonNull(poseA, "poseA");
        Objects.requireNonNull(poseB, "poseB");

        List<Point3D> poseAtomsA = heavyAtomPositions(poseA);
        List<Point3D> poseAtomsBAligned =
                transformBtoA.apply(heavyAtomPositions(poseB));

        if (poseAtomsA.isEmpty() || poseAtomsBAligned.isEmpty()) {
            throw new IllegalArgumentException(
                    "Both poses must contain heavy atoms"
            );
        }

        Point3D centroidA = centroid(poseAtomsA);
        Point3D centroidB = centroid(poseAtomsBAligned);

        SequenceAlignment alignment =
                new NeedlemanWunschSequenceAligner().align(
                        StructureSequences.sequenceResidues(receptorA),
                        StructureSequences.sequenceResidues(receptorB)
                );

        Map<Integer, Integer> bNumberByANumber = new LinkedHashMap<>();
        for (AlignedResiduePair pair : alignment.pairs()) {
            bNumberByANumber.putIfAbsent(
                    pair.queryResidueNumber(),
                    pair.candidateResidueNumber()
            );
        }

        Map<Integer, ResidueContext> residuesB =
                residuesByNumber(receptorB);

        List<InterPoseRegionAnalysis.InterPoseRegionResidueRow> rows =
                new ArrayList<>();

        for (Chain chain : receptorA.getChains()) {
            for (Residue residue : chain.residues()) {
                if (!inRegion(
                        residue,
                        poseAtomsA,
                        poseAtomsBAligned,
                        centroidA,
                        centroidB
                )) {
                    continue;
                }

                rows.add(row(
                        new ResidueId(
                                chain.id(),
                                residue.getNumber(),
                                residue.getInsertionCode()
                        ),
                        residue,
                        bNumberByANumber.get(residue.getNumber()),
                        residuesB,
                        transformBtoA,
                        poseAtomsA,
                        poseAtomsBAligned,
                        receptorA,
                        receptorB
                ));
            }
        }

        rows.sort(Comparator
                .comparingDouble(
                        InterPoseRegionAnalysis.InterPoseRegionResidueRow
                                ::rankingDistance)
                .thenComparing(Comparator.comparingDouble(
                        InterPoseRegionAnalysis.InterPoseRegionResidueRow
                                ::rankingDisplacement).reversed()));

        return new InterPoseRegionAnalysis(rows);
    }

    private InterPoseRegionAnalysis.InterPoseRegionResidueRow row(
            ResidueId residueIdA,
            Residue residueA,
            Integer residueNumberB,
            Map<Integer, ResidueContext> residuesB,
            RigidTransform transformBtoA,
            List<Point3D> poseAtomsA,
            List<Point3D> poseAtomsBAligned,
            Structure receptorA,
            Structure receptorB
    ) {
        ResidueContext residueB = residueNumberB == null
                ? null
                : residuesB.get(residueNumberB);

        Double caDisplacement = null;
        Double backboneDisplacement = null;
        Double sideChainDisplacement = null;
        ResidueId residueIdB = null;
        String residueNameB = null;
        InterPoseRegionAnalysis.ChemistryDifference chemistry;

        if (residueB != null) {
            residueIdB = residueB.id();
            residueNameB = residueB.residue().getName();

            if (residueA.getAlphaCarbonPosition().isPresent()
                    && residueB.alphaCarbon() != null) {
                caDisplacement = residueA.getAlphaCarbonPosition()
                        .orElseThrow()
                        .distance(transformBtoA.apply(
                                residueB.alphaCarbon()));
            }

            backboneDisplacement = backboneDisplacement(
                    residueA,
                    residueB.residue(),
                    transformBtoA
            );

            Point3D sideChainA = sideChainCentroid(residueA);

            if (sideChainA != null
                    && residueB.sideChainCentroid() != null) {
                sideChainDisplacement = sideChainA.distance(
                        transformBtoA.apply(
                                residueB.sideChainCentroid()));
            }

            chemistry = chemistry(
                    residueA.getName(),
                    residueB.residue().getName()
            );
        } else {
            chemistry =
                    InterPoseRegionAnalysis.ChemistryDifference.UNPAIRED;
        }

        return new InterPoseRegionAnalysis.InterPoseRegionResidueRow(
                residueIdA,
                residueA.getName(),
                residueIdB,
                residueNameB,
                caDisplacement,
                backboneDisplacement,
                sideChainDisplacement,
                nearestDistance(residueA, poseAtomsA, null),
                nearestDistance(
                        residueA,
                        poseAtomsBAligned,
                        null
                ),
                freeVolumeDifference(
                        residueA,
                        residueB,
                        receptorA,
                        receptorB
                ),
                chemistry
        );
    }

    private InterPoseRegionAnalysis.ChemistryDifference chemistry(
            String nameA,
            String nameB
    ) {
        if (nameA.trim().equalsIgnoreCase(nameB.trim())) {
            return InterPoseRegionAnalysis.ChemistryDifference
                    .SAME_RESIDUE;
        }

        ResidueChemistry chemistryA =
                chemistryClassifier.classifyName(nameA);
        ResidueChemistry chemistryB =
                chemistryClassifier.classifyName(nameB);

        return chemistryA == chemistryB
                ? InterPoseRegionAnalysis.ChemistryDifference
                        .SAME_CHEMISTRY
                : InterPoseRegionAnalysis.ChemistryDifference
                        .DIFFERENT_CHEMISTRY;
    }

    /**
     * Free-volume fraction at the B side-chain centroid (own frame)
     * minus at the A centroid, each excluding the residue's own
     * atoms; 0.0 when either residue has no side-chain centroid.
     */
    private double freeVolumeDifference(
            Residue residueA,
            ResidueContext residueB,
            Structure receptorA,
            Structure receptorB
    ) {
        Point3D centroidA = sideChainCentroid(residueA);

        if (centroidA == null || residueB == null
                || residueB.sideChainCentroid() == null) {
            return 0.0;
        }

        double freeA = ShellFreeVolume.freeFraction(
                centroidA,
                heavyPositionsExcluding(
                        receptorA,
                        residueA.getNumber()
                ),
                options.probeRadiusAngstroms()
        );
        double freeB = ShellFreeVolume.freeFraction(
                residueB.sideChainCentroid(),
                heavyPositionsExcluding(
                        receptorB,
                        residueB.residue().getNumber()
                ),
                options.probeRadiusAngstroms()
        );

        return freeB - freeA;
    }

    private boolean inRegion(
            Residue residue,
            List<Point3D> poseAtomsA,
            List<Point3D> poseAtomsBAligned,
            Point3D centroidA,
            Point3D centroidB
    ) {
        double regionSquared = options.regionRadiusAngstroms()
                * options.regionRadiusAngstroms();

        for (Atom atom : residue.getAtoms()) {
            if (atom == null || !atom.isHeavyAtom()) {
                continue;
            }

            Point3D position = atom.getPosition();

            for (Point3D poseAtom : poseAtomsA) {
                if (position.distanceSquared(poseAtom)
                        <= regionSquared) {
                    return true;
                }
            }

            for (Point3D poseAtom : poseAtomsBAligned) {
                if (position.distanceSquared(poseAtom)
                        <= regionSquared) {
                    return true;
                }
            }

            if (distanceToSegment(position, centroidA, centroidB)
                    <= options.corridorRadiusAngstroms()) {
                return true;
            }
        }

        return false;
    }

    private static double distanceToSegment(
            Point3D point,
            Point3D segmentStart,
            Point3D segmentEnd
    ) {
        double dx = segmentEnd.x() - segmentStart.x();
        double dy = segmentEnd.y() - segmentStart.y();
        double dz = segmentEnd.z() - segmentStart.z();

        double lengthSquared =
                dx * dx + dy * dy + dz * dz;

        if (lengthSquared == 0.0) {
            return point.distance(segmentStart);
        }

        double t = ((point.x() - segmentStart.x()) * dx
                + (point.y() - segmentStart.y()) * dy
                + (point.z() - segmentStart.z()) * dz)
                / lengthSquared;
        t = Math.max(0.0, Math.min(1.0, t));

        return point.distance(new Point3D(
                segmentStart.x() + t * dx,
                segmentStart.y() + t * dy,
                segmentStart.z() + t * dz
        ));
    }

    private static Double backboneDisplacement(
            Residue residueA,
            Residue residueB,
            RigidTransform transformBtoA
    ) {
        double sum = 0.0;
        int count = 0;

        for (Atom atomA : residueA.getAtoms()) {
            if (atomA == null || !atomA.isHeavyAtom()
                    || !BACKBONE_ATOMS.contains(atomA.getName())) {
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

        return count == 0 ? null : Math.sqrt(sum / count);
    }

    private static double nearestDistance(
            Residue residue,
            List<Point3D> poseAtoms,
            RigidTransform transformOrNull
    ) {
        double nearest = Double.MAX_VALUE;

        for (Atom atom : residue.getAtoms()) {
            if (atom == null || !atom.isHeavyAtom()) {
                continue;
            }

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

    private static Point3D sideChainCentroid(Residue residue) {
        List<Atom> sideChain = residue.getAtoms().stream()
                .filter(Objects::nonNull)
                .filter(Atom::isHeavyAtom)
                .filter(atom -> !BACKBONE_ATOMS.contains(atom.getName()))
                .toList();

        if (sideChain.isEmpty()) {
            return residue.getAlphaCarbonPosition().orElse(null);
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

    private static List<Point3D> heavyPositionsExcluding(
            Structure receptor,
            int residueNumber
    ) {
        List<Point3D> positions = new ArrayList<>();

        for (Chain chain : receptor.getChains()) {
            for (Residue residue : chain.residues()) {
                if (residue.getNumber() == residueNumber) {
                    continue;
                }

                for (Atom atom : residue.getAtoms()) {
                    if (atom != null && atom.isHeavyAtom()) {
                        positions.add(atom.getPosition());
                    }
                }
            }
        }

        return positions;
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
                                residue.getAlphaCarbonPosition()
                                        .orElse(null),
                                sideChainCentroid(residue)
                        )
                );
            }
        }

        return index;
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

    private record ResidueContext(
            ResidueId id,
            Residue residue,
            Point3D alphaCarbon,
            Point3D sideChainCentroid
    ) {
    }
}
