package totah.lab.athena.relaxation;

import org.junit.jupiter.api.Test;
import totah.lab.gaia.chemistry.Element;
import totah.lab.gaia.geometry.Point3D;
import totah.lab.gaia.structure.Atom;
import totah.lab.gaia.structure.AtomReference;
import totah.lab.gaia.structure.Chain;
import totah.lab.gaia.structure.Residue;
import totah.lab.gaia.structure.Structure;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LocalRelaxationProtocolContractTest {
    @Test
    void rejectsMissingRequiredBackboneAndSideChainConstants() {
        assertThatThrownBy(() -> protocol(LocalRelaxationProtocol.BackbonePolicy.HARMONICALLY_RESTRAINED,
                Optional.empty(), LocalRelaxationProtocol.SideChainPolicy.WITHIN_RADIUS_MOBILE,
                Optional.empty())).hasMessageContaining("backbone requires");
        assertThatThrownBy(() -> protocol(LocalRelaxationProtocol.BackbonePolicy.FIXED,
                Optional.empty(), LocalRelaxationProtocol.SideChainPolicy.WITHIN_RADIUS_RESTRAINED,
                Optional.empty())).hasMessageContaining("side chains require");
    }

    @Test
    void forceConstantsRejectInvalidValuesAndUnitsAndConvertExplicitly() {
        assertThatThrownBy(() -> new HarmonicForceConstant(0,
                HarmonicForceConstant.Unit.KILOJOULES_PER_MOLE_NANOMETRE_SQUARED))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new HarmonicForceConstant(1, null))
                .isInstanceOf(NullPointerException.class);
        assertThat(new HarmonicForceConstant(1,
                HarmonicForceConstant.Unit.KILOCALORIES_PER_MOLE_ANGSTROM_SQUARED)
                .kilojoulesPerMoleNanometreSquared()).isEqualTo(418.4);
    }

    @Test
    void mappingRejectsIncompleteDuplicateAndTamperedIdentity() {
        var one = new SystemAtomMapping.Entry(ref("A", "CA"), 0,
                SystemAtomMapping.ComponentRole.PROTEIN_BACKBONE);
        var duplicate = new SystemAtomMapping.Entry(ref("A", "CB"), 0,
                SystemAtomMapping.ComponentRole.PROTEIN_SIDE_CHAIN);
        assertThatThrownBy(() -> SystemAtomMapping.create(List.of(one, duplicate), "fixture"))
                .hasMessageContaining("duplicate OpenMM");
        var gap = new SystemAtomMapping.Entry(ref("A", "CB"), 2,
                SystemAtomMapping.ComponentRole.PROTEIN_SIDE_CHAIN);
        assertThatThrownBy(() -> SystemAtomMapping.create(List.of(one, gap), "fixture"))
                .hasMessageContaining("completely cover");
        assertThatThrownBy(() -> new SystemAtomMapping(List.of(one), "0".repeat(64), "fixture"))
                .hasMessageContaining("SHA-256 mismatch");
        assertThatThrownBy(() -> SystemAtomMapping.ComponentRole.valueOf("UNKNOWN"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void mappingHashIsStableAcrossInputOrderButChangesWithProvenance() {
        var first = entries();
        var reversed = first.reversed();
        assertThat(SystemAtomMapping.create(first, "rule-v1").sha256())
                .isEqualTo(SystemAtomMapping.create(reversed, "rule-v1").sha256());
        assertThat(SystemAtomMapping.create(first, "rule-v2").sha256())
                .isNotEqualTo(SystemAtomMapping.create(first, "rule-v1").sha256());
    }

    @Test
    void classifiesEveryRoleWithReasonAndReplaysIdentically() {
        LocalRelaxationProtocol protocol = protocol(LocalRelaxationProtocol.BackbonePolicy.FIXED,
                Optional.empty(), LocalRelaxationProtocol.SideChainPolicy.WITHIN_RADIUS_MOBILE,
                Optional.empty());
        Structure receptor = receptor();
        Structure ligand = structure("L", "LIG", List.of(atom(10, "C1", 0, Element.C)));
        Structure sam = structure("S", "SAM", List.of(atom(20, "S", 0, Element.S)));
        var selector = new RelaxationAtomSelector();
        var first = selector.select(protocol, receptor, ligand, sam);
        var second = selector.select(protocol, receptor, ligand, sam);
        assertThat(first).isEqualTo(second);
        assertThat(first.atoms()).extracting(RelaxationAtomSelector.SelectedAtom::mobility)
                .containsExactly(AtomSelectionRules.Mobility.FIXED,
                        AtomSelectionRules.Mobility.LOCAL_MOBILE,
                        AtomSelectionRules.Mobility.FIXED,
                        AtomSelectionRules.Mobility.POSITION_RESTRAINED,
                        AtomSelectionRules.Mobility.POSITION_RESTRAINED,
                        AtomSelectionRules.Mobility.UNRESTRAINED,
                        AtomSelectionRules.Mobility.UNRESTRAINED);
        assertThat(first.atoms()).allMatch(atom -> !atom.reason().isBlank());
    }

    @Test
    void identicalPolicyOnEquivalentTopologyHasComparableSelectionSemantics() {
        var protocolA = protocol(LocalRelaxationProtocol.BackbonePolicy.FIXED, Optional.empty(),
                LocalRelaxationProtocol.SideChainPolicy.WITHIN_RADIUS_MOBILE, Optional.empty());
        var protocolB = protocol(LocalRelaxationProtocol.BackbonePolicy.FIXED, Optional.empty(),
                LocalRelaxationProtocol.SideChainPolicy.WITHIN_RADIUS_MOBILE, Optional.empty());
        var selector = new RelaxationAtomSelector();
        Structure ligand = structure("L", "LIG", List.of(atom(10, "C1", 0, Element.C)));
        Structure sam = structure("S", "SAM", List.of(atom(20, "S", 0, Element.S)));
        var a = selector.select(protocolA, receptor(), ligand, sam);
        var b = selector.select(protocolB, receptor(), ligand, sam);
        assertThat(a.semanticPolicySha256()).isEqualTo(b.semanticPolicySha256());
        assertThat(a.selectionMaskSha256()).isEqualTo(b.selectionMaskSha256());
    }

    private static LocalRelaxationProtocol protocol(LocalRelaxationProtocol.BackbonePolicy backbone,
            Optional<HarmonicForceConstant> backboneK,
            LocalRelaxationProtocol.SideChainPolicy sideChain,
            Optional<HarmonicForceConstant> sideChainK) {
        return new LocalRelaxationProtocol("fixture", 2.0, backbone, backboneK,
                sideChain, sideChainK,
                new LocalRelaxationProtocol.Restraint(LocalRelaxationProtocol.Policy.POSITIONAL,
                        Optional.of(k()), 1.0),
                new LocalRelaxationProtocol.Restraint(LocalRelaxationProtocol.Policy.POSITIONAL,
                        Optional.of(k()), 1.0), SystemAtomMapping.create(entries(), "mapping-v1"),
                rules(), "LocalEnergyMinimizer", 1.0, 100, 8, true);
    }

    private static HarmonicForceConstant k() {
        return new HarmonicForceConstant(10,
                HarmonicForceConstant.Unit.KILOJOULES_PER_MOLE_NANOMETRE_SQUARED);
    }

    private static AtomSelectionRules rules() {
        return new AtomSelectionRules(AtomSelectionRules.Mobility.FIXED,
                AtomSelectionRules.Mobility.POSITION_RESTRAINED,
                AtomSelectionRules.Mobility.LOCAL_MOBILE,
                AtomSelectionRules.Mobility.POSITION_RESTRAINED,
                AtomSelectionRules.Mobility.FIXED,
                AtomSelectionRules.Mobility.UNRESTRAINED,
                AtomSelectionRules.Mobility.POSITION_RESTRAINED,
                AtomSelectionRules.Mobility.UNRESTRAINED,
                AtomSelectionRules.Mobility.POSITION_RESTRAINED,
                AtomSelectionRules.Mobility.UNRESTRAINED,
                AtomSelectionRules.Mobility.UNRESTRAINED,
                AtomSelectionRules.RadiusReference.LIGAND_HEAVY_ATOMS, "selection-rules-v1");
    }

    private static List<SystemAtomMapping.Entry> entries() {
        return List.of(entry(0, "A", "CA", SystemAtomMapping.ComponentRole.PROTEIN_BACKBONE),
                entry(1, "A", "CB", SystemAtomMapping.ComponentRole.PROTEIN_SIDE_CHAIN),
                entry(2, "A", "CG", SystemAtomMapping.ComponentRole.PROTEIN_SIDE_CHAIN),
                entry(3, "L", "C1", SystemAtomMapping.ComponentRole.LIGAND),
                entry(4, "S", "S", SystemAtomMapping.ComponentRole.SAM),
                entry(5, "W", "O", SystemAtomMapping.ComponentRole.SOLVENT),
                entry(6, "I", "NA", SystemAtomMapping.ComponentRole.ION));
    }

    private static SystemAtomMapping.Entry entry(int index, String chain, String atom,
            SystemAtomMapping.ComponentRole role) {
        return new SystemAtomMapping.Entry(ref(chain, atom), index, role);
    }

    private static AtomReference ref(String chain, String atom) {
        return new AtomReference(chain, 1, ' ', atom);
    }

    private static Structure receptor() {
        return new Structure(List.of(
                new Chain("A", List.of(new Residue("ALA", 1, List.of(
                        atom(1, "CA", 0, Element.C), atom(2, "CB", 1, Element.C),
                        atom(3, "CG", 5, Element.C))))),
                new Chain("W", List.of(new Residue("HOH", 1, List.of(atom(4, "O", 8, Element.O))))),
                new Chain("I", List.of(new Residue("NA", 1, List.of(atom(5, "NA", 9, Element.NA)))))));
    }

    private static Structure structure(String chain, String residue, List<Atom> atoms) {
        return new Structure(List.of(new Chain(chain, List.of(new Residue(residue, 1, atoms)))));
    }

    private static Atom atom(int serial, String name, double x, Element element) {
        return Atom.builder().pdbSerial(serial).name(name).position(new Point3D(x, 0, 0))
                .element(element).build();
    }
}
