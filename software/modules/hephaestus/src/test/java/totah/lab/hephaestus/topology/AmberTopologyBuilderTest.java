package totah.lab.hephaestus.topology;

import org.junit.jupiter.api.Test;
import totah.lab.gaia.chemistry.Element;
import totah.lab.gaia.geometry.Point3D;
import totah.lab.gaia.structure.Atom;
import totah.lab.gaia.structure.Chain;
import totah.lab.gaia.structure.Residue;
import totah.lab.gaia.structure.Structure;
import totah.lab.hephaestus.amber.AmberResidueTemplateLibrary;
import totah.lab.hephaestus.receptor.residue.ResidueState;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class AmberTopologyBuilderTest {

    @Test
    void monoatomicIonInChainIsSkippedForPeptideBonds() {
        Residue first = alanine(1);
        Residue zinc = new Residue("ZN", 2, List.of(
                Atom.builder()
                        .name("ZN")
                        .element(Element.ZN)
                        .position(new Point3D(8.0, 8.0, 8.0))
                        .occupancy(1.0)
                        .build()));
        Residue third = alanine(3);

        Structure structure = new Structure(List.of(
                new Chain("A", List.of(first, zinc, third))));

        Map<String, ResidueState> states = new LinkedHashMap<>();
        states.put("A:1", state("A", 1));
        states.put("A:3", state("A", 3));

        TopologyBuilder.BuildResult result = assertDoesNotThrow(
                () -> new AmberTopologyBuilder().build(structure, states));

        assertEquals(structure.getAtomCount(), result.topology().atomCount());
        assertEquals(0, result.report().peptideBondCount());
    }

    private ResidueState state(String chainId, int number) {
        return new ResidueState(
                chainId,
                number,
                null,
                "ALA",
                "ALA",
                "ALA",
                false,
                false,
                false,
                "test");
    }

    private Residue alanine(int number) {
        var template = AmberResidueTemplateLibrary.getInstance()
                .getTemplate("ALA");
        List<Atom> atoms = new java.util.ArrayList<>();
        int index = 0;
        double offset = (number - 1) * 10.0;
        for (var atomTemplate : template.getAtoms()) {
            String name = atomTemplate.getName();
            atoms.add(Atom.builder()
                    .name(name)
                    .element(element(name))
                    .position(new Point3D(offset + 0.3 * (++index), 1.0, 0.0))
                    .occupancy(1.0)
                    .build());
        }
        return new Residue("ALA", number, atoms);
    }

    private Element element(String atomName) {
        if (atomName.startsWith("H")) return Element.H;
        if (atomName.startsWith("N")) return Element.N;
        if (atomName.startsWith("O")) return Element.O;
        return Element.C;
    }
}
