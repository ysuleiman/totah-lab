package totah.lab.hephaestus.receptor.operation;

import org.junit.jupiter.api.Test;
import totah.lab.gaia.chemistry.Element;
import totah.lab.gaia.geometry.Point3D;
import totah.lab.gaia.molecule.Protein;
import totah.lab.gaia.structure.Atom;
import totah.lab.gaia.structure.Chain;
import totah.lab.gaia.structure.Residue;
import totah.lab.gaia.structure.Structure;
import totah.lab.hephaestus.amber.AmberResidueTemplateLibrary;
import totah.lab.hephaestus.model.PreparedProtein;
import totah.lab.hephaestus.topology.ProteinTopology;
import totah.lab.hephaestus.receptor.ReceptorPreparationOptions;
import totah.lab.hephaestus.receptor.hydrogenation.HydrogenOptimizationReport;
import totah.lab.hephaestus.receptor.residue.ResidueState;
import totah.lab.hephaestus.topology.TopologyBuildReport;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class TopologyBuilderOperationTest {

    @Test
    void addsPeptideBondsOnlyWithinEachChainAndPreservesStructure() {
        Residue a1 = alanine(1, 0.0, -1.0);
        Residue a2 = alanine(2, 3.0, 1.33);
        Residue b3 = alanine(3, 20.0, 19.0);
        Residue b4 = alanine(4, 23.0, 21.33);
        Structure structure = new Structure(List.of(
                new Chain("A", List.of(a1, a2)),
                new Chain("B", List.of(b3, b4))));

        Map<String, ResidueState> states = new LinkedHashMap<>();
        states.put("A:1", state("A", 1));
        states.put("A:2", state("A", 2));
        states.put("B:3", state("B", 3));
        states.put("B:4", state("B", 4));

        Protein protein = new Protein(
                "protein", "P12345", "protein", "GENE",
                "organism", "function", structure);
        PreparedProtein input = PreparedProtein.of(protein)
                .withAttribute(
                        HydrogenOptimizationOperation
                                .HYDROGEN_OPTIMIZATION_REPORT_ATTRIBUTE,
                        new HydrogenOptimizationReport(
                                4, 4, 0, 0, List.of()))
                .withAttribute(
                        ResidueStateAssignmentOperation.RESIDUE_STATES_ATTRIBUTE,
                        states);

        PreparedProtein output = new TopologyBuilderOperation()
                .apply(input, ReceptorPreparationOptions.defaults())
                .value();

        ProteinTopology topology = (ProteinTopology) output.topology();
        TopologyBuildReport report = (TopologyBuildReport)
                output.attributes().get(
                        TopologyBuilderOperation.TOPOLOGY_BUILD_REPORT_ATTRIBUTE);

        assertEquals(2, report.peptideBondCount());
        assertEquals(0, report.disulfideBondCount());
        assertEquals(structure.getAtomCount(), topology.atomCount());
        assertEquals(List.of("A", "B"), output.protein().structure()
                .getChains().stream().map(Chain::id).toList());
        assertEquals(List.of(1, 2), output.protein().structure()
                .getChains().get(0).residues().stream()
                .map(Residue::getNumber).toList());
        assertEquals(List.of(3, 4), output.protein().structure()
                .getChains().get(1).residues().stream()
                .map(Residue::getNumber).toList());
        assertSame(protein, output.protein());
        assertSame(a1, output.protein().structure()
                .getChains().get(0).residues().getFirst());
    }

    private Residue alanine(
            int number,
            double carbonPosition,
            double nitrogenPosition) {

        var template = AmberResidueTemplateLibrary.getInstance()
                .getTemplate("ALA");
        List<Atom> atoms = new java.util.ArrayList<>();
        int index = 0;
        for (var atomTemplate : template.getAtoms()) {
            String name = atomTemplate.getName();
            double x = switch (name) {
                case "C" -> carbonPosition;
                case "N" -> nitrogenPosition;
                default -> carbonPosition + 0.2 * (++index);
            };
            double y = switch (name) {
                case "C", "N" -> 0.0;
                default -> 2.0 + 0.1 * index;
            };
            atoms.add(Atom.builder()
                    .name(name)
                    .amberType(atomTemplate.getAmberType())
                    .charge(atomTemplate.getCharge())
                    .element(element(name))
                    .position(new Point3D(x, y, 0.0))
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

    private ResidueState state(String chainId, int number) {
        return new ResidueState(
                chainId, number, null, "ALA", "ALA", "ALA",
                false, false, false, "");
    }
}
