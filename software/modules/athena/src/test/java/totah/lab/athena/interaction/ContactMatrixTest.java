package totah.lab.athena.interaction;

import org.junit.jupiter.api.Test;
import totah.lab.gaia.chemistry.Element;
import totah.lab.gaia.structure.Atom;
import totah.lab.gaia.structure.ResidueId;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static totah.lab.athena.interaction.InteractionFixtures.atom;

class ContactMatrixTest {

    private static final InteractionThresholds THRESHOLDS =
            InteractionThresholds.athenaDefaults();

    private static Interaction interaction(
            InteractionType type,
            int serial,
            ResidueId residue,
            double distance) {

        Atom protein = atom(serial, "P" + serial, Element.C, 0, 0, 0);
        Atom ligand = atom(serial + 1000, "L" + serial, Element.C,
                distance, 0, 0);
        return new Interaction(type, residue, List.of(protein),
                List.of(ligand), distance, null, null, null, null,
                THRESHOLDS);
    }

    @Test
    void countsAndMinDistancesPerCell() {
        ResidueId a43 = new ResidueId("A", 43, null);
        ResidueId a50 = new ResidueId("A", 50, null);

        ContactMatrix matrix = ContactMatrix.of(List.of(
                interaction(InteractionType.HYDROGEN_BOND, 1, a43, 3.0),
                interaction(InteractionType.HYDROGEN_BOND, 2, a43, 2.8),
                interaction(InteractionType.PI_STACK_PARALLEL, 3, a43, 3.4),
                interaction(InteractionType.SALT_BRIDGE, 4, a50, 4.5)));

        assertThat(matrix.rows()).containsExactly(a43, a50);
        assertThat(matrix.columns())
                .containsExactly(InteractionType.values());

        assertThat(matrix.cell(a43, InteractionType.HYDROGEN_BOND).count())
                .isEqualTo(2);
        assertThat(matrix.cell(a43, InteractionType.HYDROGEN_BOND)
                .minDistanceAngstroms()).isEqualTo(2.8);
        assertThat(matrix.cell(a43, InteractionType.PI_STACK_PARALLEL)
                .count()).isEqualTo(1);
        assertThat(matrix.cell(a50, InteractionType.SALT_BRIDGE).count())
                .isEqualTo(1);
        // Empty cell: count 0, no distance.
        ContactMatrix.Cell empty = matrix.cell(
                a50, InteractionType.HALOGEN_BOND);
        assertThat(empty.count()).isZero();
        assertThat(empty.minDistanceAngstroms()).isNull();
        // Unknown residue: also an empty cell.
        assertThat(matrix.cell(new ResidueId("Z", 1, null),
                InteractionType.HYDROGEN_BOND).count()).isZero();
    }

    @Test
    void rowOrderFollowsFirstAppearance() {
        ResidueId b7 = new ResidueId("B", 7, null);
        ResidueId a3 = new ResidueId("A", 3, null);

        ContactMatrix matrix = ContactMatrix.of(List.of(
                interaction(InteractionType.SALT_BRIDGE, 1, b7, 4.0),
                interaction(InteractionType.HYDROGEN_BOND, 2, a3, 3.0)));

        assertThat(matrix.rows()).containsExactly(b7, a3);
    }

    @Test
    void rendersCountsAsDeterministicCsv() {
        ResidueId a43 = new ResidueId("A", 43, null);
        ResidueId s900 = new ResidueId("S", 900, null);

        ContactMatrix matrix = ContactMatrix.of(List.of(
                interaction(InteractionType.PI_STACK_PARALLEL, 1, a43, 3.4),
                interaction(InteractionType.HYDROGEN_BOND, 2, a43, 3.0),
                interaction(InteractionType.HYDROGEN_BOND, 3, s900, 2.9)));

        String expected = "residue,HYDROGEN_BOND,SALT_BRIDGE,"
                + "HYDROPHOBIC_CONTACT,PI_STACK_PARALLEL,"
                + "PI_STACK_T_SHAPED,PI_CATION,HALOGEN_BOND"
                + "\nA:43,1,0,0,1,0,0,0"
                + "\nS:900,1,0,0,0,0,0,0";
        assertThat(matrix.toCsv()).isEqualTo(expected);
        // Deterministic across rebuilds.
        assertThat(ContactMatrix.of(List.of(
                interaction(InteractionType.PI_STACK_PARALLEL, 11, a43, 3.4),
                interaction(InteractionType.HYDROGEN_BOND, 12, a43, 3.0),
                interaction(InteractionType.HYDROGEN_BOND, 13, s900, 2.9)))
                .toCsv()).isEqualTo(expected);
    }
}
