package totah.lab.athena.pocket.architecture;

import org.junit.jupiter.api.Test;
import totah.lab.gaia.molecule.Ligand;

import static org.assertj.core.api.Assertions.assertThat;

class LigandAtomCorrespondenceTest {

    private static final double[][] POSITIONS = {
            {1, 0, 0},
            {2, 0, 0},
            {1, 1, 0}
    };

    @Test
    void identicalOrderIsIndexCorrespondence() {
        Ligand a = PoseComparisonTestFixtures.ligand("A",
                new String[]{"C1", "C2", "C3"}, POSITIONS);
        Ligand b = PoseComparisonTestFixtures.ligand("B",
                new String[]{"C1", "C2", "C3"}, POSITIONS);

        LigandAtomCorrespondence.Mapping mapping =
                LigandAtomCorrespondence.map(a, b);

        assertThat(mapping.method())
                .isEqualTo(LigandAtomCorrespondence.Method.INDEX_ORDER);
        assertThat(mapping.bToAIndex()).containsExactly(0, 1, 2);
    }

    @Test
    void permutedOrderIsMappedByNameAndElement() {
        Ligand a = PoseComparisonTestFixtures.ligand("A",
                new String[]{"C1", "C2", "C3"}, POSITIONS);
        // Same atoms, different order: C3, C1, C2.
        Ligand b = PoseComparisonTestFixtures.ligand("B",
                new String[]{"C3", "C1", "C2"},
                new double[][]{
                        POSITIONS[2],
                        POSITIONS[0],
                        POSITIONS[1]
                });

        LigandAtomCorrespondence.Mapping mapping =
                LigandAtomCorrespondence.map(a, b);

        assertThat(mapping.method())
                .isEqualTo(LigandAtomCorrespondence.Method.NAME_ELEMENT);
        assertThat(mapping.bToAIndex()).containsExactly(2, 0, 1);
    }

    @Test
    void differentHeavyAtomCountsAreNotAvailable() {
        Ligand a = PoseComparisonTestFixtures.ligand("A",
                new String[]{"C1", "C2", "C3"}, POSITIONS);
        Ligand b = PoseComparisonTestFixtures.ligand("B",
                new String[]{"C1", "C2"},
                new double[][]{{1, 0, 0}, {2, 0, 0}});

        LigandAtomCorrespondence.Mapping mapping =
                LigandAtomCorrespondence.map(a, b);

        assertThat(mapping.method())
                .isEqualTo(LigandAtomCorrespondence.Method.NONE);
        assertThat(mapping.reason()).contains("counts differ");
    }

    @Test
    void unmappableNamesAreNotAvailable() {
        Ligand a = PoseComparisonTestFixtures.ligand("A",
                new String[]{"C1", "C2", "C3"}, POSITIONS);
        Ligand b = PoseComparisonTestFixtures.ligand("B",
                new String[]{"C1", "C2", "C4"}, POSITIONS);

        LigandAtomCorrespondence.Mapping mapping =
                LigandAtomCorrespondence.map(a, b);

        assertThat(mapping.method())
                .isEqualTo(LigandAtomCorrespondence.Method.NONE);
    }
}
