package totah.lab.hephaestus.receptor.operation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import totah.lab.gaia.chemistry.Element;
import totah.lab.gaia.geometry.Point3D;
import totah.lab.gaia.molecule.Protein;
import totah.lab.gaia.structure.Atom;
import totah.lab.gaia.structure.Chain;
import totah.lab.gaia.structure.Residue;
import totah.lab.gaia.structure.Structure;
import totah.lab.hephaestus.charge.ChargeAssignment;
import totah.lab.hephaestus.charge.AssignedCharge;
import totah.lab.hephaestus.charge.ChargeAssignmentReport;
import totah.lab.hephaestus.export.PdbqtExportReport;
import totah.lab.hephaestus.export.ReceptorPdbqtExportOptions;
import totah.lab.hephaestus.model.PreparedProtein;
import totah.lab.hephaestus.receptor.ReceptorPreparationOptions;
import totah.lab.hephaestus.typing.AD4AtomTypingReport;
import totah.lab.hephaestus.typing.AtomTypeAssignment;
import totah.lab.hephaestus.typing.AssignedAtomType;
import totah.lab.hephaestus.topology.ProteinTopology;
import totah.lab.hermes.file.pdbqt.PdbqtWriteOptions;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PdbqtExportOperationTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void exportsMultipleChainsInTheirOriginalOrder() throws Exception {
        Structure structure = new Structure(List.of(
                new Chain("A", List.of(new Residue(
                        "ALA", 1, List.of(atom("CA", Element.C, "C"))))),
                new Chain("B", List.of(new Residue(
                        "GLY", 2, List.of(atom("N", Element.N, "N")))))));
        Protein protein = new Protein(
                "protein", "P12345", "name", "GENE",
                "organism", "function", structure);
        PreparedProtein input = PreparedProtein.of(protein)
                .withTopology(new ProteinTopology(2, List.of()))
                .withCharges(new ChargeAssignment("AMBER", List.of(
                        new AssignedCharge(0,"A",1,null,"CA",0,"CA","AMBER"),
                        new AssignedCharge(1,"B",2,null,"N",0,"N","AMBER"))))
                .withAtomTypes(new AtomTypeAssignment("AutoDock4", List.of(
                        new AssignedAtomType(0,"A",1,null,"CA","C"),
                        new AssignedAtomType(1,"B",2,null,"N","N"))))
                .withAttribute(
                        ChargeAssignmentOperation
                                .CHARGE_ASSIGNMENT_REPORT_ATTRIBUTE,
                        new ChargeAssignmentReport(
                                2, 2, 2, 0, "AMBER", 0.0, List.of()))
                .withAttribute(
                        AD4AtomTypingOperation
                                .AD4_ATOM_TYPING_REPORT_ATTRIBUTE,
                        new AD4AtomTypingReport(
                                2, 2, Map.of("C", 1, "N", 1)));
        Path output = temporaryDirectory.resolve("prepared_receptor.pdbqt");
        ReceptorPreparationOptions options = optionsFor(output);

        PreparedProtein result = new PdbqtExportOperation()
                .apply(input, options).value();

        List<String> lines = Files.readAllLines(output);
        assertEquals(4, lines.size());
        assertTrue(lines.get(0).startsWith("ATOM      1  CA  ALA A   1"));
        assertEquals("TER", lines.get(1));
        assertTrue(lines.get(2).startsWith("ATOM      2  N   GLY B   2"));
        assertEquals("END", lines.get(3));
        assertSame(protein, result.protein());
        PdbqtExportReport report = (PdbqtExportReport)
                result.attributes().get(
                        PdbqtExportOperation.PDBQT_EXPORT_REPORT_ATTRIBUTE);
        assertEquals(2, report.chainCount());
        assertEquals(2, report.residueCount());
        assertEquals(2, report.atomCount());
        assertEquals(output.toAbsolutePath().normalize(), report.receptorPath());
    }

    @Test
    void skipsExportWhenRequestHasNoExportOptions() {
        PreparedProtein input = PreparedProtein.of(new Protein(
                "protein", null, "name", null, null, null,
                new Structure(List.of())));

        PreparedProtein result = new PdbqtExportOperation()
                .apply(input, ReceptorPreparationOptions.defaults()).value();

        assertSame(input, result);
    }

    private ReceptorPreparationOptions optionsFor(Path output) {
        ReceptorPreparationOptions defaults =
                ReceptorPreparationOptions.defaults();
        return new ReceptorPreparationOptions(
                defaults.removeWaters(), defaults.keepMetals(),
                defaults.allowedSpecialResidues(), defaults.plddtCutoff(),
                defaults.addHydrogens(), defaults.optimizeHydrogens(),
                defaults.buildTopology(), defaults.assignCharges(),
                defaults.assignAtomTypes(), defaults.protonationConfig(),
                defaults.residueProtonationOverrides(),
                defaults.flexibilityConfig(),
                new ReceptorPdbqtExportOptions(
                        output, new PdbqtWriteOptions(true, true)));
    }

    private Atom atom(String name, Element element, String type) {
        return Atom.builder()
                .name(name)
                .element(element)
                .amberType(name)
                .autoDockType(type)
                .charge(0.0)
                .occupancy(1.0)
                .bFactor(0.0)
                .position(new Point3D(1.0, 2.0, 3.0))
                .build();
    }
}
