package totah.lab.athena.pocket.architecture;

import totah.lab.athena.pocket.compare.KabschRigidPointAligner;
import totah.lab.athena.sequence.AlignedResiduePair;
import totah.lab.athena.sequence.NeedlemanWunschSequenceAligner;
import totah.lab.athena.sequence.SequenceAlignment;
import totah.lab.athena.sequence.StructureSequences;
import totah.lab.gaia.geometry.Point3D;
import totah.lab.gaia.geometry.RigidTransform;
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
 * Aligns two receptor backbones (sequence-seeded Kabsch over the CA
 * atoms of all aligned residue pairs, B onto A) and measures the
 * resulting displacements over the pocket region. Consistent with the
 * rest of the alignment machinery, residues are keyed by residue
 * number within a receptor; duplicate numbering across chains
 * resolves to the first chain in structure order.
 */
public final class BackboneArchitectureAnalyzer {

    private static final Set<String> BACKBONE_ATOMS =
            Set.of("N", "CA", "C", "O");

    private static final int MINIMUM_FIT_PAIRS = 3;

    private static final int MINIMUM_SEGMENT_LENGTH = 3;

    public BackboneArchitectureComparison compare(
            Structure receptorA,
            Pocket pocketA,
            Structure receptorB,
            Pocket pocketB
    ) {
        Objects.requireNonNull(receptorA, "receptorA");
        Objects.requireNonNull(pocketA, "pocketA");
        Objects.requireNonNull(receptorB, "receptorB");
        Objects.requireNonNull(pocketB, "pocketB");

        SequenceAlignment alignment =
                new NeedlemanWunschSequenceAligner().align(
                        StructureSequences.sequenceResidues(receptorA),
                        StructureSequences.sequenceResidues(receptorB)
                );

        Map<Integer, ResidueContext> residuesA =
                residuesByNumber(receptorA);
        Map<Integer, ResidueContext> residuesB =
                residuesByNumber(receptorB);

        List<AlignedResiduePair> caPairs = new ArrayList<>();
        List<Point3D> caA = new ArrayList<>();
        List<Point3D> caB = new ArrayList<>();

        for (AlignedResiduePair pair : alignment.pairs()) {
            ResidueContext residueA =
                    residuesA.get(pair.queryResidueNumber());
            ResidueContext residueB =
                    residuesB.get(pair.candidateResidueNumber());

            if (residueA == null || residueB == null
                    || residueA.alphaCarbon() == null
                    || residueB.alphaCarbon() == null) {
                continue;
            }

            caPairs.add(pair);
            caA.add(residueA.alphaCarbon());
            caB.add(residueB.alphaCarbon());
        }

        if (caPairs.size() < MINIMUM_FIT_PAIRS) {
            throw new IllegalArgumentException(
                    "Backbone alignment requires at least "
                            + MINIMUM_FIT_PAIRS
                            + " aligned residue pairs with CA atoms, "
                            + "got " + caPairs.size()
            );
        }

        RigidTransform transformBtoA =
                new KabschRigidPointAligner().align(caB, caA);

        Set<Integer> pocketNumbersA = pocketResidueNumbers(pocketA);
        Set<Integer> pocketNumbersB = pocketResidueNumbers(pocketB);

        List<ResidueDisplacementRecord> profile = new ArrayList<>();

        double caSquaredSum = 0.0;
        double backboneSquaredSum = 0.0;
        double heavySquaredSum = 0.0;
        int backboneAtomPairs = 0;
        int heavyAtomPairs = 0;

        List<Double> segmentCaDisplacements = new ArrayList<>();

        for (int index = 0; index < caPairs.size(); index++) {
            AlignedResiduePair pair = caPairs.get(index);
            ResidueContext residueA =
                    residuesA.get(pair.queryResidueNumber());
            ResidueContext residueB =
                    residuesB.get(pair.candidateResidueNumber());

            double caDisplacement = residueA.alphaCarbon().distance(
                    transformBtoA.apply(residueB.alphaCarbon()));

            segmentCaDisplacements.add(caDisplacement);

            boolean inPocketRegion = pocketNumbersA.contains(
                    pair.queryResidueNumber())
                    || pocketNumbersB.contains(
                            pair.candidateResidueNumber());

            if (!inPocketRegion) {
                continue;
            }

            DisplacementSums sums = displacementSums(
                    residueA.residue(),
                    residueB.residue(),
                    transformBtoA
            );

            caSquaredSum += caDisplacement * caDisplacement;
            backboneSquaredSum += sums.backboneSquaredSum();
            heavySquaredSum += sums.heavySquaredSum();
            backboneAtomPairs += sums.backboneAtomPairs();
            heavyAtomPairs += sums.heavyAtomPairs();

            profile.add(new ResidueDisplacementRecord(
                    residueA.id(),
                    residueB.id(),
                    residueA.residue().getName(),
                    residueB.residue().getName(),
                    caDisplacement,
                    sums.backboneAtomPairs() == 0
                            ? 0.0
                            : Math.sqrt(sums.backboneSquaredSum()
                                    / sums.backboneAtomPairs()),
                    sums.heavyAtomPairs() == 0
                            ? 0.0
                            : Math.sqrt(sums.heavySquaredSum()
                                    / sums.heavyAtomPairs())
            ));
        }

        profile.sort(Comparator.comparingDouble(
                ResidueDisplacementRecord::caDisplacement
        ).reversed());

        int pocketRegionPairs = profile.size();

        List<BackboneArchitectureComparison.ResidueDisplacement>
                residueProfile = profile.stream()
                .map(record ->
                        new BackboneArchitectureComparison
                                .ResidueDisplacement(
                                record.residueA(),
                                record.residueB(),
                                record.residueNameA(),
                                record.residueNameB(),
                                record.caDisplacement(),
                                record.backboneDisplacement(),
                                record.heavyAtomDisplacement()
                        ))
                .toList();

        return new BackboneArchitectureComparison(
                transformBtoA,
                caPairs.size(),
                pocketRegionPairs,
                pocketRegionPairs == 0
                        ? 0.0
                        : Math.sqrt(caSquaredSum / pocketRegionPairs),
                backboneAtomPairs == 0
                        ? 0.0
                        : Math.sqrt(
                                backboneSquaredSum / backboneAtomPairs),
                heavyAtomPairs == 0
                        ? 0.0
                        : Math.sqrt(heavySquaredSum / heavyAtomPairs),
                residueProfile,
                segments(caPairs, segmentCaDisplacements)
        );
    }

    /**
     * Contiguous aligned segments (both residue numbers incrementing
     * by one) of at least {@link #MINIMUM_SEGMENT_LENGTH} residues,
     * with mean CA displacement, sorted descending.
     */
    private static List<BackboneArchitectureComparison
            .SegmentDisplacement> segments(
            List<AlignedResiduePair> pairs,
            List<Double> caDisplacements
    ) {
        List<BackboneArchitectureComparison.SegmentDisplacement>
                segments = new ArrayList<>();

        int segmentStart = 0;

        for (int index = 1; index <= pairs.size(); index++) {
            boolean contiguous = index < pairs.size()
                    && pairs.get(index).queryResidueNumber()
                            == pairs.get(index - 1).queryResidueNumber() + 1
                    && pairs.get(index).candidateResidueNumber()
                            == pairs.get(index - 1)
                                    .candidateResidueNumber() + 1;

            if (contiguous) {
                continue;
            }

            int length = index - segmentStart;

            if (length >= MINIMUM_SEGMENT_LENGTH) {
                double mean = 0.0;
                for (int segmentIndex = segmentStart;
                        segmentIndex < index; segmentIndex++) {
                    mean += caDisplacements.get(segmentIndex);
                }
                mean /= length;

                segments.add(
                        new BackboneArchitectureComparison
                                .SegmentDisplacement(
                                pairs.get(segmentStart)
                                        .queryResidueNumber(),
                                pairs.get(index - 1)
                                        .queryResidueNumber(),
                                pairs.get(segmentStart)
                                        .candidateResidueNumber(),
                                pairs.get(index - 1)
                                        .candidateResidueNumber(),
                                length,
                                mean
                        ));
            }

            segmentStart = index;
        }

        segments.sort(Comparator.comparingDouble(
                BackboneArchitectureComparison
                        .SegmentDisplacement::meanCaDisplacement
        ).reversed());

        return List.copyOf(segments);
    }

    private static DisplacementSums displacementSums(
            Residue residueA,
            Residue residueB,
            RigidTransform transformBtoA
    ) {
        double backboneSquaredSum = 0.0;
        double heavySquaredSum = 0.0;
        int backboneAtomPairs = 0;
        int heavyAtomPairs = 0;

        for (Atom atomA : residueA.getAtoms()) {
            if (atomA == null || !atomA.isHeavyAtom()) {
                continue;
            }

            var atomB = residueB.findAtom(atomA.getName());

            if (atomB.isEmpty() || !atomB.get().isHeavyAtom()) {
                continue;
            }

            double distanceSquared = atomA.getPosition()
                    .distanceSquared(transformBtoA.apply(
                            atomB.get().getPosition()));

            heavySquaredSum += distanceSquared;
            heavyAtomPairs++;

            if (BACKBONE_ATOMS.contains(atomA.getName())) {
                backboneSquaredSum += distanceSquared;
                backboneAtomPairs++;
            }
        }

        return new DisplacementSums(
                backboneSquaredSum,
                heavySquaredSum,
                backboneAtomPairs,
                heavyAtomPairs
        );
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
                                        .orElse(null)
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

    private record ResidueContext(
            ResidueId id,
            Residue residue,
            Point3D alphaCarbon
    ) {
    }

    private record ResidueDisplacementRecord(
            ResidueId residueA,
            ResidueId residueB,
            String residueNameA,
            String residueNameB,
            double caDisplacement,
            double backboneDisplacement,
            double heavyAtomDisplacement
    ) {
    }

    private record DisplacementSums(
            double backboneSquaredSum,
            double heavySquaredSum,
            int backboneAtomPairs,
            int heavyAtomPairs
    ) {
    }
}
