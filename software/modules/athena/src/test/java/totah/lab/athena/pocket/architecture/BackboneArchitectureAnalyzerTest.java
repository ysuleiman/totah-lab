package totah.lab.athena.pocket.architecture;

import org.junit.jupiter.api.Test;
import totah.lab.gaia.pocket.Pocket;
import totah.lab.gaia.structure.Structure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.offset;

class BackboneArchitectureAnalyzerTest {

    private final BackboneArchitectureAnalyzer analyzer =
            new BackboneArchitectureAnalyzer();

    @Test
    void identicalReceptorsHaveZeroDisplacement() {
        Structure receptor = ArchitectureTestFixtures.receptor();
        Pocket pocket = ArchitectureTestFixtures.pocket("1",
                ArchitectureTestFixtures.BASE_SPHERES);

        BackboneArchitectureComparison comparison =
                analyzer.compare(receptor, pocket, receptor, pocket);

        assertThat(comparison.caRmsd())
                .isCloseTo(0.0, offset(1.0e-9));
        assertThat(comparison.backboneRmsd())
                .isCloseTo(0.0, offset(1.0e-9));
        assertThat(comparison.heavyAtomRmsd())
                .isCloseTo(0.0, offset(1.0e-9));
        assertThat(comparison.pocketRegionResiduePairs()).isEqualTo(6);
        assertThat(comparison.displacementProfile()).hasSize(6);
        assertThat(comparison.displacementProfile().get(0)
                .caDisplacement()).isCloseTo(0.0, offset(1.0e-9));
    }

    @Test
    void rigidlyTransformedReceptorHasZeroDisplacement() {
        Structure receptorA = ArchitectureTestFixtures.receptor();
        Structure receptorB = ArchitectureTestFixtures.transformed(
                receptorA,
                ArchitectureTestFixtures.TRANSFORM
        );
        Pocket pocket = ArchitectureTestFixtures.pocket("1",
                ArchitectureTestFixtures.BASE_SPHERES);

        BackboneArchitectureComparison comparison =
                analyzer.compare(receptorA, pocket, receptorB, pocket);

        assertThat(comparison.caRmsd())
                .isCloseTo(0.0, offset(1.0e-6));
    }

    @Test
    void shiftedResidueTopsTheDisplacementProfile() {
        Structure receptorA = ArchitectureTestFixtures.receptor();
        Structure receptorB =
                ArchitectureTestFixtures.receptorWithShiftedCa(
                        3, 2, 0, 0);
        Pocket pocket = ArchitectureTestFixtures.pocket("1",
                ArchitectureTestFixtures.BASE_SPHERES);

        BackboneArchitectureComparison comparison =
                analyzer.compare(receptorA, pocket, receptorB, pocket);

        BackboneArchitectureComparison.ResidueDisplacement top =
                comparison.displacementProfile().get(0);

        assertThat(top.residueA().residueNumber()).isEqualTo(3);
        assertThat(top.caDisplacement()).isGreaterThan(1.0);

        // The other residues absorb only a small share of the shift.
        assertThat(comparison.displacementProfile().get(1)
                .caDisplacement())
                .isLessThan(top.caDisplacement());

        // The top segment localizes to the shifted residue's region.
        BackboneArchitectureComparison.SegmentDisplacement segment =
                comparison.segmentProfile().get(0);
        assertThat(segment.startResidueA())
                .isLessThanOrEqualTo(3);
        assertThat(segment.endResidueA())
                .isGreaterThanOrEqualTo(3);
    }
}
