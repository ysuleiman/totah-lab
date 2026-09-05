package totah.lab.athena.interaction;

import org.junit.jupiter.api.Test;
import totah.lab.athena.interaction.perception.HydrophobicAtoms;
import totah.lab.athena.interaction.perception.PerceptionProvenance;
import totah.lab.gaia.chemistry.Element;
import totah.lab.gaia.structure.Atom;
import totah.lab.gaia.structure.ResidueId;
import totah.lab.gaia.structure.Structure;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static totah.lab.athena.interaction.InteractionFixtures.atom;
import static totah.lab.athena.interaction.InteractionFixtures.chain;
import static totah.lab.athena.interaction.InteractionFixtures.residue;
import static totah.lab.athena.interaction.InteractionFixtures.structure;

class HydrophobicContactDetectorTest {

    private static final InteractionThresholds THRESHOLDS =
            InteractionThresholds.athenaDefaults();

    private final HydrophobicContactDetector detector =
            new HydrophobicContactDetector();

    private static HydrophobicAtoms perceived(Atom... atoms) {
        return new HydrophobicAtoms(List.of(atoms),
                PerceptionProvenance.BOND_GRAPH, "test fixture");
    }

    @Test
    void detectsContactWithinBoundsAndResolvesProteinResidue() {
        Atom proteinCarbon = atom(1, "CB", Element.C, 0, 0, 0);
        Structure protein = structure(chain("A",
                residue("ALA", 10, List.of(proteinCarbon))));
        Atom ligandCarbon = atom(2, "C1", Element.C, 3.0, 0, 0);

        List<Interaction> contacts = detector.detect(protein,
                perceived(proteinCarbon), perceived(ligandCarbon),
                THRESHOLDS);

        assertThat(contacts).singleElement().satisfies(contact -> {
            assertThat(contact.type())
                    .isEqualTo(InteractionType.HYDROPHOBIC_CONTACT);
            assertThat(contact.residue())
                    .isEqualTo(new ResidueId("A", 10, null));
            assertThat(contact.proteinAtoms()).containsExactly(proteinCarbon);
            assertThat(contact.ligandAtoms()).containsExactly(ligandCarbon);
            assertThat(contact.distanceAngstroms()).isEqualTo(3.0);
            assertThat(contact.primaryAngleDegrees()).isNull();
            assertThat(contact.thresholds()).isSameAs(THRESHOLDS);
        });
    }

    @Test
    void maxBoundIsInclusive() {
        Atom proteinCarbon = atom(1, "CB", Element.C, 0, 0, 0);
        Structure protein = structure(chain("A",
                residue("ALA", 10, List.of(proteinCarbon))));
        Atom ligandCarbon = atom(2, "C1", Element.C, 4.0, 0, 0);

        assertThat(detector.detect(protein, perceived(proteinCarbon),
                perceived(ligandCarbon), THRESHOLDS)).hasSize(1);
    }

    @Test
    void minBoundIsExclusive() {
        Atom proteinCarbon = atom(1, "CB", Element.C, 0, 0, 0);
        Structure protein = structure(chain("A",
                residue("ALA", 10, List.of(proteinCarbon))));
        Atom atMin = atom(2, "C1", Element.C, 0.5, 0, 0);
        Atom justAboveMin = atom(3, "C2", Element.C, 0.6, 0, 0);
        Atom aboveMax = atom(4, "C3", Element.C, 4.1, 0, 0);

        List<Interaction> contacts = detector.detect(protein,
                perceived(proteinCarbon),
                perceived(atMin, justAboveMin, aboveMax), THRESHOLDS);

        assertThat(contacts).singleElement().satisfies(contact ->
                assertThat(contact.ligandAtoms())
                        .containsExactly(justAboveMin));
    }

    @Test
    void foreignProteinAtomRejected() {
        Atom proteinCarbon = atom(1, "CB", Element.C, 0, 0, 0);
        Structure protein = structure(chain("A",
                residue("ALA", 10, List.of(proteinCarbon))));
        Atom foreign = atom(9, "XX", Element.C, 1.0, 0, 0);

        assertThatThrownBy(() -> detector.detect(protein,
                perceived(foreign), perceived(proteinCarbon), THRESHOLDS))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
