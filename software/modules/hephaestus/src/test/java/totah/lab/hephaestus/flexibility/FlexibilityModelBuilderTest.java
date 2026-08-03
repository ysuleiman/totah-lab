package totah.lab.hephaestus.flexibility;

import org.junit.jupiter.api.Test;
import totah.lab.gaia.chemistry.Element;
import totah.lab.gaia.geometry.Point3D;
import totah.lab.gaia.structure.Atom;
import totah.lab.gaia.structure.Chain;
import totah.lab.gaia.structure.Residue;
import totah.lab.gaia.structure.ResidueId;
import totah.lab.gaia.structure.Structure;
import totah.lab.hephaestus.topology.Edge;
import totah.lab.hephaestus.topology.ProteinTopology;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FlexibilityModelBuilderTest {

    /**
     * Valine whose side-chain atoms precede CA in the atom ordering, with
     * a backbone amide hydrogen present. With includeBackbone=false the
     * amide H must not become an orphan fragment, and the child fragment
     * (emitted first because its atoms come first) must still be wired to
     * the CA-containing parent fragment.
     */
    @Test
    void backboneHydrogenDoesNotBecomeOrphanAndParentsIgnoreEmissionOrder() {
        // Chain: ALA 1 (single CA), VAL 2 (9 atoms), ALA 3 (single CA).
        Residue valine = residue("VAL", 2,
                "N", "H", "CB", "CG1", "CG2", "CA", "HA", "C", "O");
        Structure structure = new Structure(List.of(
                new Chain("A", List.of(
                        residue("ALA", 1, "CA"),
                        valine,
                        residue("ALA", 3, "CA")))));

        // VAL local indices: N=0 H=1 CB=2 CG1=3 CG2=4 CA=5 HA=6 C=7 O=8,
        // global base index 1.
        List<Edge> edges = new java.util.ArrayList<>();
        add(edges, 1, 6);  // N-CA
        add(edges, 1, 2);  // N-H (amide hydrogen)
        add(edges, 6, 7);  // CA-HA
        add(edges, 6, 3);  // CA-CB (chi bond, cut)
        add(edges, 3, 4);  // CB-CG1
        add(edges, 3, 5);  // CB-CG2
        add(edges, 6, 8);  // CA-C
        add(edges, 8, 9);  // C-O
        ProteinTopology topology = new ProteinTopology(
                structure.getAtomCount(), edges);

        FlexibilityModel model = new FlexibilityModelBuilder().build(
                structure,
                topology,
                new FlexibilityPreparationConfig(
                        Set.of(new ResidueId("A", 2, null)),
                        false, false, false));

        FlexibleResidue flexible = model.flexibleResidues().getFirst();

        assertEquals(2, flexible.fragments().size());
        assertTrue(flexible.fragments().stream()
                .flatMap(fragment -> fragment.atoms().stream())
                .noneMatch(atom -> atom.atomName().equals("H")));

        RigidFragment root = flexible.fragments().stream()
                .filter(fragment -> fragment.parentFragmentId() == null)
                .findFirst()
                .orElseThrow();
        assertTrue(root.atoms().stream()
                .anyMatch(atom -> atom.atomName().equals("CA")));

        RigidFragment child = flexible.fragments().stream()
                .filter(fragment -> fragment.parentFragmentId() != null)
                .findFirst()
                .orElseThrow();
        assertEquals(root.id(), child.parentFragmentId());
        assertTrue(child.atoms().stream()
                .anyMatch(atom -> atom.atomName().equals("CB")));

        assertEquals(1, flexible.rotatableBonds().size());
        RotatableBond bond = flexible.rotatableBonds().getFirst();
        assertEquals(root.id(), bond.parentFragmentId());
        assertEquals(child.id(), bond.childFragmentId());
        assertNotNull(bond.parentAtom());
    }

    private Residue residue(String name, int number, String... names) {
        return new Residue(name, number,
                java.util.Arrays.stream(names).map(this::atom).toList());
    }

    private Atom atom(String name) {
        Element element = name.startsWith("H") ? Element.H
                : name.startsWith("N") ? Element.N
                : name.startsWith("O") ? Element.O : Element.C;
        return Atom.builder()
                .name(name)
                .element(element)
                .position(new Point3D(0, 0, 0))
                .occupancy(1.0)
                .build();
    }

    private void add(List<Edge> edges, int a, int b) {
        edges.add(new Edge(a, b, 1.5));
    }
}
