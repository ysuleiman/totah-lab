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
import totah.lab.hephaestus.charge.ChargeAssignmentReport;
import totah.lab.hephaestus.model.PreparedProtein;
import totah.lab.hephaestus.receptor.ReceptorPreparationOptions;
import totah.lab.hephaestus.receptor.residue.ResidueState;
import totah.lab.hephaestus.topology.ProteinTopology;
import totah.lab.hephaestus.topology.TopologyBuildReport;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

class ChargeAssignmentOperationTest {

    @Test
    void assignsAmberChargesPerChainAndPreservesOrderingAndMetadata() {
        Residue a1 = unchargedAlanine(1);
        Residue b2 = unchargedAlanine(2);
        Structure structure = new Structure(List.of(
                new Chain("A", List.of(a1)),
                new Chain("B", List.of(b2))));
        Protein protein = new Protein(
                "protein", "P12345", "name", "GENE",
                "organism", "function", structure);

        Map<String, ResidueState> states = Map.of(
                "A:1", state("A", 1),
                "B:2", state("B", 2));
        ProteinTopology topology = new ProteinTopology(
                structure.getAtomCount(), List.of());
        PreparedProtein input = PreparedProtein.of(protein)
                .withTopology(topology)
                .withAttribute(
                        TopologyBuilderOperation.TOPOLOGY_BUILD_REPORT_ATTRIBUTE,
                        new TopologyBuildReport(
                                2, structure.getAtomCount(), 0, 0, 0, 0,
                                List.of("A:1 -> ALA", "B:2 -> ALA")))
                .withAttribute(
                        ResidueStateAssignmentOperation.RESIDUE_STATES_ATTRIBUTE,
                        states);

        PreparedProtein output = new ChargeAssignmentOperation()
                .apply(input, ReceptorPreparationOptions.defaults())
                .value();

        assertNotSame(protein, output.protein());
        assertEquals(protein.id(), output.protein().id());
        assertEquals(protein.uniProtId(), output.protein().uniProtId());
        assertEquals(protein.gene(), output.protein().gene());
        assertEquals(List.of("A", "B"), output.protein().structure()
                .getChains().stream().map(Chain::id).toList());
        assertEquals(List.of(1), output.protein().structure().getChains()
                .get(0).residues().stream().map(Residue::getNumber).toList());
        assertEquals(List.of(2), output.protein().structure().getChains()
                .get(1).residues().stream().map(Residue::getNumber).toList());
        assertEquals(a1.getAtoms().stream().map(Atom::getName).toList(),
                output.protein().structure().getChains().get(0).residues()
                        .getFirst().getAtoms().stream().map(Atom::getName).toList());
        assertEquals(0.0, a1.getAtoms().getFirst().getCharge());

        var template = AmberResidueTemplateLibrary.getInstance()
                .getTemplate("ALA");
        Atom chargedFirst = output.protein().structure().getChains().get(0)
                .residues().getFirst().getAtoms().getFirst();
        assertEquals(template.getAtoms().getFirst().getCharge(),
                chargedFirst.getCharge());
        assertEquals(template.getAtoms().getFirst().getAmberType(),
                chargedFirst.getAmberType());

        assertEquals(structure.getAtomCount(), output.charges().atomCount());
        assertEquals(List.of("A", "B"), output.charges().charges().stream()
                .map(charge -> charge.chainId()).distinct().toList());
        assertEquals(
                java.util.stream.IntStream.range(0, structure.getAtomCount())
                        .boxed().toList(),
                output.charges().charges().stream()
                        .map(charge -> charge.atomIndex()).toList());
        ChargeAssignmentReport report = (ChargeAssignmentReport)
                output.attributes().get(
                        ChargeAssignmentOperation
                                .CHARGE_ASSIGNMENT_REPORT_ATTRIBUTE);
        assertEquals(structure.getAtomCount(), report.amberAssignedAtoms());
        assertEquals(0, report.fixedIonAtoms());
    }

    @Test
    void returnsOriginalPreparedProteinWhenChargeAssignmentIsDisabled() {
        PreparedProtein input = PreparedProtein.of(new Protein(
                "protein", null, "name", null, null, null,
                new Structure(List.of())));
        ReceptorPreparationOptions defaults =
                ReceptorPreparationOptions.defaults();
        ReceptorPreparationOptions disabled = new ReceptorPreparationOptions(
                defaults.removeWaters(), defaults.keepMetals(),
                defaults.allowedSpecialResidues(), defaults.plddtCutoff(),
                defaults.addHydrogens(), defaults.optimizeHydrogens(),
                defaults.buildTopology(), false, defaults.assignAtomTypes(),
                defaults.protonationConfig(),
                defaults.residueProtonationOverrides(),
                defaults.flexibilityConfig(),
                defaults.pdbqtExportOptions());

        PreparedProtein output = new ChargeAssignmentOperation()
                .apply(input, disabled).value();

        assertSame(input, output);
    }

    private Residue unchargedAlanine(int number) {
        var template = AmberResidueTemplateLibrary.getInstance()
                .getTemplate("ALA");
        List<Atom> atoms = new ArrayList<>();
        int index = 0;
        for (var atomTemplate : template.getAtoms()) {
            String name = atomTemplate.getName();
            atoms.add(Atom.builder()
                    .name(name)
                    .element(element(name))
                    .position(new Point3D(index++, 0.0, 0.0))
                    .occupancy(1.0)
                    .charge(0.0)
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
