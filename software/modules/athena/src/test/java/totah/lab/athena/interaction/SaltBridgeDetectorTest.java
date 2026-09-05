package totah.lab.athena.interaction;

import org.junit.jupiter.api.Test;
import totah.lab.athena.interaction.perception.ChargeSign;
import totah.lab.athena.interaction.perception.ChargedGroup;
import totah.lab.athena.interaction.perception.ChargedGroupType;
import totah.lab.gaia.chemistry.Element;
import totah.lab.gaia.structure.ResidueId;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static totah.lab.athena.interaction.InteractionFixtures.atom;
import static totah.lab.athena.interaction.InteractionFixtures.chargedGroup;

class SaltBridgeDetectorTest {

    private static final InteractionThresholds THRESHOLDS =
            InteractionThresholds.athenaDefaults();
    private static final ResidueId PROTEIN = new ResidueId("A", 60, null);
    private static final ResidueId LIGAND = new ResidueId("L", 501, null);

    private final SaltBridgeDetector detector = new SaltBridgeDetector();

    private static ChargedGroup proteinNegative() {
        // Carboxylate pair with centroid at the origin.
        return chargedGroup(ChargeSign.NEGATIVE, ChargedGroupType.RESIDUE_ASP,
                PROTEIN, List.of(
                        atom(60, "OD1", Element.O, 0.2, 0, 0),
                        atom(61, "OD2", Element.O, -0.2, 0, 0)));
    }

    private static ChargedGroup ligandPositive(double x) {
        return chargedGroup(ChargeSign.POSITIVE, ChargedGroupType.AMINE,
                LIGAND, List.of(atom(110, "N1", Element.N, x, 0, 0)));
    }

    private static ChargedGroup ligandNegative(double x) {
        return chargedGroup(ChargeSign.NEGATIVE, ChargedGroupType.CARBOXYLATE,
                LIGAND, List.of(
                        atom(110, "O1", Element.O, x, 0.2, 0),
                        atom(111, "O2", Element.O, x, -0.2, 0)));
    }

    @Test
    void detectsOppositeSignPairAcrossBoundary() {
        List<Interaction> bridges = detector.detect(
                List.of(proteinNegative()), List.of(ligandPositive(4.5)),
                THRESHOLDS);

        assertThat(bridges).singleElement().satisfies(bridge -> {
            assertThat(bridge.type()).isEqualTo(InteractionType.SALT_BRIDGE);
            assertThat(bridge.residue()).isEqualTo(PROTEIN);
            assertThat(bridge.distanceAngstroms()).isEqualTo(4.5);
            assertThat(bridge.primaryAngleDegrees()).isNull();
            assertThat(bridge.proteinAtoms()).hasSize(2);
            assertThat(bridge.ligandAtoms()).hasSize(1);
            assertThat(bridge.proteinGroupId())
                    .isEqualTo("RESIDUE_ASP A:60");
            assertThat(bridge.ligandGroupId()).isEqualTo("AMINE L:501");
        });
    }

    @Test
    void maxBoundIsInclusiveAndMinBoundExclusive() {
        assertThat(detector.detect(List.of(proteinNegative()),
                List.of(ligandPositive(5.5)), THRESHOLDS)).hasSize(1);
        assertThat(detector.detect(List.of(proteinNegative()),
                List.of(ligandPositive(5.6)), THRESHOLDS)).isEmpty();
        assertThat(detector.detect(List.of(proteinNegative()),
                List.of(ligandPositive(0.5)), THRESHOLDS)).isEmpty();
        assertThat(detector.detect(List.of(proteinNegative()),
                List.of(ligandPositive(0.6)), THRESHOLDS)).hasSize(1);
    }

    @Test
    void sameSignPairsAreIgnored() {
        ChargedGroup proteinPositive = chargedGroup(ChargeSign.POSITIVE,
                ChargedGroupType.RESIDUE_LYS, PROTEIN,
                List.of(atom(60, "NZ", Element.N, 0, 0, 0)));

        assertThat(detector.detect(List.of(proteinPositive),
                List.of(ligandPositive(3.0)), THRESHOLDS)).isEmpty();
    }

    @Test
    void bothDirectionsProduceOneRecordPerGroupPair() {
        ChargedGroup proteinPositive = chargedGroup(ChargeSign.POSITIVE,
                ChargedGroupType.RESIDUE_LYS, PROTEIN,
                List.of(atom(60, "NZ", Element.N, 0, 0, 0)));

        List<Interaction> bridges = detector.detect(
                List.of(proteinNegative(), proteinPositive),
                List.of(ligandPositive(4.0), ligandNegative(4.0)),
                THRESHOLDS);

        // protein(-) x ligand(+) and protein(+) x ligand(-).
        assertThat(bridges).hasSize(2);
        assertThat(bridges).extracting(Interaction::proteinGroupId)
                .containsExactly("RESIDUE_ASP A:60", "RESIDUE_LYS A:60");
    }
}
