package totah.lab.pipeline.stage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import totah.lab.math.charges.ChargeModel;
import totah.lab.math.charges.ChargeSystem;
import totah.lab.pipeline.ContextKeys;
import totah.lab.pipeline.PipelineContext;
import totah.lab.protein.Atom;
import totah.lab.protein.Element;
import totah.lab.protein.Point3D;
import totah.lab.protein.Residue;
import totah.lab.protein.Topology;
import totah.lab.topology.AmberResidueTemplateLibrary;
import totah.lab.topology.AtomTemplate;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChargeAssignmentStageTest {

    @TempDir
    Path tempDir;

    @Test
    void requiresTopologyBuildReport() {
        PipelineContext context = contextWith(List.of(nAlanine(1)));
        context.put(ContextKeys.PROTEIN_TOPOLOGY, new Topology(5, List.of()));
        context.put(ContextKeys.RESIDUE_STATES, states(state("A:1", "ALA", "NALA")));

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> new ChargeAssignmentStage(new ThrowingChargeModel()).run(context));

        assertTrue(error.getMessage().contains(ContextKeys.TOPOLOGY_BUILD_REPORT));
    }

    @Test
    void rejectsEmptyResidues() {
        PipelineContext context = contextWith(List.of());
        context.put(ContextKeys.PROTEIN_TOPOLOGY, new Topology(0, List.of()));
        context.put(ContextKeys.TOPOLOGY_BUILD_REPORT, topologyReport(0, 0));
        context.put(ContextKeys.RESIDUE_STATES, Map.of());

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> new ChargeAssignmentStage(new ThrowingChargeModel()).run(context));

        assertTrue(error.getMessage().contains("Run TopologyBuilderStage first"));
    }

    @Test
    void rejectsResidueWithoutState() {
        PipelineContext context = contextWith(List.of(nAlanine(1), cLysine(2)));
        context.put(ContextKeys.PROTEIN_TOPOLOGY, new Topology(15, List.of()));
        context.put(ContextKeys.TOPOLOGY_BUILD_REPORT, topologyReport(2, 15));
        context.put(ContextKeys.RESIDUE_STATES, states(state("A:1", "ALA", "NALA")));

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> new ChargeAssignmentStage(new ThrowingChargeModel()).run(context));

        assertTrue(error.getMessage().contains("Missing residue state"));
    }

    @Test
    void assignsAmberChargesAndTypesByDefaultWithoutCallingModel() throws Exception {
        PipelineContext context = preparedContext(List.of(nAlanine(1), cLysine(2)),
                states(state("A:1", "ALA", "NALA"), state("A:2", "LYS", "CLYS")));

        new ChargeAssignmentStage(new ThrowingChargeModel()).run(context);

        List<Residue> residues = context.require(ContextKeys.PROTEIN_RESIDUES);
        Atom chargedN = residues.getFirst().getAtom("N");
        AtomTemplate amberN = AmberResidueTemplateLibrary.getInstance().getTemplate("NALA").getAtom("N");
        assertEquals(amberN.getCharge(), chargedN.getCharge(), 1e-9);
        assertEquals(amberN.getAmberType(), chargedN.getAmberType());

        Atom chargedOxt = residues.get(1).getAtom("OXT");
        AtomTemplate amberOxt = AmberResidueTemplateLibrary.getInstance().getTemplate("CLYS").getAtom("OXT");
        assertEquals(amberOxt.getCharge(), chargedOxt.getCharge(), 1e-9);
        assertEquals(amberOxt.getAmberType(), chargedOxt.getAmberType());

        ChargeAssignmentReport report = context.require(ContextKeys.CHARGE_ASSIGNMENT_REPORT);
        assertEquals("AMBER", report.source());
        assertEquals(2, report.residueCount());
        assertEquals(15, report.atomCount());
        assertEquals(totalCharge(residues), report.totalCharge(), 1e-9);
        assertEquals(List.of("A:1 -> NALA", "A:2 -> CLYS"), report.assignedTemplates());
    }

    @Test
    void rejectsPresentAtomMissingFromAmberTemplate() {
        Residue broken = nAlanine(1).toBuilder()
                .atoms(List.of(
                        nAlanine(1).getAtom("N"),
                        nAlanine(1).getAtom("CA"),
                        nAlanine(1).getAtom("C"),
                        nAlanine(1).getAtom("O"),
                        nAlanine(1).getAtom("CB"),
                        atom("XX", "C", 8.0, 8.0, 8.0)))
                .build();
        PipelineContext context = preparedContext(List.of(broken), states(state("A:1", "ALA", "NALA")));

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> new ChargeAssignmentStage(new ThrowingChargeModel()).run(context));

        assertTrue(error.getMessage().contains("No Amber atom 'XX'"));
    }

    @Test
    void explicitOverrideUsesChargeModelAfterAmberMetadataAssignment() throws Exception {
        FixedChargeModel model = new FixedChargeModel();
        PipelineContext context = preparedContext(List.of(nAlanine(1)),
                states(state("A:1", "ALA", "NALA")));
        context.put(ContextKeys.OVERRIDE_CHARGES_WITH_MODEL, true);

        new ChargeAssignmentStage(model).run(context);

        List<Residue> residues = context.require(ContextKeys.PROTEIN_RESIDUES);
        for (Residue residue : residues) {
            for (Atom atom : residue.getAtoms()) {
                assertEquals(0.25, atom.getCharge(), 1e-9);
            }
        }
        assertTrue(model.called);
        assertEquals("FixedChargeModel", context.<ChargeAssignmentReport>require(
                ContextKeys.CHARGE_ASSIGNMENT_REPORT).source());
        assertEquals(AmberResidueTemplateLibrary.getInstance().getTemplate("NALA")
                .getAtom("N").getAmberType(), residues.getFirst().getAtom("N").getAmberType());
    }

    @Test
    void explicitOverrideRequiresConfiguredModel() {
        PipelineContext context = preparedContext(List.of(nAlanine(1)),
                states(state("A:1", "ALA", "NALA")));
        context.put(ContextKeys.OVERRIDE_CHARGES_WITH_MODEL, true);

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> new ChargeAssignmentStage(null).run(context));

        assertTrue(error.getMessage().contains("no ChargeModel"));
    }

    @Test
    void reportListsAreDefensiveCopies() throws Exception {
        PipelineContext context = preparedContext(List.of(nAlanine(1)),
                states(state("A:1", "ALA", "NALA")));

        new ChargeAssignmentStage(new ThrowingChargeModel()).run(context);

        ChargeAssignmentReport report = context.require(ContextKeys.CHARGE_ASSIGNMENT_REPORT);
        assertThrows(UnsupportedOperationException.class,
                () -> report.assignedTemplates().add("A:9 -> ALA"));
    }

    private PipelineContext preparedContext(List<Residue> residues, Map<String, ResidueState> states) {
        PipelineContext context = contextWith(residues);
        context.put(ContextKeys.PROTEIN_TOPOLOGY, new Topology(atomCount(residues), List.of()));
        context.put(ContextKeys.TOPOLOGY_BUILD_REPORT, topologyReport(residues.size(), atomCount(residues)));
        context.put(ContextKeys.RESIDUE_STATES, states);
        return context;
    }

    private PipelineContext contextWith(List<Residue> residues) {
        PipelineContext context = new PipelineContext(tempDir, tempDir.resolve("run"));
        context.put(ContextKeys.PROTEIN_RESIDUES, residues);
        return context;
    }

    private TopologyBuildReport topologyReport(int residueCount, int atomCount) {
        return new TopologyBuildReport(residueCount, atomCount, 0, 0, 0, 0, List.of());
    }

    private Map<String, ResidueState> states(ResidueState... states) {
        Map<String, ResidueState> map = new LinkedHashMap<>();
        for (ResidueState state : states) {
            map.put(state.residueKey(), state);
        }
        return map;
    }

    private ResidueState state(String key, String residueName, String amberTemplate) {
        return new ResidueState(key, residueName, residueName, amberTemplate,
                amberTemplate.startsWith("N"), amberTemplate.startsWith("C"), false, "");
    }

    private Residue nAlanine(int number) {
        return residue("ALA", number,
                atom("N", "N", 0.000, 0.000, 0.000),
                atom("CA", "C", 1.460, 0.000, 0.000),
                atom("C", "C", 2.005, 1.419, 0.000),
                atom("O", "O", 1.230, 2.375, 0.000),
                atom("CB", "C", 1.460, -1.089, -1.089));
    }

    private Residue cLysine(int number) {
        return residue("LYS", number,
                atom("N", "N", 3.319, 1.628, 0.000),
                atom("CA", "C", 4.259, 0.510, 0.000),
                atom("C", "C", 3.523, -0.822, 0.000),
                atom("O", "O", 4.159, -1.876, 0.000),
                atom("CB", "C", 5.716, 1.015, 0.000),
                atom("CG", "C", 6.689, -0.182, 0.000),
                atom("CD", "C", 8.146, 0.323, 0.000),
                atom("CE", "C", 9.119, -0.874, 0.000),
                atom("NZ", "N", 10.500, -0.395, 0.000),
                atom("OXT", "O", 2.350, -1.910, 0.000));
    }

    private Residue residue(String name, int number, Atom... atoms) {
        return Residue.builder()
                .name(name)
                .chain("A")
                .number(number)
                .insertionCode(' ')
                .atoms(List.of(atoms))
                .build();
    }

    private Atom atom(String name, String element, double x, double y, double z) {
        return Atom.builder()
                .name(name)
                .position(new Point3D(x, y, z))
                .charge(-9.0)
                .occupancy(1.0)
                .bFactor(80.0)
                .element(Element.builder()
                        .symbol(element)
                        .atomicNumber(0)
                        .atomicMass(0.0)
                        .covalentRadius(0.0)
                        .vdwRadius(0.0)
                        .build())
                .build();
    }

    private int atomCount(List<Residue> residues) {
        return residues.stream().mapToInt(Residue::getAtomCount).sum();
    }

    private double totalCharge(List<Residue> residues) {
        return residues.stream()
                .flatMap(residue -> residue.getAtoms().stream())
                .mapToDouble(Atom::getCharge)
                .sum();
    }

    private static final class ThrowingChargeModel implements ChargeModel {
        @Override
        public double[] computeCharges(ChargeSystem system, double totalFormalCharge) {
            throw new AssertionError("Charge model should not be called unless overrideChargesWithModel=true");
        }
    }

    private static final class FixedChargeModel implements ChargeModel {
        private boolean called;

        @Override
        public double[] computeCharges(ChargeSystem system, double totalFormalCharge) {
            called = true;
            double[] charges = new double[system.size()];
            java.util.Arrays.fill(charges, 0.25);
            return charges;
        }
    }
}
