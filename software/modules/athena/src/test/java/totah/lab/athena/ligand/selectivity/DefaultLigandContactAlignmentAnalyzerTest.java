package totah.lab.athena.ligand.selectivity;

import org.junit.jupiter.api.Test;
import totah.lab.athena.ligand.contact.ContactType;
import totah.lab.athena.ligand.contact.LigandContact;
import totah.lab.gaia.chemistry.Element;
import totah.lab.gaia.classification.ResidueCategory;
import totah.lab.gaia.geometry.Point3D;
import totah.lab.gaia.molecule.Ligand;
import totah.lab.gaia.pocket.AlphaSphere;
import totah.lab.gaia.pocket.AlphaSphereSet;
import totah.lab.gaia.pocket.Pocket;
import totah.lab.gaia.pocket.PocketId;
import totah.lab.gaia.pocket.PocketSource;
import totah.lab.gaia.structure.Atom;
import totah.lab.gaia.structure.Chain;
import totah.lab.gaia.structure.Residue;
import totah.lab.gaia.structure.ResidueId;
import totah.lab.gaia.structure.Structure;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultLigandContactAlignmentAnalyzerTest {

    private final DefaultLigandContactAlignmentAnalyzer analyzer =
            new DefaultLigandContactAlignmentAnalyzer();

    @Test
    void conservedContactWhenIdenticalResidueContactsOnBothSides() {
        Structure receptorA = receptor(1, "ALA", "PHE", "GLY");
        Structure receptorB = receptor(1, "ALA", "PHE", "GLY");

        LigandContactAlignment alignment = analyzer.align(
                receptorA, pose(), List.of(contact(2)),
                receptorB, pose(), List.of(contact(2)),
                null, null
        );

        assertThat(alignment.contacts()).hasSize(1);

        AlignedLigandContact row = alignment.contacts().get(0);
        assertThat(row.differentialType())
                .isEqualTo(DifferentialContactType.CONSERVED_CONTACT);
        assertThat(row.alignmentPosition()).isEqualTo(2);
        assertThat(row.residueA()).isEqualTo("PHE");
        assertThat(row.residueB()).isEqualTo("PHE");
        assertThat(row.contactA()).isTrue();
        assertThat(row.contactB()).isTrue();
        assertThat(row.minDistanceA()).isEqualTo(3.0);
        assertThat(row.contactTypeA()).isEqualTo(ContactType.DIRECT);
        assertThat(row.substitutionClass())
                .isEqualTo(SubstitutionClass.IDENTICAL);
        assertThat(row.pocketMemberA()).isNull();
        assertThat(row.structurallyEquivalent()).isNull();
    }

    @Test
    void aOnlyContactWhenOnlySideAContacts() {
        Structure receptorA = receptor(1, "ALA", "PHE");
        Structure receptorB = receptor(1, "ALA", "PHE");

        LigandContactAlignment alignment = analyzer.align(
                receptorA, pose(), List.of(contact(2)),
                receptorB, pose(), List.of(),
                null, null
        );

        assertThat(alignment.contacts()).hasSize(1);
        assertThat(alignment.contacts().get(0).differentialType())
                .isEqualTo(DifferentialContactType.A_ONLY_CONTACT);
        assertThat(alignment.contacts().get(0).contactB()).isFalse();
        assertThat(alignment.contacts().get(0).minDistanceB()).isNull();
    }

    @Test
    void bOnlyContactWhenOnlySideBContacts() {
        Structure receptorA = receptor(1, "ALA", "PHE");
        Structure receptorB = receptor(1, "ALA", "PHE");

        LigandContactAlignment alignment = analyzer.align(
                receptorA, pose(), List.of(),
                receptorB, pose(), List.of(contact(2)),
                null, null
        );

        assertThat(alignment.contacts()).hasSize(1);
        assertThat(alignment.contacts().get(0).differentialType())
                .isEqualTo(DifferentialContactType.B_ONLY_CONTACT);
    }

    @Test
    void contactBothDifferentResidueReportsChemistryDelta() {
        Structure receptorA = receptor(1, "ALA", "PHE");
        Structure receptorB = receptor(1, "ALA", "LEU");

        LigandContactAlignment alignment = analyzer.align(
                receptorA, pose(), List.of(contact(2)),
                receptorB, pose(), List.of(contact(2)),
                null, null
        );

        AlignedLigandContact row = alignment.contacts().get(0);
        assertThat(row.differentialType()).isEqualTo(
                DifferentialContactType.CONTACT_BOTH_DIFFERENT_RESIDUE);
        assertThat(row.substitutionClass())
                .isEqualTo(SubstitutionClass.RADICAL);
        assertThat(row.conservative()).isFalse();
        assertThat(row.chemistryA())
                .contains(ResidueCategory.AROMATIC);
        assertThat(row.chemistryB())
                .doesNotContain(ResidueCategory.AROMATIC);
    }

    @Test
    void nonContactDifferenceWhenResiduesDifferWithoutContacts() {
        Structure receptorA = receptor(1, "ALA", "PHE");
        Structure receptorB = receptor(1, "ALA", "LEU");

        LigandContactAlignment alignment = analyzer.align(
                receptorA, pose(), List.of(),
                receptorB, pose(), List.of(),
                null, null
        );

        assertThat(alignment.contacts()).hasSize(1);
        AlignedLigandContact row = alignment.contacts().get(0);
        assertThat(row.differentialType())
                .isEqualTo(DifferentialContactType.NONCONTACT_DIFFERENCE);
        assertThat(row.contactA()).isFalse();
        assertThat(row.contactB()).isFalse();
    }

    @Test
    void correspondenceFollowsTheAlignmentNotResidueNumbers() {
        // B carries an N-terminal insert (ALA 7), so the homologous
        // PHE is residue 11 in A but residue 9 in B.
        Structure receptorA = receptor(10, "ALA", "PHE", "GLY");
        Structure receptorB = receptor(7, "ALA", "ALA", "PHE", "GLY");

        LigandContactAlignment alignment = analyzer.align(
                receptorA, pose(), List.of(contact(11)),
                receptorB, pose(), List.of(contact(9)),
                null, null
        );

        assertThat(alignment.contacts()).hasSize(1);

        AlignedLigandContact row = alignment.contacts().get(0);
        assertThat(row.differentialType())
                .isEqualTo(DifferentialContactType.CONSERVED_CONTACT);
        assertThat(row.residueAId().residueNumber()).isEqualTo(11);
        // Raw-number matching would have claimed B11; the alignment
        // correctly maps A11 onto B9.
        assertThat(row.residueBId().residueNumber()).isEqualTo(9);
    }

    @Test
    void unmappedContactResidueGetsAnUnmappedRow() {
        // A's C-terminal TRP 4 has no counterpart in B.
        Structure receptorA = receptor(1, "ALA", "PHE", "GLY", "TRP");
        Structure receptorB = receptor(1, "ALA", "PHE", "GLY");

        LigandContactAlignment alignment = analyzer.align(
                receptorA, pose(), List.of(contact(4)),
                receptorB, pose(), List.of(),
                null, null
        );

        assertThat(alignment.contacts()).hasSize(1);

        AlignedLigandContact row = alignment.contacts().get(0);
        assertThat(row.differentialType())
                .isEqualTo(DifferentialContactType.UNMAPPED);
        assertThat(row.residueA()).isEqualTo("TRP");
        assertThat(row.residueAId().residueNumber()).isEqualTo(4);
        assertThat(row.contactA()).isTrue();
        assertThat(row.residueB()).isNull();
        assertThat(row.residueBId()).isNull();
        assertThat(row.contactB()).isFalse();
        assertThat(row.substitutionClass()).isNull();
        assertThat(row.chemistryB()).isEmpty();
    }

    @Test
    void pocketMembershipAndStructuralEquivalenceUseThePockets() {
        Structure receptorA = receptor(1, "ALA", "PHE");
        Structure receptorB = receptor(1, "ALA", "LEU");
        Pocket pocketA = pocket(List.of(
                new ResidueId("A", 1, null),
                new ResidueId("A", 2, null)));
        Pocket pocketB = pocket(List.of(
                new ResidueId("A", 2, null)));

        LigandContactAlignment alignment = analyzer.align(
                receptorA, pose(), List.of(contact(2)),
                receptorB, pose(), List.of(contact(2)),
                pocketA, pocketB
        );

        AlignedLigandContact row = alignment.contacts().get(0);
        assertThat(row.pocketMemberA()).isTrue();
        assertThat(row.pocketMemberB()).isTrue();
        assertThat(row.structurallyEquivalent()).isTrue();
    }

    static Structure receptor(int firstNumber, String... names) {
        List<Residue> residues = new ArrayList<>();

        for (int index = 0; index < names.length; index++) {
            residues.add(new Residue(
                    names[index],
                    firstNumber + index,
                    List.of(atom(1000 + index, "CA",
                            new Point3D(index * 3.8, 0, 0)))
            ));
        }

        return new Structure(List.of(new Chain("A", residues)));
    }

    static Ligand pose() {
        Residue residue = new Residue("LIG", 1,
                List.of(atom(1, "C1", new Point3D(0, 0, 0))));
        Structure structure = new Structure(
                List.of(new Chain("L", List.of(residue))));
        return new Ligand("L", "L", null, null, null, null, structure);
    }

    static LigandContact contact(int residueNumber) {
        return new LigandContact(
                atom(5001, "C1", new Point3D(0, 0, 0)),
                atom(5002, "CA", new Point3D(0, 0, 0)),
                new ResidueId("A", residueNumber, null),
                3.0,
                ContactType.DIRECT
        );
    }

    static Pocket pocket(List<ResidueId> residues) {
        return new Pocket(
                new PocketId("1"),
                "pocket-1",
                PocketSource.FPOCKET,
                new Point3D(0, 0, 0),
                residues,
                List.of(),
                Optional.empty(),
                Optional.of(new AlphaSphereSet(List.of(
                        new AlphaSphere(1L, new Point3D(0, 0, 0), 2.0)))),
                Map.of()
        );
    }

    static Atom atom(int serial, String name, Point3D position) {
        return Atom.builder()
                .pdbSerial(serial)
                .name(name)
                .position(position)
                .charge(0.0)
                .occupancy(1.0)
                .bFactor(0.0)
                .element(Element.C)
                .build();
    }
}
