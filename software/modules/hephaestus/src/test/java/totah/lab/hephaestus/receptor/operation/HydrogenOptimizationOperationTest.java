package totah.lab.hephaestus.receptor.operation;

import org.junit.jupiter.api.Test;
import totah.lab.gaia.chemistry.Element;
import totah.lab.gaia.geometry.Point3D;
import totah.lab.gaia.molecule.Protein;
import totah.lab.gaia.structure.Atom;
import totah.lab.gaia.structure.Chain;
import totah.lab.gaia.structure.Residue;
import totah.lab.gaia.structure.Structure;
import totah.lab.hephaestus.model.PreparedProtein;
import totah.lab.hephaestus.receptor.ReceptorPreparationOptions;
import totah.lab.hephaestus.receptor.hydrogenation.HydrogenOptimizationReport;
import totah.lab.hephaestus.receptor.hydrogenation.HydrogenationReport;
import totah.lab.hephaestus.receptor.residue.ResidueState;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

class HydrogenOptimizationOperationTest {

    @Test
    void preservesMultiChainHierarchyAndAtomOrder() {
        Residue residueA = serine(1, 0.0);
        Residue residueB = serine(2, 20.0);
        Structure structure = new Structure(List.of(
                new Chain("A", List.of(residueA)),
                new Chain("B", List.of(residueB))));

        Map<String, ResidueState> states = new LinkedHashMap<>();
        states.put("A:1", state("A", 1));
        states.put("B:2", state("B", 2));

        PreparedProtein input = PreparedProtein.of(protein(structure))
                .withAttribute(
                        ReceptorHydrogenationOperation
                                .HYDROGENATION_REPORT_ATTRIBUTE,
                        new HydrogenationReport(
                                2, 2, 0, 2, List.of(), List.of()))
                .withAttribute(
                        ResidueStateAssignmentOperation
                                .RESIDUE_STATES_ATTRIBUTE,
                        states);

        PreparedProtein output = new HydrogenOptimizationOperation()
                .apply(input, ReceptorPreparationOptions.defaults())
                .value();

        List<Chain> outputChains = output.protein().structure().getChains();
        assertEquals(List.of("A", "B"), outputChains.stream()
                .map(Chain::id)
                .toList());
        assertEquals(List.of(1), outputChains.get(0).residues().stream()
                .map(Residue::getNumber)
                .toList());
        assertEquals(List.of(2), outputChains.get(1).residues().stream()
                .map(Residue::getNumber)
                .toList());
        assertEquals(atomNames(residueA),
                atomNames(outputChains.get(0).residues().getFirst()));
        assertEquals(atomNames(residueB),
                atomNames(outputChains.get(1).residues().getFirst()));
        assertNotSame(residueA, outputChains.get(0).residues().getFirst());
        assertEquals("protein", output.protein().id());
        assertEquals("P12345", output.protein().uniProtId().orElseThrow());
        assertEquals("GENE", output.protein().gene().orElseThrow());
        assertEquals("organism", output.protein().organism().orElseThrow());
        assertEquals("function", output.protein().function().orElseThrow());
        assertEquals(
                new Point3D(0.5, 2.8, 0.0),
                residueA.findAtom("HG").orElseThrow().getPosition());

        HydrogenOptimizationReport report =
                (HydrogenOptimizationReport) output.attributes().get(
                        HydrogenOptimizationOperation
                                .HYDROGEN_OPTIMIZATION_REPORT_ATTRIBUTE);
        assertEquals(2, report.inputResidues());
        assertEquals(2, report.outputResidues());
    }

    @Test
    void returnsInputWhenOptimizationIsDisabled() {
        ReceptorPreparationOptions defaults =
                ReceptorPreparationOptions.defaults();
        ReceptorPreparationOptions disabled =
                new ReceptorPreparationOptions(
                        defaults.removeWaters(),
                        defaults.keepMetals(),
                        defaults.allowedSpecialResidues(),
                        defaults.plddtCutoff(),
                        defaults.addHydrogens(),
                        false,
                        defaults.buildTopology(),
                        defaults.assignCharges(),
                        defaults.assignAtomTypes(),
                        defaults.protonationConfig(),
                        defaults.residueProtonationOverrides(),
                        defaults.flexibilityConfig(),
                        defaults.pdbqtExportOptions());

        PreparedProtein input = PreparedProtein.of(
                protein(new Structure(List.of())));

        PreparedProtein output = new HydrogenOptimizationOperation()
                .apply(input, disabled)
                .value();

        assertSame(input, output);
    }

    private Protein protein(Structure structure) {
        return new Protein(
                "protein",
                "P12345",
                "test protein",
                "GENE",
                "organism",
                "function",
                structure);
    }

    private ResidueState state(String chainId, int number) {
        return new ResidueState(
                chainId,
                number,
                null,
                "SER",
                "SER",
                "SER",
                false,
                false,
                false,
                "");
    }

    private Residue serine(int number, double offset) {
        return new Residue(
                "SER",
                number,
                List.of(
                        atom("CA", Element.C, offset, 0.0, 0.0),
                        atom("CB", Element.C, offset, 1.5, 0.0),
                        atom("OG", Element.O, offset, 2.5, 0.0),
                        atom("HG", Element.H, offset + 0.5, 2.8, 0.0)));
    }

    private Atom atom(
            String name,
            Element element,
            double x,
            double y,
            double z) {

        return Atom.builder()
                .name(name)
                .element(element)
                .position(new Point3D(x, y, z))
                .occupancy(1.0)
                .build();
    }

    private List<String> atomNames(Residue residue) {
        return residue.getAtoms().stream()
                .map(Atom::getName)
                .toList();
    }
}
