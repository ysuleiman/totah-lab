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
import totah.lab.hephaestus.flexibility.FlexibilityPreparationConfig;
import totah.lab.hephaestus.model.PreparedProtein;
import totah.lab.hephaestus.receptor.ReceptorPreparationOptions;
import totah.lab.hephaestus.topology.Edge;
import totah.lab.hephaestus.topology.ProteinTopology;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FlexibilityPreparationOperationTest {
    @Test
    void buildsChainAwareFragmentsWithCanonicalAtomIndices() {
        Residue selected = residue("VAL", 2, "N", "CA", "HA", "CB", "CG1", "C", "O");
        Structure structure = new Structure(List.of(
                new Chain("A", List.of(residue("ALA", 2, "CA"))),
                new Chain("B", List.of(
                        residue("ALA", 1, "CA"), selected,
                        residue("ALA", 3, "CA")))));
        List<Edge> edges = new ArrayList<>();
        add(edges, 2, 3); add(edges, 3, 4); add(edges, 3, 5);
        add(edges, 5, 6); add(edges, 3, 7); add(edges, 7, 8);
        ProteinTopology topology = new ProteinTopology(structure.getAtomCount(), edges);
        PreparedProtein input = PreparedProtein.of(new Protein(
                "p", null, "protein", null, null, null, structure)).withTopology(topology);
        ResidueId requested = new ResidueId("B", 2, null);
        ReceptorPreparationOptions defaults = ReceptorPreparationOptions.defaults();
        ReceptorPreparationOptions options = new ReceptorPreparationOptions(
                defaults.removeWaters(), defaults.keepMetals(), defaults.allowedSpecialResidues(),
                defaults.plddtCutoff(), defaults.addHydrogens(), defaults.optimizeHydrogens(),
                defaults.buildTopology(), defaults.assignCharges(), defaults.assignAtomTypes(),
                defaults.protonationConfig(), defaults.residueProtonationOverrides(),
                new FlexibilityPreparationConfig(Set.of(requested), false, false, false),
                defaults.pdbqtExportOptions());

        PreparedProtein output = new FlexibilityPreparationOperation().apply(input, options).value();
        var flexible = output.flexibility().flexibleResidues().getFirst();

        assertEquals(requested, flexible.residue());
        assertEquals("CA", flexible.anchorAtom().atomName());
        assertEquals(3, flexible.anchorAtom().atomIndex());
        assertEquals(2, flexible.fragments().size());
        assertEquals(1, flexible.rotatableBonds().size());
        assertEquals("CA", flexible.rotatableBonds().getFirst().parentAtom().atomName());
        assertEquals("CB", flexible.rotatableBonds().getFirst().childAtom().atomName());
    }

    private Residue residue(String name, int number, String... names) {
        return new Residue(name, number, java.util.Arrays.stream(names).map(this::atom).toList());
    }
    private Atom atom(String name) {
        Element element = name.startsWith("H") ? Element.H
                : name.startsWith("N") ? Element.N
                : name.startsWith("O") ? Element.O : Element.C;
        return Atom.builder().name(name).element(element).position(new Point3D(0, 0, 0))
                .occupancy(1.0).build();
    }
    private void add(List<Edge> edges, int a, int b) { edges.add(new Edge(a, b, 1.5)); }
}
