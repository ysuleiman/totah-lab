package totah.lab.athena.interaction;

import org.junit.jupiter.api.Test;
import totah.lab.athena.interaction.perception.PerceptionProvenance;
import totah.lab.gaia.chemistry.Element;
import totah.lab.gaia.structure.Atom;
import totah.lab.gaia.structure.ResidueId;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static totah.lab.athena.interaction.InteractionFixtures.atom;

class InteractionFingerprintTest {

    private static final InteractionThresholds THRESHOLDS =
            InteractionThresholds.athenaDefaults();

    private static Interaction interaction(
            InteractionType type, int serial, ResidueId residue) {

        Atom protein = atom(serial, "P" + serial, Element.C, 0, 0, 0);
        Atom ligand = atom(serial + 1000, "L" + serial, Element.C,
                3, 0, 0);
        return new Interaction(type, residue, List.of(protein),
                List.of(ligand), 3.0, null, null, null, null, THRESHOLDS);
    }

    private static InteractionProfile profile(Interaction... interactions) {
        return new InteractionProfile(
                List.of(interactions),
                List.of(interactions),
                Set.of(),
                THRESHOLDS,
                List.of(new PerceptionSummary(PerceptionSummary.RECEPTOR,
                        PerceptionProvenance.BOND_GRAPH,
                        1, 0, 0, 0, 0)));
    }

    @Test
    void buildsTypedMapAndKeepsRecordsAccessible() {
        ResidueId a10 = new ResidueId("A", 10, null);
        ResidueId a20 = new ResidueId("A", 20, null);
        Interaction hb = interaction(InteractionType.HYDROGEN_BOND, 1, a10);
        Interaction hydro = interaction(
                InteractionType.HYDROPHOBIC_CONTACT, 2, a10);
        Interaction hb2 = interaction(InteractionType.HYDROGEN_BOND, 3, a20);

        InteractionFingerprint fingerprint = InteractionFingerprint.of(
                profile(hb, hydro, hb2));

        assertThat(fingerprint.byResidue()).containsExactly(
                Map.entry(a10, Set.of(InteractionType.HYDROGEN_BOND,
                        InteractionType.HYDROPHOBIC_CONTACT)),
                Map.entry(a20, Set.of(InteractionType.HYDROGEN_BOND)));
        // No collapsing: the records behind the typed map are intact.
        assertThat(fingerprint.interactions())
                .containsExactly(hb, hydro, hb2);
        assertThat(fingerprint.interactionsOf(a10))
                .containsExactly(hb, hydro);
        assertThat(fingerprint.interactionsOf(a20))
                .containsExactly(hb2);
    }

    @Test
    void typedJaccardComparesResidueTypePairs() {
        ResidueId a10 = new ResidueId("A", 10, null);
        ResidueId a20 = new ResidueId("A", 20, null);
        ResidueId a30 = new ResidueId("A", 30, null);
        ResidueId b5 = new ResidueId("B", 5, null);

        InteractionFingerprint first = InteractionFingerprint.of(profile(
                interaction(InteractionType.HYDROGEN_BOND, 1, a10),
                interaction(InteractionType.HYDROPHOBIC_CONTACT, 2, a10),
                interaction(InteractionType.HYDROGEN_BOND, 3, a20),
                interaction(InteractionType.HALOGEN_BOND, 4, b5)));
        InteractionFingerprint second = InteractionFingerprint.of(profile(
                interaction(InteractionType.HYDROGEN_BOND, 5, a10),
                interaction(InteractionType.SALT_BRIDGE, 6, a10),
                interaction(InteractionType.PI_STACK_PARALLEL, 7, a30)));

        // Typed pairs: {(A10,HB)} shared out of 6 unique.
        assertThat(first.typedJaccard(second)).isEqualTo(1.0 / 6.0);
        // Residues: {A10} shared out of {A10, A20, B5, A30}.
        assertThat(first.residueJaccard(second)).isEqualTo(0.25);
    }

    @Test
    void jaccardIsSymmetricAndEmptyFingerprintsAreIdentical() {
        ResidueId a10 = new ResidueId("A", 10, null);
        InteractionFingerprint first = InteractionFingerprint.of(profile(
                interaction(InteractionType.HYDROGEN_BOND, 1, a10)));
        InteractionFingerprint second = InteractionFingerprint.of(profile(
                interaction(InteractionType.SALT_BRIDGE, 2, a10)));
        InteractionFingerprint empty = InteractionFingerprint.of(profile());

        assertThat(first.typedJaccard(second))
                .isEqualTo(second.typedJaccard(first));
        assertThat(first.residueJaccard(second)).isEqualTo(1.0);
        assertThat(first.typedJaccard(second)).isEqualTo(0.0);
        assertThat(empty.typedJaccard(
                InteractionFingerprint.of(profile()))).isEqualTo(1.0);
        assertThat(empty.residueJaccard(
                InteractionFingerprint.of(profile()))).isEqualTo(1.0);
        assertThat(empty.typedJaccard(first)).isEqualTo(0.0);
    }
}
