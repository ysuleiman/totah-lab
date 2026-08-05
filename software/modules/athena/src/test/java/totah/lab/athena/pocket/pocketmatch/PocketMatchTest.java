package totah.lab.athena.pocket.pocketmatch;

import org.junit.jupiter.api.Test;
import totah.lab.gaia.pocket.Pocket;
import totah.lab.gaia.structure.Structure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class PocketMatchTest {

    private final PocketMatch pocketMatch = new PocketMatch();

    @Test
    void comparesTwoPocketsEndToEnd() {
        Structure structure = PocketMatchTestFixtures.structureOf(
                PocketMatchTestFixtures.alanine(1, 0.0, 0.0, 0.0),
                PocketMatchTestFixtures.residueWithoutBetaCarbon(
                        "LYS", 2, 5.0, 1.0, 0.0),
                PocketMatchTestFixtures.residueWithoutBetaCarbon(
                        "ASP", 3, 1.0, 5.0, 2.0)
        );
        Pocket pocket = PocketMatchTestFixtures
                .pocketOfResidueNumbers(structure, 1, 2, 3);

        PocketMatchComparison identical = pocketMatch.compare(
                structure, pocket, structure, pocket);
        assertThat(identical.symmetricScore())
                .isCloseTo(1.0, within(1.0e-12));

        Pocket subset = PocketMatchTestFixtures
                .pocketOfResidueNumbers(structure, 1, 2);
        PocketMatchComparison containment = pocketMatch.compare(
                structure, subset, structure, pocket);
        assertThat(containment.firstCoverage())
                .isCloseTo(1.0, within(1.0e-9));
        assertThat(containment.symmetricScore())
                .isLessThan(1.0);
    }
}
