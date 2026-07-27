package totah.lab.pipeline.stage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import totah.lab.pipeline.ContextKeys;
import totah.lab.pipeline.PipelineContext;
import totah.lab.protein.Atom;
import totah.lab.protein.Element;
import totah.lab.protein.Point3D;
import totah.lab.protein.Residue;
import totah.lab.protein.Topology;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AD4AtomTypingStageTest {

    @TempDir
    Path tempDir;

    @Test
    void requiresChargeAssignmentReport() {
        PipelineContext context = contextWith(List.of(alanineWithHydrogens(1)),
                new Topology(7, List.of(edge(0, 1), edge(1, 2))));
        context.put(ContextKeys.RESIDUE_STATES, states(state("A:1", "ALA", "NALA")));

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> new AD4AtomTypingStage().run(context));

        assertTrue(error.getMessage().contains(ContextKeys.CHARGE_ASSIGNMENT_REPORT));
    }

    @Test
    void rejectsEmptyResidues() {
        PipelineContext context = contextWith(List.of(), new Topology(0, List.of()));
        context.put(ContextKeys.CHARGE_ASSIGNMENT_REPORT, chargeReport(0, 0));
        context.put(ContextKeys.RESIDUE_STATES, Map.of());

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> new AD4AtomTypingStage().run(context));

        assertTrue(error.getMessage().contains("Run ChargeAssignmentStage first"));
    }

    @Test
    void rejectsMissingAmberTypeFromChargeStage() {
        Residue residue = residue("ALA", 1,
                atom("N", "N", "N", 0.0, 0.0, 0.0),
                atom("CA", "C", null, 1.4, 0.0, 0.0));
        PipelineContext context = contextWith(List.of(residue), new Topology(2, List.of(edge(0, 1))));
        context.put(ContextKeys.CHARGE_ASSIGNMENT_REPORT, chargeReport(1, 2));
        context.put(ContextKeys.RESIDUE_STATES, states(state("A:1", "ALA", "NALA")));

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> new AD4AtomTypingStage().run(context));

        assertTrue(error.getMessage().contains("Missing Amber atom type"));
    }

    @Test
    void assignsHydrogenTypeFromTopologyParentNotDistance() {
        Residue residue = residue("ALA", 1,
                atom("C1", "C", "CT", 0.0, 0.0, 0.0),
                atom("N1", "N", "N", 0.2, 0.0, 0.0),
                atom("H1", "H", "H1", 0.3, 0.0, 0.0));
        PipelineContext context = contextWith(List.of(residue),
                new Topology(3, List.of(edge(0, 2), edge(0, 1))));
        context.put(ContextKeys.CHARGE_ASSIGNMENT_REPORT, chargeReport(1, 3));
        context.put(ContextKeys.RESIDUE_STATES, states(state("A:1", "ALA", "ALA")));

        new AD4AtomTypingStage().run(context);

        Residue typed = residues(context).getFirst();
        assertEquals("H", typed.getAtom("H1").getAutoDockType(),
                "H should be non-polar because topology bonds it to carbon");
    }

    @Test
    void typesHistidineNitrogensByTopologyBondedHydrogens() {
        Residue his = residue("HIS", 1,
                atom("CG", "C", "CC", 0.0, 0.0, 0.0),
                atom("ND1", "N", "NA", -1.0, 0.0, 0.0),
                atom("CE1", "C", "CR", 0.0, 1.0, 0.0),
                atom("NE2", "N", "NB", 1.0, 0.0, 0.0),
                atom("HD1", "H", "H", -1.0, -1.0, 0.0));
        PipelineContext context = contextWith(List.of(his),
                new Topology(5, List.of(edge(0, 1), edge(1, 2), edge(2, 3), edge(1, 4))));
        context.put(ContextKeys.CHARGE_ASSIGNMENT_REPORT, chargeReport(1, 5));
        context.put(ContextKeys.RESIDUE_STATES, states(state("A:1", "HIS", "HID")));

        new AD4AtomTypingStage().run(context);

        Residue typed = residues(context).getFirst();
        assertEquals("N", typed.getAtom("ND1").getAutoDockType());
        assertEquals("NA", typed.getAtom("NE2").getAutoDockType());
        assertEquals("A", typed.getAtom("CG").getAutoDockType());
    }

    @Test
    void typesCysteineSulfurByAmberState() {
        Residue cym = residue("CYS", 1, atom("SG", "S", "SH", 0.0, 0.0, 0.0));
        Residue cyx = residue("CYS", 2, atom("SG", "S", "S", 2.0, 0.0, 0.0));
        PipelineContext context = contextWith(List.of(cym, cyx), new Topology(2, List.of(edge(0, 1))));
        context.put(ContextKeys.CHARGE_ASSIGNMENT_REPORT, chargeReport(2, 2));
        context.put(ContextKeys.RESIDUE_STATES, states(
                state("A:1", "CYS", "CYM"),
                state("A:2", "CYS", "CYX", true)));

        new AD4AtomTypingStage().run(context);

        List<Residue> typed = residues(context);
        assertEquals("SA", typed.get(0).getAtom("SG").getAutoDockType());
        assertEquals("S", typed.get(1).getAtom("SG").getAutoDockType());
    }

    @Test
    void rejectsUnsupportedElementInsteadOfFallingBackToCarbon() {
        Residue residue = residue("MSE", 1, atom("SE", "Se", "Se", 0.0, 0.0, 0.0));
        PipelineContext context = contextWith(List.of(residue), new Topology(1, List.of()));
        context.put(ContextKeys.CHARGE_ASSIGNMENT_REPORT, chargeReport(1, 1));
        context.put(ContextKeys.RESIDUE_STATES, states(state("A:1", "MSE", "MET")));

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> new AD4AtomTypingStage().run(context));

        assertTrue(error.getMessage().contains("Unsupported AD4 element"));
    }

    @Test
    void publishesReportWithTypeCountsAndDefensiveMap() {
        PipelineContext context = contextWith(List.of(alanineWithHydrogens(1)),
                new Topology(7, List.of(
                        edge(0, 1), edge(1, 2), edge(1, 4), edge(0, 5), edge(4, 6))));
        context.put(ContextKeys.CHARGE_ASSIGNMENT_REPORT, chargeReport(1, 7));
        context.put(ContextKeys.RESIDUE_STATES, states(state("A:1", "ALA", "NALA")));

        new AD4AtomTypingStage().run(context);

        AD4AtomTypingReport report = context.require(ContextKeys.AD4_ATOM_TYPING_REPORT);
        assertEquals(1, report.residueCount());
        assertEquals(7, report.atomCount());
        assertEquals(3, report.typeCounts().get("C"));
        assertEquals(1, report.typeCounts().get("N"));
        assertEquals(1, report.typeCounts().get("OA"));
        assertEquals(1, report.typeCounts().get("HD"));
        assertEquals(1, report.typeCounts().get("H"));
        assertThrows(UnsupportedOperationException.class, () -> report.typeCounts().put("X", 1));
    }

    private PipelineContext contextWith(List<Residue> residues, Topology topology) {
        PipelineContext context = new PipelineContext(tempDir, tempDir.resolve("run"));
        context.put(ContextKeys.PROTEIN_RESIDUES, residues);
        context.put(ContextKeys.PROTEIN_TOPOLOGY, topology);
        return context;
    }

    private ChargeAssignmentReport chargeReport(int residues, int atoms) {
        return new ChargeAssignmentReport(residues, atoms, "AMBER", 0.0, List.of());
    }

    private Map<String, ResidueState> states(ResidueState... states) {
        Map<String, ResidueState> map = new LinkedHashMap<>();
        for (ResidueState state : states) {
            map.put(state.residueKey(), state);
        }
        return map;
    }

    private ResidueState state(String key, String residueName, String amberTemplate) {
        return state(key, residueName, amberTemplate, false);
    }

    private ResidueState state(String key, String residueName, String amberTemplate, boolean disulfide) {
        return new ResidueState(key, residueName, residueName, amberTemplate,
                amberTemplate.startsWith("N"), amberTemplate.startsWith("C"), disulfide, "");
    }

    private Residue alanineWithHydrogens(int number) {
        return residue("ALA", number,
                atom("N", "N", "N3", 0.0, 0.0, 0.0),
                atom("CA", "C", "CT", 1.4, 0.0, 0.0),
                atom("C", "C", "C", 2.0, 1.3, 0.0),
                atom("O", "O", "O", 1.4, 2.3, 0.0),
                atom("CB", "C", "CT", 1.4, -1.0, 0.0),
                atom("H1", "H", "H", -0.8, 0.0, 0.0),
                atom("HB1", "H", "HC", 1.4, -1.8, 0.0));
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

    private Atom atom(String name, String element, String amberType, double x, double y, double z) {
        return Atom.builder()
                .name(name)
                .amberType(amberType)
                .position(new Point3D(x, y, z))
                .charge(0.1)
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

    private Topology.Edge edge(int a, int b) {
        return new Topology.Edge(a, b, 1.0);
    }

    private List<Residue> residues(PipelineContext context) {
        return context.require(ContextKeys.PROTEIN_RESIDUES);
    }
}
