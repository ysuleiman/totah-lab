package totah.lab.athena.interaction.perception;

import org.junit.jupiter.api.Test;
import totah.lab.gaia.chemistry.BondOrder;
import totah.lab.gaia.chemistry.Element;
import totah.lab.gaia.geometry.Point3D;
import totah.lab.gaia.structure.Atom;
import totah.lab.gaia.structure.AtomReference;
import totah.lab.gaia.structure.Bond;
import totah.lab.gaia.structure.Chain;
import totah.lab.gaia.structure.ConnectivityProvenance;
import totah.lab.gaia.structure.Residue;
import totah.lab.gaia.structure.Structure;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ChargedGroupPerceptionTest {

    private final ChargedGroupPerception perception =
            new ChargedGroupPerception();

    @Test
    void perceivesArginineGuanidiniumFromTemplate() {
        Structure structure = proteinStructure("ARG", 7, List.of(
                atom("NE", Element.N, 0.0, 0.0, 0.0, 0.0),
                atom("NH1", Element.N, 3.0, 0.0, 0.0, 0.0),
                atom("NH2", Element.N, 0.0, 3.0, 0.0, 0.0)));

        assertThat(perception.perceive(structure))
                .singleElement()
                .satisfies(group -> {
                    assertThat(group.sign()).isEqualTo(ChargeSign.POSITIVE);
                    assertThat(group.type())
                            .isEqualTo(ChargedGroupType.RESIDUE_ARG);
                    assertThat(group.provenance())
                            .isEqualTo(PerceptionProvenance.PROTEIN_TEMPLATE);
                    assertThat(group.owner().residueNumber()).isEqualTo(7);
                    assertThat(group.chargeCenter())
                            .isEqualTo(new Point3D(1.0, 1.0, 0.0));
                });
    }

    @Test
    void perceivesLysineAndHistidineAsPositive() {
        Structure structure = new Structure(List.of(new Chain("A", List.of(
                new Residue("LYS", 11, List.of(
                        atom("NZ", Element.N, 0.0, 0.0, 0.0, 0.0))),
                new Residue("HIS", 12, List.of(
                        atom("ND1", Element.N, 5.0, 0.0, 0.0, 0.0),
                        atom("NE2", Element.N, 7.0, 0.0, 0.0, 0.0)))))));

        assertThat(perception.perceive(structure))
                .satisfiesExactly(
                        group -> {
                            assertThat(group.type())
                                    .isEqualTo(ChargedGroupType.RESIDUE_LYS);
                            assertThat(group.sign())
                                    .isEqualTo(ChargeSign.POSITIVE);
                        },
                        group -> {
                            assertThat(group.type())
                                    .isEqualTo(ChargedGroupType.RESIDUE_HIS);
                            assertThat(group.sign())
                                    .isEqualTo(ChargeSign.POSITIVE);
                            assertThat(group.note()).contains("pH");
                        });
    }

    @Test
    void perceivesAspartateAndGlutamateAsNegative() {
        Structure structure = new Structure(List.of(new Chain("A", List.of(
                new Residue("ASP", 21, List.of(
                        atom("OD1", Element.O, 0.0, 0.0, 0.0, 0.0),
                        atom("OD2", Element.O, 2.0, 0.0, 0.0, 0.0))),
                new Residue("GLU", 22, List.of(
                        atom("OE1", Element.O, 5.0, 0.0, 0.0, 0.0),
                        atom("OE2", Element.O, 7.0, 0.0, 0.0, 0.0)))))));

        assertThat(perception.perceive(structure))
                .satisfiesExactly(
                        group -> {
                            assertThat(group.type())
                                    .isEqualTo(ChargedGroupType.RESIDUE_ASP);
                            assertThat(group.sign())
                                    .isEqualTo(ChargeSign.NEGATIVE);
                        },
                        group -> {
                            assertThat(group.type())
                                    .isEqualTo(ChargedGroupType.RESIDUE_GLU);
                            assertThat(group.sign())
                                    .isEqualTo(ChargeSign.NEGATIVE);
                        });
    }

    @Test
    void skipsResidueWithIncompleteTemplate() {
        Structure structure = proteinStructure("ARG", 7, List.of(
                atom("NE", Element.N, 0.0, 0.0, 0.0, 0.0),
                atom("NH1", Element.N, 3.0, 0.0, 0.0, 0.0))); // NH2 missing

        assertThat(perception.perceive(structure)).isEmpty();
    }

    @Test
    void perceivesLigandCarboxylateFromBondGraph() {
        Structure structure = ligandStructure(
                List.of(
                        atom("C1", Element.C, 0.0, 0.0, 0.0, 0.0),
                        atom("O1", Element.O, 1.3, 0.0, 0.0, 0.0),
                        atom("O2", Element.O, 0.0, 1.3, 0.0, 0.0),
                        atom("C2", Element.C, 0.0, 0.0, 1.5, 0.0)),
                List.of(
                        bond("C1", "O1"),
                        bond("C1", "O2"),
                        bond("C1", "C2")));

        assertThat(perception.perceive(structure))
                .singleElement()
                .satisfies(group -> {
                    assertThat(group.type())
                            .isEqualTo(ChargedGroupType.CARBOXYLATE);
                    assertThat(group.sign()).isEqualTo(ChargeSign.NEGATIVE);
                    assertThat(group.provenance())
                            .isEqualTo(PerceptionProvenance.BOND_GRAPH);
                    assertThat(group.atoms())
                            .extracting(Atom::getName)
                            .containsExactlyInAnyOrder("C1", "O1", "O2");
                });
    }

    @Test
    void perceivesLigandGuanidiniumFromBondGraph() {
        Structure structure = ligandStructure(
                List.of(
                        atom("C1", Element.C, 0.0, 0.0, 0.0, 0.0),
                        atom("N1", Element.N, 1.4, 0.0, 0.0, 0.0),
                        atom("N2", Element.N, 0.0, 1.4, 0.0, 0.0),
                        atom("N3", Element.N, 0.0, 0.0, 1.4, 0.0)),
                List.of(
                        bond("C1", "N1"),
                        bond("C1", "N2"),
                        bond("C1", "N3")));

        assertThat(perception.perceive(structure))
                .singleElement()
                .satisfies(group -> {
                    assertThat(group.type())
                            .isEqualTo(ChargedGroupType.GUANIDINIUM);
                    assertThat(group.sign()).isEqualTo(ChargeSign.POSITIVE);
                });
    }

    @Test
    void perceivesQuaternaryAmineFromBondDegreeFour() {
        Structure structure = ligandStructure(
                List.of(
                        atom("N1", Element.N, 0.0, 0.0, 0.0, 0.0),
                        atom("C1", Element.C, 1.5, 0.0, 0.0, 0.0),
                        atom("C2", Element.C, 0.0, 1.5, 0.0, 0.0),
                        atom("C3", Element.C, 0.0, 0.0, 1.5, 0.0),
                        atom("C4", Element.C, -1.5, 0.0, 0.0, 0.0)),
                List.of(
                        bond("N1", "C1"),
                        bond("N1", "C2"),
                        bond("N1", "C3"),
                        bond("N1", "C4")));

        assertThat(perception.perceive(structure))
                .singleElement()
                .satisfies(group -> {
                    assertThat(group.type()).isEqualTo(ChargedGroupType.AMINE);
                    assertThat(group.sign()).isEqualTo(ChargeSign.POSITIVE);
                    assertThat(group.atoms()).hasSize(5);
                });
    }

    @Test
    void perceivesSulfoniumFromBondGraph() {
        Structure structure = ligandStructure(
                List.of(
                        atom("S1", Element.S, 0.0, 0.0, 0.0, 0.0),
                        atom("C1", Element.C, 1.8, 0.0, 0.0, 0.0),
                        atom("C2", Element.C, 0.0, 1.8, 0.0, 0.0),
                        atom("C3", Element.C, 0.0, 0.0, 1.8, 0.0)),
                List.of(
                        bond("S1", "C1"),
                        bond("S1", "C2"),
                        bond("S1", "C3")));

        assertThat(perception.perceive(structure))
                .singleElement()
                .satisfies(group -> {
                    assertThat(group.type())
                            .isEqualTo(ChargedGroupType.SULFONIUM);
                    assertThat(group.sign()).isEqualTo(ChargeSign.POSITIVE);
                });
    }

    @Test
    void neutralFunctionalGroupsYieldNoChargedGroup() {
        // Ketone carbon (single O) and tertiary amine (degree 3).
        Structure structure = ligandStructure(
                List.of(
                        atom("C1", Element.C, 0.0, 0.0, 0.0, 0.0),
                        atom("O1", Element.O, 1.2, 0.0, 0.0, 0.0),
                        atom("N1", Element.N, 5.0, 0.0, 0.0, 0.0),
                        atom("C2", Element.C, 6.5, 0.0, 0.0, 0.0),
                        atom("C3", Element.C, 5.0, 1.5, 0.0, 0.0),
                        atom("C4", Element.C, 5.0, 0.0, 1.5, 0.0)),
                List.of(
                        bond("C1", "O1"),
                        bond("N1", "C2"),
                        bond("N1", "C3"),
                        bond("N1", "C4")));

        assertThat(perception.perceive(structure)).isEmpty();
    }

    @Test
    void degradesToChargeSumWhenConnectivityAbsent() {
        Structure structure = new Structure(List.of(new Chain("A",
                List.of(new Residue("LIG", 501, List.of(
                        atom("O1", Element.O, 0.0, 0.0, 0.0, -0.5),
                        atom("O2", Element.O, 2.0, 0.0, 0.0, -0.3),
                        atom("C1", Element.C, 1.0, 1.0, 0.0, 0.1)))))));

        assertThat(perception.perceive(structure))
                .singleElement()
                .satisfies(group -> {
                    assertThat(group.type())
                            .isEqualTo(ChargedGroupType.CHARGE_SUM);
                    assertThat(group.sign()).isEqualTo(ChargeSign.NEGATIVE);
                    assertThat(group.provenance())
                            .isEqualTo(PerceptionProvenance.CHARGE_SUM_FALLBACK);
                    assertThat(group.degraded()).isTrue();
                    assertThat(group.note()).contains("ABSENT");
                });
    }

    @Test
    void nearNeutralChargeSumYieldsNoFallbackGroup() {
        Structure structure = new Structure(List.of(new Chain("A",
                List.of(new Residue("LIG", 501, List.of(
                        atom("O1", Element.O, 0.0, 0.0, 0.0, -0.3),
                        atom("C1", Element.C, 2.0, 0.0, 0.0, 0.1)))))));

        assertThat(perception.perceive(structure)).isEmpty();
    }

    @Test
    void emptyStructureYieldsNoChargedGroups() {
        assertThat(perception.perceive(new Structure(List.of()))).isEmpty();
    }

    private static Structure proteinStructure(
            String residueName,
            int residueNumber,
            List<Atom> atoms) {

        return new Structure(List.of(new Chain("A",
                List.of(new Residue(residueName, residueNumber, atoms)))));
    }

    private static Structure ligandStructure(
            List<Atom> atoms,
            List<Bond> bonds) {

        return new Structure(
                List.of(new Chain("A",
                        List.of(new Residue("LIG", 501, atoms)))),
                bonds,
                ConnectivityProvenance.EXPLICIT);
    }

    private static Bond bond(String firstName, String secondName) {
        return new Bond(
                new AtomReference("A", 501, ' ', firstName),
                new AtomReference("A", 501, ' ', secondName),
                BondOrder.SINGLE);
    }

    private static Atom atom(
            String name,
            Element element,
            double x,
            double y,
            double z,
            double charge) {

        return Atom.builder()
                .pdbSerial(1)
                .name(name)
                .element(element)
                .position(new Point3D(x, y, z))
                .charge(charge)
                .occupancy(1.0)
                .build();
    }
}
