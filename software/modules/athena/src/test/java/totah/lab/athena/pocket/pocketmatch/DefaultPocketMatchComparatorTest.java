package totah.lab.athena.pocket.pocketmatch;

import org.junit.jupiter.api.Test;
import totah.lab.gaia.geometry.Point3D;
import totah.lab.gaia.geometry.RigidTransform;
import totah.lab.gaia.pocket.Pocket;
import totah.lab.gaia.structure.Atom;
import totah.lab.gaia.structure.Chain;
import totah.lab.gaia.structure.Residue;
import totah.lab.gaia.structure.Structure;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class DefaultPocketMatchComparatorTest {

    private final DefaultPocketMatchSignatureFactory factory =
            new DefaultPocketMatchSignatureFactory();

    private final DefaultPocketMatchComparator comparator =
            new DefaultPocketMatchComparator();

    @Test
    void twoPointerMatchingConsumesEachElementAtMostOnce() {
        double[] first = {1.0, 1.2};
        double[] second = {1.1};

        // |1.0 - 1.1| <= 0.15 matches; 1.2 cannot rematch 1.1
        assertThat(DefaultPocketMatchComparator
                .countMatches(first, second, 0.15))
                .isEqualTo(1);
    }

    @Test
    void twoPointerMatchingAdvancesPastUnmatchableElements() {
        double[] first = {1.0, 5.0};
        double[] second = {2.0};

        assertThat(DefaultPocketMatchComparator
                .countMatches(first, second, 0.5))
                .isEqualTo(0);

        double[] close = {1.4, 4.6};
        assertThat(DefaultPocketMatchComparator
                .countMatches(first, close, 0.5))
                .isEqualTo(2);
    }

    @Test
    void identicalSignatureScoresOneInEveryMeasure() {
        PocketMatchSignature signature = sampleSignature();

        PocketMatchComparison comparison =
                comparator.compare(signature, signature);

        assertThat(comparison.matchedDistanceCount())
                .isEqualTo(signature.totalDistanceCount());
        assertThat(comparison.symmetricScore())
                .isCloseTo(1.0, within(1.0e-12));
        assertThat(comparison.firstCoverage())
                .isCloseTo(1.0, within(1.0e-12));
        assertThat(comparison.secondCoverage())
                .isCloseTo(1.0, within(1.0e-12));
    }

    @Test
    void symmetricScoreIsSymmetricAndCoveragesSwapDirections() {
        PocketMatchSignature small = subsetSignature(3);
        PocketMatchSignature large = subsetSignature(9);

        PocketMatchComparison forward = comparator.compare(small, large);
        PocketMatchComparison reverse = comparator.compare(large, small);

        assertThat(forward.symmetricScore())
                .isCloseTo(reverse.symmetricScore(), within(1.0e-12));
        assertThat(forward.firstCoverage())
                .isCloseTo(reverse.secondCoverage(), within(1.0e-12));
        assertThat(forward.secondCoverage())
                .isCloseTo(reverse.firstCoverage(), within(1.0e-12));
    }

    @Test
    void directionalCoverageExposesSubsetContainment() {
        // the three-residue pocket shares exact coordinates with a
        // subset of the nine-residue pocket
        PocketMatchSignature small = subsetSignature(3);
        PocketMatchSignature large = subsetSignature(9);

        PocketMatchComparison comparison =
                comparator.compare(small, large);

        assertThat(comparison.firstCoverage())
                .isCloseTo(1.0, within(1.0e-9));
        assertThat(comparison.secondCoverage())
                .isLessThan(0.6);
        assertThat(comparison.symmetricScore())
                .isLessThan(comparison.firstCoverage());
    }

    @Test
    void scoresStayWithinUnitInterval() {
        Random random = new Random(42);
        PocketMatchSignature first = randomSignature(random, 8);
        PocketMatchSignature second = randomSignature(random, 12);

        PocketMatchComparison comparison =
                comparator.compare(first, second);

        assertThat(comparison.symmetricScore()).isBetween(0.0, 1.0);
        assertThat(comparison.firstCoverage()).isBetween(0.0, 1.0);
        assertThat(comparison.secondCoverage()).isBetween(0.0, 1.0);
    }

    @Test
    void emptySignaturesCompareSafelyToZero() {
        PocketMatchSignature empty = emptySignature();

        PocketMatchComparison bothEmpty =
                comparator.compare(empty, empty);
        assertThat(bothEmpty.matchedDistanceCount()).isZero();
        assertThat(bothEmpty.symmetricScore()).isZero();
        assertThat(bothEmpty.firstCoverage()).isZero();
        assertThat(bothEmpty.secondCoverage()).isZero();

        PocketMatchComparison oneEmpty =
                comparator.compare(empty, sampleSignature());
        assertThat(oneEmpty.symmetricScore()).isZero();
        assertThat(oneEmpty.firstCoverage()).isZero();
        assertThat(oneEmpty.secondCoverage()).isZero();
    }

    @Test
    void scoresAreInvariantUnderRigidRotationAndTranslation() {
        Structure structure = sampleStructure();
        Pocket pocket = PocketMatchTestFixtures
                .pocketOfResidueNumbers(structure, 1, 2, 3, 4);

        PocketMatchSignature original =
                factory.describe(structure, pocket);
        PocketMatchSignature moved = factory.describe(
                transformed(structure, sampleRigidTransform()),
                pocket
        );

        PocketMatchComparison comparison =
                comparator.compare(original, moved);

        assertThat(comparison.symmetricScore())
                .isCloseTo(1.0, within(1.0e-9));
        assertThat(comparison.firstCoverage())
                .isCloseTo(1.0, within(1.0e-9));
        assertThat(comparison.secondCoverage())
                .isCloseTo(1.0, within(1.0e-9));
    }

    @Test
    void smallCoordinateNoiseDecreasesTheScoreGradually() {
        Structure structure = sampleStructure();
        Pocket pocket = PocketMatchTestFixtures
                .pocketOfResidueNumbers(structure, 1, 2, 3, 4);
        PocketMatchSignature original =
                factory.describe(structure, pocket);

        double previous = 1.0;
        for (double sigma : new double[]{0.1, 0.3, 0.8}) {
            PocketMatchSignature noisy = factory.describe(
                    perturbed(structure, sigma),
                    pocket
            );
            double score =
                    comparator.compare(original, noisy).symmetricScore();
            assertThat(score)
                    .as("score at noise sigma=%s", sigma)
                    .isLessThanOrEqualTo(previous + 1.0e-9);
            previous = score;
        }
        assertThat(previous).isLessThan(1.0);
    }

    @Test
    void randomizingChemistryAtFixedCoordinatesDecreasesTheScore() {
        Structure structure = sampleStructure();
        Pocket pocket = PocketMatchTestFixtures
                .pocketOfResidueNumbers(structure, 1, 2, 3, 4);
        PocketMatchSignature original =
                factory.describe(structure, pocket);

        // identical coordinates, different residue identities
        Structure relabeled = relabeled(
                structure,
                List.of("PHE", "LYS", "ASP", "SER")
        );
        PocketMatchSignature chemistryShuffled =
                factory.describe(relabeled, pocket);

        PocketMatchComparison comparison =
                comparator.compare(original, chemistryShuffled);

        assertThat(comparison.symmetricScore()).isLessThan(1.0);
    }

    @Test
    void largerToleranceMatchesMoreDistances() {
        PocketMatchSignature first = sampleSignature();
        PocketMatchSignature second = factory.describe(
                perturbed(sampleStructure(), 0.4),
                PocketMatchTestFixtures.pocketOfResidueNumbers(
                        sampleStructure(), 1, 2, 3, 4)
        );

        int strict = new DefaultPocketMatchComparator(
                new PocketMatchConfiguration(0.1))
                .compare(first, second)
                .matchedDistanceCount();
        int loose = new DefaultPocketMatchComparator(
                new PocketMatchConfiguration(1.0))
                .compare(first, second)
                .matchedDistanceCount();

        assertThat(loose).isGreaterThan(strict);
    }

    @Test
    void comparisonEchoesTheConfiguredTolerance() {
        PocketMatchComparison comparison =
                new DefaultPocketMatchComparator(
                        new PocketMatchConfiguration(0.25))
                        .compare(sampleSignature(), sampleSignature());

        assertThat(comparison.distanceToleranceAngstroms())
                .isEqualTo(0.25);
    }

    private PocketMatchSignature sampleSignature() {
        Structure structure = sampleStructure();
        return factory.describe(
                structure,
                PocketMatchTestFixtures.pocketOfResidueNumbers(
                        structure, 1, 2, 3, 4)
        );
    }

    /**
     * A pocket over the first {@code residueCount} residues of a shared
     * nine-residue structure, so smaller signatures are exact subsets
     * of larger ones.
     */
    private PocketMatchSignature subsetSignature(int residueCount) {
        String[] names = {
                "ALA", "LYS", "ASP", "TYR", "CYS",
                "VAL", "ARG", "GLU", "PHE"
        };
        List<Residue> residues = new ArrayList<>();
        for (int index = 0; index < names.length; index++) {
            residues.add(PocketMatchTestFixtures.alanine(
                    index + 1,
                    (index % 3) * 5.0,
                    (index / 3) * 5.0,
                    index * 1.5
            ).toBuilder().name(names[index]).build());
        }
        Structure structure = new Structure(
                List.of(new Chain("A", residues)));
        int[] numbers = new int[residueCount];
        for (int index = 0; index < residueCount; index++) {
            numbers[index] = index + 1;
        }
        return factory.describe(
                structure,
                PocketMatchTestFixtures.pocketOfResidueNumbers(
                        structure, numbers)
        );
    }

    private static Structure sampleStructure() {
        return PocketMatchTestFixtures.structureOf(
                PocketMatchTestFixtures.alanine(1, 0.0, 0.0, 0.0),
                PocketMatchTestFixtures.residueWithoutBetaCarbon(
                        "LYS", 2, 5.0, 1.0, 0.0),
                PocketMatchTestFixtures.residueWithoutBetaCarbon(
                        "ASP", 3, 1.0, 5.0, 2.0),
                PocketMatchTestFixtures.residueWithoutBetaCarbon(
                        "TYR", 4, 6.0, 6.0, 4.0)
        );
    }

    private static RigidTransform sampleRigidTransform() {
        double cos = Math.cos(Math.PI / 3.0);
        double sin = Math.sin(Math.PI / 3.0);
        // 60-degree rotation about Z, then translation
        return new RigidTransform(
                cos, -sin, 0.0,
                sin, cos, 0.0,
                0.0, 0.0, 1.0,
                new Point3D(17.0, -23.0, 31.0)
        );
    }

    private static Structure transformed(
            Structure structure,
            RigidTransform transform
    ) {
        List<Chain> chains = new ArrayList<>();
        for (Chain chain : structure.getChains()) {
            List<Residue> residues = new ArrayList<>();
            for (Residue residue : chain.residues()) {
                residues.add(residue.toBuilder()
                        .atoms(transformAtoms(residue, transform))
                        .build());
            }
            chains.add(new Chain(chain.id(), residues));
        }
        return new Structure(chains);
    }

    private static List<Atom> transformAtoms(
            Residue residue,
            RigidTransform transform
    ) {
        List<Atom> atoms = new ArrayList<>();
        for (Atom atom : residue.getAtoms()) {
            atoms.add(atom.toBuilder()
                    .position(transform.apply(atom.getPosition()))
                    .build());
        }
        return atoms;
    }

    private static Structure perturbed(
            Structure structure,
            double sigma
    ) {
        Random random = new Random(7);
        List<Chain> chains = new ArrayList<>();
        for (Chain chain : structure.getChains()) {
            List<Residue> residues = new ArrayList<>();
            for (Residue residue : chain.residues()) {
                List<Atom> atoms = new ArrayList<>();
                for (Atom atom : residue.getAtoms()) {
                    atoms.add(atom.toBuilder()
                            .position(new Point3D(
                                    atom.getPosition().x()
                                            + random.nextGaussian()
                                            * sigma,
                                    atom.getPosition().y()
                                            + random.nextGaussian()
                                            * sigma,
                                    atom.getPosition().z()
                                            + random.nextGaussian()
                                            * sigma
                            ))
                            .build());
                }
                residues.add(residue.toBuilder().atoms(atoms).build());
            }
            chains.add(new Chain(chain.id(), residues));
        }
        return new Structure(chains);
    }

    private static Structure relabeled(
            Structure structure,
            List<String> newNames
    ) {
        List<Chain> chains = new ArrayList<>();
        for (Chain chain : structure.getChains()) {
            List<Residue> residues = new ArrayList<>();
            int index = 0;
            for (Residue residue : chain.residues()) {
                residues.add(residue.toBuilder()
                        .name(newNames.get(index++))
                        .build());
            }
            chains.add(new Chain(chain.id(), residues));
        }
        return new Structure(chains);
    }

    private PocketMatchSignature randomSignature(
            Random random,
            int residueCount
    ) {
        String[] names = {
                "ALA", "LYS", "ASP", "TYR", "CYS", "VAL", "ARG", "GLU"
        };
        List<Residue> residues = new ArrayList<>();
        for (int index = 0; index < residueCount; index++) {
            residues.add(PocketMatchTestFixtures.alanine(
                    index + 1,
                    random.nextDouble() * 20.0,
                    random.nextDouble() * 20.0,
                    random.nextDouble() * 20.0
            ).toBuilder()
                    .name(names[index % names.length])
                    .build());
        }
        Structure structure = new Structure(
                List.of(new Chain("A", residues)));
        int[] numbers = new int[residueCount];
        for (int index = 0; index < residueCount; index++) {
            numbers[index] = index + 1;
        }
        return factory.describe(
                structure,
                PocketMatchTestFixtures.pocketOfResidueNumbers(
                        structure, numbers)
        );
    }

    private static PocketMatchSignature emptySignature() {
        double[][] lists =
                new double[PocketMatchCategories.CATEGORY_COUNT][];
        for (int index = 0; index < lists.length; index++) {
            lists[index] = new double[0];
        }
        return PocketMatchSignature.ofPersisted(lists, 0);
    }
}
