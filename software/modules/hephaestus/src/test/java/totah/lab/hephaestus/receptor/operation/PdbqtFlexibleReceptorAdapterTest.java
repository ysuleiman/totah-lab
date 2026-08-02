package totah.lab.hephaestus.receptor.operation;

import org.junit.jupiter.api.Test;
import totah.lab.gaia.chemistry.Element;
import totah.lab.gaia.geometry.Point3D;
import totah.lab.gaia.molecule.Protein;
import totah.lab.gaia.structure.Atom;
import totah.lab.gaia.structure.Chain;
import totah.lab.gaia.structure.Residue;
import totah.lab.gaia.structure.ResidueId;
import totah.lab.gaia.structure.Structure;
import totah.lab.hephaestus.flexibility.AtomReference;
import totah.lab.hephaestus.flexibility.FlexibleResidue;
import totah.lab.hephaestus.flexibility.FlexibilityModel;
import totah.lab.hephaestus.flexibility.RigidFragment;
import totah.lab.hephaestus.model.PreparedProtein;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import totah.lab.hephaestus.validation.ValidationException;

class PdbqtFlexibleReceptorAdapterTest {
    @Test
    void preservesModelOrderAndInsertionIdentityWhilePartitioningExactlyOnce() {
        PreparedProtein protein = protein();
        ResidueId b = new ResidueId("B", 9, 'A');
        ResidueId a = new ResidueId("A", 1, null);
        AtomReference bCa = new AtomReference(b, "CA", 1);
        AtomReference aCa = new AtomReference(a, "CA", 0);
        FlexibilityModel model = new FlexibilityModel(List.of(
                flexible(b, bCa, "b-root"), flexible(a, aCa, "a-root")));

        var input = new PdbqtFlexibleReceptorAdapter().adapt(protein, model);

        assertEquals(List.of("B", "A"), input.flexibleResidues().stream()
                .map(residue -> residue.chainId()).toList());
        assertEquals('A', input.flexibleResidues().getFirst().insertionCode());
        assertEquals(List.of(2), input.rigidAtoms().stream()
                .map(atom -> atom.atom().canonicalAtomIndex()).toList());
    }

    @Test
    void rejectsStaleCanonicalIndex() {
        ResidueId ref = new ResidueId("A", 1, null);
        AtomReference stale = new AtomReference(ref, "CA", 99);
        assertThrows(ValidationException.class, () ->
                new PdbqtFlexibleReceptorAdapter().adapt(
                        protein(), new FlexibilityModel(List.of(flexible(ref, stale, "root")))));
    }

    @Test
    void rejectsMismatchedAtomNameOrResidueIdentity() {
        ResidueId ref = new ResidueId("A", 1, null);
        AtomReference mismatch = new AtomReference(ref, "CB", 0);
        assertThrows(ValidationException.class, () ->
                new PdbqtFlexibleReceptorAdapter().adapt(
                        protein(), new FlexibilityModel(List.of(flexible(ref, mismatch, "root")))));
    }

    private FlexibleResidue flexible(ResidueId residue, AtomReference atom, String id) {
        return new FlexibleResidue(residue, atom,
                List.of(new RigidFragment(id, List.of(atom), atom, null)), List.of());
    }

    private PreparedProtein protein() {
        Residue a = new Residue("ALA", 1, List.of(atom("CA")));
        Residue b = Residue.builder().name("VAL").number(9).insertionCode('A')
                .atoms(List.of(atom("CA"), atom("CB"))).build();
        Structure structure = new Structure(List.of(
                new Chain("A", List.of(a)), new Chain("B", List.of(b))));
        return PreparedProtein.of(new Protein("p", null, "protein", null, null, null, structure));
    }

    private Atom atom(String name) {
        return Atom.builder().name(name).element(Element.C).amberType("CT")
                .autoDockType("C").charge(0).occupancy(1).bFactor(0)
                .position(new Point3D(0,0,0)).build();
    }
}
