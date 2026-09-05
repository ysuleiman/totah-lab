package totah.lab.athena.interaction;

import org.junit.jupiter.api.Test;
import totah.lab.gaia.chemistry.BondOrder;
import totah.lab.gaia.chemistry.Element;
import totah.lab.gaia.structure.Atom;
import totah.lab.gaia.structure.AtomReference;
import totah.lab.gaia.structure.Bond;
import totah.lab.gaia.structure.Chain;
import totah.lab.gaia.structure.ConnectivityProvenance;
import totah.lab.gaia.structure.Residue;
import totah.lab.gaia.structure.ResidueId;
import totah.lab.gaia.structure.Structure;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static totah.lab.athena.interaction.InteractionFixtures.atom;
import static totah.lab.athena.interaction.InteractionFixtures.bond;
import static totah.lab.athena.interaction.InteractionFixtures.chain;
import static totah.lab.athena.interaction.InteractionFixtures.residue;

/**
 * Synthetic complex exercising all six interaction types through real
 * perception:
 * <ul>
 *   <li>PHE A:43 ring x ligand aromatic ring at z=3.4 (parallel
 *   pi-stack; the ring-atom hydrophobic contacts it generates raw must
 *   be refined away);</li>
 *   <li>ARG A:55 and LYS A:50 x ligand carboxylate (two salt
 *   bridges);</li>
 *   <li>LYS A:50 NZ above the ligand ring (pi-cation);</li>
 *   <li>SER A:80 OG acceptor x ligand N1-H1 donor (hydrogen bond);</li>
 *   <li>ALA A:90 CB x ligand methyl (hydrophobic contact);</li>
 *   <li>GLY A:70 backbone O x ligand Cl (halogen bond, exact
 *   angles);</li>
 *   <li>PHE A:100 far away (must be removed by binding-site
 *   preselection);</li>
 *   <li>SAM S:900 (separate cofactor structure) acceptor x ligand
 *   N2-H2 donor (environment-side hydrogen bond).</li>
 * </ul>
 */
class InteractionProfilerTest {

    private final InteractionProfiler profiler = new InteractionProfiler();

    private static Atom c(int serial, String name, double x, double y,
            double z) {
        return atom(serial, name, Element.C, x, y, z);
    }

    /** Receptor with the full six-type fixture, EXPLICIT connectivity. */
    private static Structure receptor() {
        List<Atom> phe43 = List.of(
                c(1, "CG", 1.4, 0, 0),
                c(2, "CD1", 0.7, 1.2124, 0),
                c(3, "CD2", 0.7, -1.2124, 0),
                c(4, "CE1", -0.7, 1.2124, 0),
                c(5, "CE2", -0.7, -1.2124, 0),
                c(6, "CZ", -1.4, 0, 0));
        List<Atom> lys50 = List.of(
                atom(7, "NZ", Element.N, 0, 0, 4.5));
        List<Atom> arg55 = List.of(
                atom(8, "NE", Element.N, 9.4, 0, 3.4),
                atom(9, "NH1", Element.N, 8.4, 0.9, 3.4),
                atom(10, "NH2", Element.N, 8.4, -0.9, 3.4));
        List<Atom> ser80 = List.of(
                atom(11, "OG", Element.O, "OA", 0, 53, 0));
        List<Atom> gly70 = List.of(
                c(12, "C", 3.792, 21.575, 0),
                atom(13, "O", Element.O, 3.188, 20.279, 0));
        List<Atom> ala90 = List.of(
                atom(14, "N", Element.N, 4.5, -7.9, 3.4),
                c(15, "CA", 4.5, -6.5, 3.4),
                c(16, "CB", 4.5, -5.0, 3.4),
                c(17, "C", 5.9, -6.9, 3.4),
                atom(18, "O", Element.O, 6.9, -6.9, 3.4));
        List<Atom> phe100 = List.of(
                c(19, "CG", 501.4, 500, 500),
                c(20, "CD1", 500.7, 501.2124, 500),
                c(21, "CD2", 500.7, 498.7876, 500),
                c(22, "CE1", 499.3, 501.2124, 500),
                c(23, "CE2", 499.3, 498.7876, 500),
                c(24, "CZ", 498.6, 500, 500));

        List<Bond> bonds = List.of(
                bond("A", 43, "CG", "CD1"), bond("A", 43, "CD1", "CE1"),
                bond("A", 43, "CE1", "CZ"), bond("A", 43, "CZ", "CE2"),
                bond("A", 43, "CE2", "CD2"), bond("A", 43, "CD2", "CG"),
                bond("A", 70, "C", "O"),
                bond("A", 90, "N", "CA"), bond("A", 90, "CA", "CB"),
                bond("A", 90, "CA", "C"), bond("A", 90, "C", "O"));

        return new Structure(List.of(chain("A",
                residue("PHE", 43, phe43),
                residue("LYS", 50, lys50),
                residue("ARG", 55, arg55),
                residue("GLY", 70, gly70),
                residue("SER", 80, ser80),
                residue("ALA", 90, ala90),
                residue("PHE", 100, phe100))),
                bonds, ConnectivityProvenance.EXPLICIT);
    }

    /** Ligand: aromatic ring + carboxylate + methyl + Cl donor + two N-H donors. */
    private static Structure ligand() {
        List<Atom> atoms = List.of(
                c(101, "CR1", 1.4, 0, 3.4),
                c(102, "CR2", 0.7, 1.2124, 3.4),
                c(103, "CR3", -0.7, 1.2124, 3.4),
                c(104, "CR4", -1.4, 0, 3.4),
                c(105, "CR5", -0.7, -1.2124, 3.4),
                c(106, "CR6", 0.7, -1.2124, 3.4),
                c(107, "CC", 3.5, 0, 3.4),
                atom(108, "OC1", Element.O, 4.1, 0.9, 3.4),
                atom(109, "OC2", Element.O, 4.1, -0.9, 3.4),
                c(110, "CM", 4.5, -1.5, 3.4),
                c(111, "CX", -1.7, 20, 0),
                atom(112, "CL1", Element.CL, 0, 20, 0),
                atom(113, "N1", Element.N, "N", 0, 50, 0),
                atom(114, "H1", Element.H, "HD", 0, 51, 0),
                atom(115, "N2", Element.N, "N", 0, 60, 0),
                atom(116, "H2", Element.H, "HD", 0, 61, 0));
        List<Bond> bonds = List.of(
                aromatic("CR1", "CR2"), aromatic("CR2", "CR3"),
                aromatic("CR3", "CR4"), aromatic("CR4", "CR5"),
                aromatic("CR5", "CR6"), aromatic("CR6", "CR1"),
                bond("L", 501, "CR1", "CC"),
                bond("L", 501, "CC", "OC1"), bond("L", 501, "CC", "OC2"),
                bond("L", 501, "CC", "CM"),
                bond("L", 501, "CX", "CL1"),
                bond("L", 501, "N1", "H1"),
                bond("L", 501, "N2", "H2"));
        return new Structure(List.of(chain("L",
                residue("LIG", 501, atoms))),
                bonds, ConnectivityProvenance.EXPLICIT);
    }

    /** SAM as a separate fixed cofactor: acceptor O9 for the N2-H2 donor. */
    private static Structure sam() {
        return new Structure(List.of(chain("S",
                residue("SAM", 900, List.of(
                        atom(201, "O9", Element.O, "OA", 0, 63, 0),
                        c(202, "C9", 1.4, 63, 0))))),
                List.of(bond("S", 900, "O9", "C9")),
                ConnectivityProvenance.EXPLICIT);
    }

    private static Bond aromatic(String atom1, String atom2) {
        return new Bond(
                new AtomReference("L", 501, ' ', atom1),
                new AtomReference("L", 501, ' ', atom2),
                BondOrder.AROMATIC);
    }

    private static List<Interaction> ofType(
            InteractionProfile profile, InteractionType type) {

        return profile.interactions(type);
    }

    @Test
    void profilesAllSixInteractionTypesWithRefinementApplied() {
        InteractionProfile profile = profiler.profile(receptor(), ligand());

        assertThat(profile.thresholds().provenance()).isEqualTo(
                InteractionThresholds.ATHENA_DEFAULTS_PROVENANCE);
        assertThat(profile.cofactorResidues()).isEmpty();
        assertThat(profile.anyPerceptionDegraded()).isFalse();

        assertThat(ofType(profile, InteractionType.SALT_BRIDGE))
                .extracting(Interaction::residue)
                .containsExactlyInAnyOrder(
                        new ResidueId("A", 55, null),
                        new ResidueId("A", 50, null));
        assertThat(ofType(profile, InteractionType.HYDROGEN_BOND))
                .singleElement()
                .extracting(Interaction::residue)
                .isEqualTo(new ResidueId("A", 80, null));
        assertThat(ofType(profile, InteractionType.PI_STACK_PARALLEL))
                .singleElement()
                .extracting(Interaction::residue)
                .isEqualTo(new ResidueId("A", 43, null));
        assertThat(ofType(profile, InteractionType.PI_CATION))
                .singleElement()
                .extracting(Interaction::residue)
                .isEqualTo(new ResidueId("A", 50, null));
        assertThat(ofType(profile, InteractionType.HYDROPHOBIC_CONTACT))
                .singleElement()
                .extracting(Interaction::residue)
                .isEqualTo(new ResidueId("A", 90, null));
        assertThat(ofType(profile, InteractionType.HALOGEN_BOND))
                .singleElement()
                .extracting(Interaction::residue)
                .isEqualTo(new ResidueId("A", 70, null));

        assertThat(profile.interactions()).hasSize(7);

        // Refinement applied: raw contains the 18 ring-atom hydrophobic
        // contacts between the stacked rings (each ring carbon is within
        // 4.0 A of its counterpart and the two adjacent ones); refined
        // drops all of them.
        assertThat(profile.rawInteractions()).hasSize(25);
        assertThat(profile.rawInteractions())
                .filteredOn(i -> i.type()
                        == InteractionType.HYDROPHOBIC_CONTACT)
                .hasSize(19);
        assertThat(ofType(profile, InteractionType.HYDROPHOBIC_CONTACT))
                .noneMatch(i -> i.residue().residueNumber() == 43);
    }

    @Test
    void bindingSitePreselectionDropsFarResidues() {
        InteractionProfile profile = profiler.profile(receptor(), ligand());

        // PHE A:100 carries a full ring template but sits ~500 A away;
        // preselection must remove it before perception.
        PerceptionSummary receptorSummary = profile.perception().stream()
                .filter(s -> s.side().equals(PerceptionSummary.RECEPTOR))
                .findFirst()
                .orElseThrow();
        assertThat(receptorSummary.ringCount()).isEqualTo(1);
        assertThat(receptorSummary.hydrophobicProvenance().name())
                .isEqualTo("BOND_GRAPH");
        assertThat(profile.interactions())
                .noneMatch(i -> i.residue().residueNumber() == 100);
    }

    @Test
    void separateCofactorIsProfiledAsEnvironmentNeverAsLigand() {
        InteractionProfile profile = profiler.profile(
                receptor(), ligand(), sam());

        // Receptor-side results unchanged; cofactor adds its own HB.
        assertThat(profile.interactions()).hasSize(8);
        assertThat(profile.cofactorResidues())
                .containsExactly(new ResidueId("S", 900, null));

        Interaction samBond = ofType(profile, InteractionType.HYDROGEN_BOND)
                .stream()
                .filter(i -> i.residue().chainId().equals("S"))
                .findFirst()
                .orElseThrow();
        assertThat(samBond.residue())
                .isEqualTo(new ResidueId("S", 900, null));
        assertThat(samBond.proteinAtoms())
                .extracting(Atom::getName)
                .containsExactly("O9");
        assertThat(samBond.ligandAtoms())
                .extracting(Atom::getName)
                .containsExactly("N2", "H2");

        assertThat(profile.perception())
                .extracting(PerceptionSummary::side)
                .containsExactly(PerceptionSummary.RECEPTOR,
                        PerceptionSummary.LIGAND,
                        PerceptionSummary.COFACTOR);

        // Without the cofactor argument there is no SAM interaction.
        assertThat(profiler.profile(receptor(), ligand()).interactions())
                .noneMatch(i -> i.residue().chainId().equals("S"));
    }

    @Test
    void mergedComplexWithLigandSelectorYieldsSameInteractions() {
        Structure receptor = receptor();
        Structure sam = sam();
        Structure ligand = ligand();
        List<Chain> chains = new ArrayList<>(receptor.getChains());
        chains.addAll(sam.getChains());
        chains.addAll(ligand.getChains());
        List<Bond> bonds = new ArrayList<>(receptor.getBonds());
        bonds.addAll(sam.getBonds());
        bonds.addAll(ligand.getBonds());
        Structure complex = new Structure(chains, bonds,
                ConnectivityProvenance.EXPLICIT);

        InteractionProfile profile = profiler.profileComplex(
                complex, id -> id.chainId().equals("L"));

        // The merged structure profiles SAM as an ordinary environment
        // residue: its HB appears with the SAM residue id and the
        // cofactor set stays empty (nothing was passed separately).
        assertThat(profile.cofactorResidues()).isEmpty();
        assertThat(profile.interactions()).hasSize(8);
        assertThat(ofType(profile, InteractionType.HYDROGEN_BOND))
                .extracting(Interaction::residue)
                .containsExactlyInAnyOrder(new ResidueId("A", 80, null),
                        new ResidueId("S", 900, null));
    }

    @Test
    void degradedPerceptionIsSurfaced() {
        // No bonds anywhere: connectivity ABSENT, hydrophobic perception
        // degrades to AD4 typing, halogen detection stays empty.
        Structure receptor = new Structure(List.of(chain("A",
                residue("ALA", 10, List.of(
                        atom(1, "CB", Element.C, "C", 0, 0, 0),
                        atom(2, "OG", Element.O, "OA", 0, 3.0, 0))))));
        Structure ligand = new Structure(List.of(chain("L",
                residue("LIG", 501, List.of(
                        atom(101, "C1", Element.C, "C", 3.5, 0, 0),
                        atom(102, "CL1", Element.CL, "Cl", 0, 5.0, 0))))));

        InteractionProfile profile = profiler.profile(receptor, ligand);

        assertThat(profile.anyPerceptionDegraded()).isTrue();
        assertThat(profile.perception())
                .filteredOn(PerceptionSummary::degraded)
                .isNotEmpty();
        // Hydrophobic contact still found via the AD4 fallback.
        assertThat(ofType(profile, InteractionType.HYDROPHOBIC_CONTACT))
                .hasSize(1);
        // Halogen detection requires connectivity: empty, never guessed.
        assertThat(ofType(profile, InteractionType.HALOGEN_BOND))
                .isEmpty();
    }
}
