package totah.lab.hephaestus.receptor.operation;

import org.junit.jupiter.api.Test;
import totah.lab.gaia.chemistry.Element;
import totah.lab.gaia.geometry.Point3D;
import totah.lab.gaia.molecule.Protein;
import totah.lab.gaia.structure.Atom;
import totah.lab.gaia.structure.Chain;
import totah.lab.gaia.structure.Residue;
import totah.lab.gaia.structure.Structure;
import totah.lab.hephaestus.charge.ChargeAssignment;
import totah.lab.hephaestus.charge.ChargeAssignmentReport;
import totah.lab.hephaestus.model.PreparedProtein;
import totah.lab.hephaestus.receptor.ReceptorPreparationOptions;
import totah.lab.hephaestus.receptor.residue.ResidueState;
import totah.lab.hephaestus.topology.Edge;
import totah.lab.hephaestus.topology.ProteinTopology;
import totah.lab.hephaestus.typing.AD4AtomTypingReport;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class AD4AtomTypingOperationTest {

    @Test
    void typesAtomsUsingExplicitTopologyAndPreservesMultiChainOrder() {
        Residue a1 = residue("ALA", 1,
                atom("CA", Element.C, "CT"),
                atom("HA", Element.H, "H1"));
        Residue b2 = residue("ALA", 2,
                atom("N", Element.N, "N"),
                atom("H", Element.H, "H"));
        Structure structure = new Structure(List.of(
                new Chain("A", List.of(a1)),
                new Chain("B", List.of(b2))));
        Protein protein = new Protein(
                "protein", "P12345", "name", "GENE",
                "organism", "function", structure);
        ProteinTopology topology = new ProteinTopology(4, List.of(
                new Edge(0, 1, 1.0),
                new Edge(2, 3, 1.0)));
        PreparedProtein input = PreparedProtein.of(protein)
                .withTopology(topology)
                .withCharges(new ChargeAssignment("AMBER", List.of()))
                .withAttribute(
                        ChargeAssignmentOperation
                                .CHARGE_ASSIGNMENT_REPORT_ATTRIBUTE,
                        new ChargeAssignmentReport(
                                2, 4, 4, 0, "AMBER", 0.0, List.of()))
                .withAttribute(
                        ResidueStateAssignmentOperation.RESIDUE_STATES_ATTRIBUTE,
                        Map.of(
                                "A:1", state("A", 1),
                                "B:2", state("B", 2)));

        PreparedProtein output = new AD4AtomTypingOperation()
                .apply(input, ReceptorPreparationOptions.defaults())
                .value();

        Structure typed = output.protein().structure();
        assertEquals(List.of("A", "B"), typed.getChains().stream()
                .map(Chain::id).toList());
        assertEquals(List.of(1), typed.getChains().get(0).residues().stream()
                .map(Residue::getNumber).toList());
        assertEquals(List.of(2), typed.getChains().get(1).residues().stream()
                .map(Residue::getNumber).toList());
        assertEquals(List.of("CA", "HA"), typed.getChains().get(0)
                .residues().getFirst().getAtoms().stream()
                .map(Atom::getName).toList());
        assertEquals("H", typed.getChains().get(0).residues().getFirst()
                .getAtoms().get(1).getAutoDockType());
        assertEquals("HD", typed.getChains().get(1).residues().getFirst()
                .getAtoms().get(1).getAutoDockType());
        assertNull(a1.getAtoms().get(1).getAutoDockType());

        assertEquals(List.of("A", "A", "B", "B"),
                output.atomTypes().atomTypes().stream()
                        .map(type -> type.chainId()).toList());
        assertEquals(List.of(0, 1, 2, 3),
                output.atomTypes().atomTypes().stream()
                        .map(type -> type.atomIndex()).toList());
        AD4AtomTypingReport report = (AD4AtomTypingReport)
                output.attributes().get(
                        AD4AtomTypingOperation.AD4_ATOM_TYPING_REPORT_ATTRIBUTE);
        assertEquals(2, report.residueCount());
        assertEquals(4, report.atomCount());
        assertEquals(Map.of("C", 1, "H", 1, "N", 1, "HD", 1),
                report.typeCounts());
    }

    private Atom atom(String name, Element element, String amberType) {
        return Atom.builder()
                .name(name)
                .element(element)
                .amberType(amberType)
                .charge(0.0)
                .occupancy(1.0)
                .position(new Point3D(0.0, 0.0, 0.0))
                .build();
    }

    private Residue residue(String name, int number, Atom... atoms) {
        return new Residue(name, number, List.of(atoms));
    }

    private ResidueState state(String chainId, int number) {
        return new ResidueState(
                chainId, number, null, "ALA", "ALA", "ALA",
                false, false, false, "");
    }
}
