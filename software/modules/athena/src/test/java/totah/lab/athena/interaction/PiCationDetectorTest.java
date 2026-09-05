package totah.lab.athena.interaction;

import org.junit.jupiter.api.Test;
import totah.lab.athena.interaction.perception.AromaticRing;
import totah.lab.athena.interaction.perception.ChargeSign;
import totah.lab.athena.interaction.perception.ChargedGroup;
import totah.lab.athena.interaction.perception.ChargedGroupType;
import totah.lab.gaia.chemistry.Element;
import totah.lab.gaia.geometry.Point3D;
import totah.lab.gaia.geometry.Vector3D;
import totah.lab.gaia.structure.Atom;
import totah.lab.gaia.structure.ResidueId;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static totah.lab.athena.interaction.InteractionFixtures.atom;
import static totah.lab.athena.interaction.InteractionFixtures.chargedGroup;
import static totah.lab.athena.interaction.InteractionFixtures.hexRing;

class PiCationDetectorTest {

    private static final InteractionThresholds THRESHOLDS =
            InteractionThresholds.athenaDefaults();
    private static final ResidueId PROTEIN_RING_OWNER =
            new ResidueId("A", 43, null);
    private static final ResidueId LYS_OWNER = new ResidueId("A", 50, null);
    private static final ResidueId LIGAND = new ResidueId("L", 501, null);

    private final PiCationDetector detector = new PiCationDetector();

    private static AromaticRing proteinRing() {
        return hexRing("PHE A:43 ring0", PROTEIN_RING_OWNER, 1,
                new Point3D(0, 0, 0),
                new Vector3D(1, 0, 0), new Vector3D(0, 1, 0));
    }

    private static AromaticRing ligandRing() {
        return hexRing("LIG L:501 ring0", LIGAND, 101,
                new Point3D(0, 0, 0),
                new Vector3D(1, 0, 0), new Vector3D(0, 1, 0));
    }

    private static ChargedGroup proteinPositive(double x, double y, double z) {
        return chargedGroup(ChargeSign.POSITIVE, ChargedGroupType.RESIDUE_LYS,
                LYS_OWNER,
                List.of(atom(50, "NZ", Element.N, x, y, z)));
    }

    @Test
    void proteinPositiveGroupFacingLigandRing() {
        List<Interaction> interactions = detector.detect(
                List.of(proteinPositive(0, 0, 4.0)), List.of(),
                List.of(), List.of(ligandRing()), THRESHOLDS);

        assertThat(interactions).singleElement().satisfies(piCation -> {
            assertThat(piCation.type()).isEqualTo(InteractionType.PI_CATION);
            assertThat(piCation.residue()).isEqualTo(LYS_OWNER);
            assertThat(piCation.distanceAngstroms()).isEqualTo(4.0);
            assertThat(piCation.primaryAngleDegrees()).isNull();
            assertThat(piCation.proteinGroupId())
                    .isEqualTo("RESIDUE_LYS A:50");
            assertThat(piCation.ligandGroupId())
                    .isEqualTo("LIG L:501 ring0");
        });
    }

    @Test
    void distanceBoundIsInclusiveAndOffsetOutRejects() {
        AromaticRing ring = ligandRing();

        assertThat(detector.detect(List.of(proteinPositive(0, 0, 6.0)),
                List.of(), List.of(), List.of(ring), THRESHOLDS)).hasSize(1);
        assertThat(detector.detect(List.of(proteinPositive(0, 0, 6.1)),
                List.of(), List.of(), List.of(ring), THRESHOLDS)).isEmpty();
        // Offset exactly 2.0: inclusive, accepted.
        assertThat(detector.detect(List.of(proteinPositive(2.0, 0, 3.0)),
                List.of(), List.of(), List.of(ring), THRESHOLDS)).hasSize(1);
        // Offset 2.5 > 2.0: rejected.
        assertThat(detector.detect(List.of(proteinPositive(2.5, 0, 3.0)),
                List.of(), List.of(), List.of(ring), THRESHOLDS)).isEmpty();
    }

    @Test
    void negativeGroupsAreIgnored() {
        ChargedGroup negative = chargedGroup(ChargeSign.NEGATIVE,
                ChargedGroupType.RESIDUE_ASP, new ResidueId("A", 60, null),
                List.of(atom(60, "OD1", Element.O, 0, 0, 4.0)));

        assertThat(detector.detect(List.of(negative), List.of(),
                List.of(), List.of(ligandRing()), THRESHOLDS)).isEmpty();
    }

    @Test
    void ligandAmineFacingProteinRingPassesTertamineGuard() {
        // Neighbors coplanar with the ring: amine normal parallel to the
        // ring normal.
        ChargedGroup amine = chargedGroup(ChargeSign.POSITIVE,
                ChargedGroupType.AMINE, LIGAND, List.of(
                        atom(110, "N1", Element.N, 0, 0, 4.0),
                        atom(111, "C1", Element.C, 1, 0, 4.0),
                        atom(112, "C2", Element.C, 0, 1, 4.0),
                        atom(113, "C3", Element.C, -1, 0, 4.0)));

        List<Interaction> interactions = detector.detect(
                List.of(), List.of(proteinRing()),
                List.of(amine), List.of(), THRESHOLDS);

        assertThat(interactions).singleElement().satisfies(piCation -> {
            assertThat(piCation.residue()).isEqualTo(PROTEIN_RING_OWNER);
            assertThat(piCation.primaryAngleDegrees()).isCloseTo(0.0,
                    org.assertj.core.data.Offset.offset(1e-9));
            assertThat(piCation.proteinGroupId())
                    .isEqualTo("PHE A:43 ring0");
            assertThat(piCation.ligandGroupId())
                    .isEqualTo("AMINE L:501");
        });
    }

    @Test
    void ligandAmineWithPerpendicularAmineNormalIsRejected() {
        // Neighbor vectors (0,1,0) and (0,0,1): amine normal (1,0,0) is
        // perpendicular to the ring normal.
        ChargedGroup amine = chargedGroup(ChargeSign.POSITIVE,
                ChargedGroupType.AMINE, LIGAND, List.of(
                        atom(110, "N1", Element.N, 0, 0, 4.0),
                        atom(111, "C1", Element.C, 0, 1, 4.0),
                        atom(112, "C2", Element.C, 0, 0, 5.0)));

        assertThat(detector.detect(List.of(), List.of(proteinRing()),
                List.of(amine), List.of(), THRESHOLDS)).isEmpty();
    }

    @Test
    void allChargeGroupsAreEvaluatedPerRing() {
        // Deviation from PLIP: the tertamine branch `break` quirk skips
        // later charge groups of a ring; here the failing first group
        // must not suppress the passing second one.
        ChargedGroup failing = chargedGroup(ChargeSign.POSITIVE,
                ChargedGroupType.AMINE, LIGAND, List.of(
                        atom(110, "N1", Element.N, 0, 0, 4.0),
                        atom(111, "C1", Element.C, 0, 1, 4.0),
                        atom(112, "C2", Element.C, 0, 0, 5.0)));
        ChargedGroup passing = chargedGroup(ChargeSign.POSITIVE,
                ChargedGroupType.AMINE, new ResidueId("L", 502, null),
                List.of(
                        atom(120, "N1", Element.N, 0, 0, 3.5),
                        atom(121, "C1", Element.C, 1, 0, 3.5),
                        atom(122, "C2", Element.C, 0, 1, 3.5),
                        atom(123, "C3", Element.C, -1, 0, 3.5)));

        List<Interaction> interactions = detector.detect(
                List.of(), List.of(proteinRing()),
                List.of(failing, passing), List.of(), THRESHOLDS);

        assertThat(interactions).singleElement().satisfies(piCation ->
                assertThat(piCation.ligandGroupId())
                        .isEqualTo("AMINE L:502"));
    }

    @Test
    void nonAmineGroupsSkipTheTertamineGuard() {
        ChargedGroup guanidinium = chargedGroup(ChargeSign.POSITIVE,
                ChargedGroupType.GUANIDINIUM, LIGAND, List.of(
                        atom(110, "C1", Element.C, 0, 0, 4.0),
                        atom(111, "N1", Element.N, 1, 0, 4.0),
                        atom(112, "N2", Element.N, -0.5, 0.87, 4.0),
                        atom(113, "N3", Element.N, -0.5, -0.87, 4.0)));

        assertThat(detector.detect(List.of(), List.of(proteinRing()),
                List.of(guanidinium), List.of(), THRESHOLDS))
                .singleElement()
                .extracting(Interaction::primaryAngleDegrees)
                .isNull();
    }
}
