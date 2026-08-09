package totah.lab.proteus.protein.mutation;

import org.junit.jupiter.api.Test;
import totah.lab.gaia.chemistry.BondOrder;
import totah.lab.gaia.chemistry.Element;
import totah.lab.gaia.geometry.Point3D;
import totah.lab.gaia.structure.Atom;
import totah.lab.gaia.structure.AtomReference;
import totah.lab.gaia.structure.Bond;
import totah.lab.gaia.structure.Chain;
import totah.lab.gaia.structure.ConnectivityMetadata;
import totah.lab.gaia.structure.ConnectivityProvenance;
import totah.lab.gaia.structure.Residue;
import totah.lab.gaia.structure.ResidueId;
import totah.lab.gaia.structure.Structure;
import totah.lab.proteus.protein.variant.ProteinVariant;
import totah.lab.proteus.validation.ValidationCode;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProteinMutationOperationTest {

    @Test
    void rebuiltStructureKeepsTemplateBondOrders() {
        MutationResult result = new ProteinMutationOperation().apply(
                structure(),
                new Mutation(new ResidueId("A", 1, null), "ALA", "PHE"),
                MutationContext.defaults());

        assertTrue(result.appliedMutation().isPresent());

        List<Bond> bonds = result.structure().bonds();
        assertEquals(BondOrder.AROMATIC, orderOf(bonds, "CG", "CD1"));
        assertEquals(BondOrder.AROMATIC, orderOf(bonds, "CE1", "CZ"));
        assertEquals(BondOrder.SINGLE, orderOf(bonds, "CA", "CB"));
        assertEquals(BondOrder.SINGLE, orderOf(bonds, "CB", "CG"));
    }

    @Test
    void asparagineCarbonylKeepsDoubleBondOrder() {
        MutationResult result = new ProteinMutationOperation().apply(
                structure(),
                new Mutation(new ResidueId("A", 1, null), "ALA", "ASN"),
                MutationContext.defaults());

        assertTrue(result.appliedMutation().isPresent());
        assertEquals(BondOrder.DOUBLE,
                orderOf(result.structure().bonds(), "CG", "OD1"));
    }

    @Test
    void newSideChainSerialsDoNotCollideWithOtherResidues() {
        MutationResult result = new ProteinMutationOperation().apply(
                structure(),
                new Mutation(new ResidueId("A", 1, null), "ALA", "PHE"),
                MutationContext.defaults());

        assertTrue(result.appliedMutation().isPresent());

        Set<Integer> serials = new HashSet<>();
        int maxOriginalSerial = 9;
        int newAtoms = 0;
        for (Chain chain : result.structure().getChains()) {
            for (Residue residue : chain.residues()) {
                for (Atom atom : residue.getAtoms()) {
                    assertTrue(serials.add(atom.getPdbSerial()),
                            "duplicate pdb serial " + atom.getPdbSerial());
                    if (residue.getNumber() == 1
                            && atom.getPdbSerial() > maxOriginalSerial) {
                        newAtoms++;
                    }
                }
            }
        }
        // PHE contributes seven new side-chain atoms, all numbered above
        // the structure-wide maximum serial of the input structure.
        assertEquals(7, newAtoms);
    }

    @Test
    void alanineToTryptophanBuildsFusedIndoleSideChain() {
        MutationResult result = new ProteinMutationOperation().apply(
                structure(),
                new Mutation(new ResidueId("A", 1, null), "ALA", "TRP"),
                MutationContext.defaults());

        assertThat(result.appliedMutation()).isPresent();
        Residue mutated = result.structure().residue(new ResidueId("A", 1, null));
        assertThat(mutated.getName()).isEqualTo("TRP");
        // Four backbone atoms plus the ten TRP side-chain atoms of the
        // fused indole (CB, CG, the CD1-NE1 five-membered ring and the
        // CD2-CE3-CZ3-CH2-CZ2-CE2 benzene ring).
        assertThat(mutated.getAtoms())
                .extracting(Atom::getName)
                .containsExactly("N", "CA", "C", "O",
                        "CB", "CG", "CD1", "CD2", "NE1", "CE2",
                        "CE3", "CZ3", "CH2", "CZ2");
        assertEquals(BondOrder.SINGLE,
                orderOf(result.structure().bonds(), "CA", "CB"));
        assertEquals(BondOrder.SINGLE,
                orderOf(result.structure().bonds(), "CB", "CG"));
        assertEquals(BondOrder.AROMATIC,
                orderOf(result.structure().bonds(), "CG", "CD1"));
        assertEquals(BondOrder.AROMATIC,
                orderOf(result.structure().bonds(), "CD1", "NE1"));
        assertEquals(BondOrder.AROMATIC,
                orderOf(result.structure().bonds(), "NE1", "CE2"));
        assertEquals(BondOrder.AROMATIC,
                orderOf(result.structure().bonds(), "CZ2", "CE2"));
    }

    @Test
    void alanineToValineBuildsBranchedSideChain() {
        MutationResult result = new ProteinMutationOperation().apply(
                structure(),
                new Mutation(new ResidueId("A", 1, null), "ALA", "VAL"),
                MutationContext.defaults());

        assertThat(result.appliedMutation()).isPresent();
        Residue mutated = result.structure().residue(new ResidueId("A", 1, null));
        assertThat(mutated.getName()).isEqualTo("VAL");
        // Four backbone atoms plus the three VAL side-chain atoms.
        assertThat(mutated.getAtoms())
                .extracting(Atom::getName)
                .containsExactly("N", "CA", "C", "O", "CB", "CG1", "CG2");
        assertEquals(BondOrder.SINGLE,
                orderOf(result.structure().bonds(), "CB", "CG1"));
        assertEquals(BondOrder.SINGLE,
                orderOf(result.structure().bonds(), "CB", "CG2"));
    }

    @Test
    void alanineToMethionineBuildsThioetherSideChain() {
        MutationResult result = new ProteinMutationOperation().apply(
                structure(),
                new Mutation(new ResidueId("A", 1, null), "ALA", "MET"),
                MutationContext.defaults());

        assertThat(result.appliedMutation()).isPresent();
        Residue mutated = result.structure().residue(new ResidueId("A", 1, null));
        assertThat(mutated.getName()).isEqualTo("MET");
        // Four backbone atoms plus the four MET side-chain atoms.
        assertThat(mutated.getAtoms())
                .extracting(Atom::getName)
                .containsExactly("N", "CA", "C", "O", "CB", "CG", "SD", "CE");
        assertEquals(BondOrder.SINGLE,
                orderOf(result.structure().bonds(), "CB", "CG"));
        assertEquals(BondOrder.SINGLE,
                orderOf(result.structure().bonds(), "CG", "SD"));
        assertEquals(BondOrder.SINGLE,
                orderOf(result.structure().bonds(), "SD", "CE"));
    }

    @Test
    void alanineToLeucineBuildsBranchedSideChain() {
        MutationResult result = new ProteinMutationOperation().apply(
                structure(),
                new Mutation(new ResidueId("A", 1, null), "ALA", "LEU"),
                MutationContext.defaults());

        assertThat(result.appliedMutation()).isPresent();
        Residue mutated = result.structure().residue(new ResidueId("A", 1, null));
        assertThat(mutated.getName()).isEqualTo("LEU");
        // Four backbone atoms plus the four LEU side-chain atoms.
        assertThat(mutated.getAtoms())
                .extracting(Atom::getName)
                .containsExactly("N", "CA", "C", "O", "CB", "CG", "CD1", "CD2");
        assertEquals(BondOrder.SINGLE,
                orderOf(result.structure().bonds(), "CB", "CG"));
        assertEquals(BondOrder.SINGLE,
                orderOf(result.structure().bonds(), "CG", "CD1"));
        assertEquals(BondOrder.SINGLE,
                orderOf(result.structure().bonds(), "CG", "CD2"));
    }

    @Test
    void singleSubstitutionPreservesBackboneAndResidueIdentity() {
        Structure source = insertionCodedStructure();
        Residue original = source.residue(new ResidueId("A", 1, 'A'));

        MutationResult result = new ProteinMutationOperation().apply(
                source,
                new Mutation(new ResidueId("A", 1, 'A'), "ALA", "PHE"),
                MutationContext.defaults());

        assertThat(result.appliedMutation()).isPresent();
        Residue mutated = result.structure().residue(new ResidueId("A", 1, 'A'));
        assertThat(mutated.getName()).isEqualTo("PHE");
        assertThat(mutated.getNumber()).isEqualTo(1);
        assertThat(mutated.getInsertionCode()).isEqualTo('A');
        for (String backbone : List.of("N", "CA", "C", "O")) {
            assertThat(mutated.findAtom(backbone)).isPresent();
            assertThat(mutated.findAtom(backbone).orElseThrow().getPosition())
                    .as("backbone atom %s keeps its coordinates", backbone)
                    .isEqualTo(original.findAtom(backbone).orElseThrow().getPosition());
        }
        assertThat(result.structure().getChainCount()).isEqualTo(1);
        assertThat(result.structure().findChain("A").orElseThrow().residueCount())
                .isEqualTo(2);
    }

    @Test
    void glycineToPhenylalanineAddsSideChainAtoms() {
        MutationResult result = new ProteinMutationOperation().apply(
                structure(),
                new Mutation(new ResidueId("A", 2, null), "GLY", "PHE"),
                MutationContext.defaults());

        assertThat(result.appliedMutation()).isPresent();
        Residue mutated = result.structure().residue(new ResidueId("A", 2, null));
        assertThat(mutated.getName()).isEqualTo("PHE");
        // Four backbone atoms plus the seven PHE side-chain atoms.
        assertThat(mutated.getAtomCount()).isEqualTo(4 + 7);
    }

    @Test
    void phenylalanineToGlycineStripsSideChainAtoms() {
        Residue phenylalanine = new Residue("PHE", 1, List.of(
                atom("N", Element.N, 1, 0.0, 0.0, 0.0),
                atom("CA", Element.C, 2, 1.45, 0.0, 0.0),
                atom("C", Element.C, 3, 2.05, 1.35, 0.0),
                atom("O", Element.O, 4, 1.45, 2.40, 0.0),
                atom("CB", Element.C, 5, 2.10, -1.20, 0.80),
                atom("CG", Element.C, 6, 3.60, -1.30, 0.90)));
        Structure source = new Structure(
                List.of(new Chain("A", List.of(phenylalanine))),
                List.of(),
                new ConnectivityMetadata(ConnectivityProvenance.EXPLICIT, List.of()));

        MutationResult result = new ProteinMutationOperation().apply(
                source,
                new Mutation(new ResidueId("A", 1, null), "PHE", "GLY"),
                MutationContext.defaults());

        assertThat(result.appliedMutation()).isPresent();
        Residue mutated = result.structure().residue(new ResidueId("A", 1, null));
        assertThat(mutated.getName()).isEqualTo("GLY");
        assertThat(mutated.getAtomCount()).isEqualTo(4);
        assertThat(mutated.getAtoms())
                .allMatch(atom -> List.of("N", "CA", "C", "O").contains(atom.getName()));
    }

    @Test
    void mutationSetAppliesAllMutationsInOrder() {
        MutationSet set = new MutationSet("mettl7b-panel", "METTL7B",
                List.of(
                        new Mutation(new ResidueId("A", 1, null), "ALA", "PHE"),
                        new Mutation(new ResidueId("A", 2, null), "GLY", "SER")),
                MutationPurpose.CYSTEINE_MECHANISM);

        ProteinVariant variant = new ProteinMutationOperation().apply(
                new MutationRequest(structure(), set));

        assertThat(variant.id()).isEqualTo("mettl7b-panel");
        assertThat(variant.parentStructureId()).isEqualTo("METTL7B");
        assertThat(variant.mutationSet()).isEqualTo(set);
        assertThat(variant.structure().residue(new ResidueId("A", 1, null)).getName())
                .isEqualTo("PHE");
        assertThat(variant.structure().residue(new ResidueId("A", 2, null)).getName())
                .isEqualTo("SER");
        assertThat(variant.provenance().parentStructureId()).isEqualTo("METTL7B");
        assertThat(variant.provenance().appliedMutations())
                .extracting(AppliedMutation::replacementResidueName)
                .containsExactly("PHE", "SER");
        assertThat(variant.provenance().warnings()).isEmpty();
        assertThat(variant.provenance().rotamerMethod()).isNotBlank();
        assertThat(variant.provenance().softwareVersion()).isNotBlank();
        assertThat(variant.provenance().timestamp()).isNotNull();
    }

    @Test
    void declaredWildTypeMismatchIsRejected() {
        MutationResult result = new ProteinMutationOperation().apply(
                structure(),
                new Mutation(new ResidueId("A", 1, null), "GLY", "PHE"),
                MutationContext.defaults());

        assertThat(result.appliedMutation()).isEmpty();
        assertThat(result.validation().hasErrors()).isTrue();
        assertThat(result.validation().issues())
                .anyMatch(issue -> issue.code()
                        == ValidationCode.MUTATION_WILD_TYPE_MISMATCH);

        MutationSet set = new MutationSet("bad-set", "METTL7B",
                List.of(new Mutation(new ResidueId("A", 1, null), "GLY", "PHE")),
                MutationPurpose.SELECTIVITY_VALIDATION);
        assertThatThrownBy(() -> new ProteinMutationOperation().apply(
                new MutationRequest(structure(), set)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void disulfideBondedCysteineIsRejected() {
        Residue cysteine = new Residue("CYS", 203, List.of(
                atom("N", Element.N, 1, 0.0, 0.0, 0.0),
                atom("CA", Element.C, 2, 1.45, 0.0, 0.0),
                atom("C", Element.C, 3, 2.05, 1.35, 0.0),
                atom("SG", Element.S, 4, 2.10, -1.20, 0.80)));
        Residue partner = new Residue("CYS", 210, List.of(
                atom("N", Element.N, 5, 4.0, 0.0, 0.0),
                atom("CA", Element.C, 6, 5.45, 0.0, 0.0),
                atom("C", Element.C, 7, 6.05, 1.35, 0.0),
                atom("SG", Element.S, 8, 4.10, -1.20, 0.80)));
        Structure source = new Structure(
                List.of(new Chain("A", List.of(cysteine, partner))),
                List.of(new Bond(
                        new AtomReference("A", 203, ' ', "SG"),
                        new AtomReference("A", 210, ' ', "SG"),
                        BondOrder.SINGLE)),
                new ConnectivityMetadata(ConnectivityProvenance.EXPLICIT, List.of()));

        MutationResult result = new ProteinMutationOperation().apply(
                source,
                new Mutation(new ResidueId("A", 203, null), "CYS", "ALA"),
                MutationContext.defaults());

        assertThat(result.appliedMutation()).isEmpty();
        assertThat(result.validation().issues())
                .anyMatch(issue -> issue.code()
                        == ValidationCode.MUTATION_EXPLICIT_COVALENT_BOND);
        // The failed mutation leaves the source structure untouched.
        assertThat(result.structure()).isSameAs(source);
    }

    @Test
    void applicationIsDeterministic() {
        MutationSet set = new MutationSet("mettl7b-panel", "METTL7B",
                List.of(
                        new Mutation(new ResidueId("A", 1, null), "ALA", "PHE"),
                        new Mutation(new ResidueId("A", 2, null), "GLY", "TYR")),
                MutationPurpose.COMBINATION_ANALYSIS);

        ProteinVariant first = new ProteinMutationOperation().apply(
                new MutationRequest(structure(), set));
        ProteinVariant second = new ProteinMutationOperation().apply(
                new MutationRequest(structure(), set));

        assertThat(atomPositions(second.structure()))
                .isEqualTo(atomPositions(first.structure()));
    }

    private static List<Point3D> atomPositions(Structure structure) {
        return structure.getChains().stream()
                .flatMap(chain -> chain.residues().stream())
                .flatMap(residue -> residue.getAtoms().stream())
                .map(Atom::getPosition)
                .toList();
    }

    private BondOrder orderOf(List<Bond> bonds, String first, String second) {
        return bonds.stream()
                .filter(bond -> matches(bond, first, second))
                .map(Bond::order)
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "no bond " + first + "-" + second));
    }

    private boolean matches(Bond bond, String first, String second) {
        return (bond.atom1().atomName().equals(first)
                && bond.atom2().atomName().equals(second))
                || (bond.atom1().atomName().equals(second)
                && bond.atom2().atomName().equals(first));
    }

    private Structure structure() {
        Residue alanine = new Residue("ALA", 1, List.of(
                atom("N", Element.N, 1, 0.0, 0.0, 0.0),
                atom("CA", Element.C, 2, 1.45, 0.0, 0.0),
                atom("C", Element.C, 3, 2.05, 1.35, 0.0),
                atom("O", Element.O, 4, 1.45, 2.40, 0.0),
                atom("CB", Element.C, 5, 2.10, -1.20, 0.80)));
        Residue glycine = new Residue("GLY", 2, List.of(
                atom("N", Element.N, 6, 3.50, 1.80, 0.0),
                atom("CA", Element.C, 7, 4.95, 1.80, 0.0),
                atom("C", Element.C, 8, 5.55, 3.15, 0.0),
                atom("O", Element.O, 9, 4.95, 4.20, 0.0)));
        return new Structure(
                List.of(new Chain("A", List.of(alanine, glycine))),
                List.of(),
                new ConnectivityMetadata(
                        ConnectivityProvenance.EXPLICIT, List.of()));
    }

    private Structure insertionCodedStructure() {
        Residue alanine = new Residue("ALA", 1, 'A', List.of(
                atom("N", Element.N, 1, 0.0, 0.0, 0.0),
                atom("CA", Element.C, 2, 1.45, 0.0, 0.0),
                atom("C", Element.C, 3, 2.05, 1.35, 0.0),
                atom("O", Element.O, 4, 1.45, 2.40, 0.0),
                atom("CB", Element.C, 5, 2.10, -1.20, 0.80)));
        Residue glycine = new Residue("GLY", 2, List.of(
                atom("N", Element.N, 6, 3.50, 1.80, 0.0),
                atom("CA", Element.C, 7, 4.95, 1.80, 0.0),
                atom("C", Element.C, 8, 5.55, 3.15, 0.0),
                atom("O", Element.O, 9, 4.95, 4.20, 0.0)));
        return new Structure(
                List.of(new Chain("A", List.of(alanine, glycine))),
                List.of(),
                new ConnectivityMetadata(
                        ConnectivityProvenance.EXPLICIT, List.of()));
    }

    private Atom atom(
            String name,
            Element element,
            int serial,
            double x,
            double y,
            double z) {

        return Atom.builder()
                .pdbSerial(serial)
                .name(name)
                .element(element)
                .position(new Point3D(x, y, z))
                .occupancy(1.0)
                .build();
    }
}
