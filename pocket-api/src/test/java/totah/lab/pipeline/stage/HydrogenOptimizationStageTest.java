package totah.lab.pipeline.stage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import totah.lab.pipeline.ContextKeys;
import totah.lab.pipeline.PipelineContext;
import totah.lab.pipeline.report.HydrogenOptimizationReport;
import totah.lab.pipeline.report.HydrogenationReport;
import totah.lab.pipeline.residue.ResidueState;
import totah.lab.protein.Atom;
import totah.lab.protein.Element;
import totah.lab.protein.Point3D;
import totah.lab.protein.Residue;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HydrogenOptimizationStageTest {

    @TempDir
    Path tempDir;

    @Test
    void requiresHydrogenationReportFromReceptorHydrogenationStage() {
        PipelineContext context = contextWith(List.of(serine(1), aspartateAcceptor(2)));
        context.put(ContextKeys.RESIDUE_STATES, states(
                state("A:1", "SER"),
                state("A:2", "CASP")));

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> new HydrogenOptimizationStage().run(context));

        assertTrue(error.getMessage().contains(ContextKeys.HYDROGENATION_REPORT));
    }

    @Test
    void rejectsEmptyResidueInput() {
        PipelineContext context = contextWith(List.of());
        context.put(ContextKeys.HYDROGENATION_REPORT, hydrogenationReport(0));
        context.put(ContextKeys.RESIDUE_STATES, Map.of());

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> new HydrogenOptimizationStage().run(context));

        assertTrue(error.getMessage().contains("Run ReceptorHydrogenationStage first"));
    }

    @Test
    void rejectsResidueWithoutAssignedState() {
        PipelineContext context = contextWith(List.of(serine(1), aspartateAcceptor(2)));
        context.put(ContextKeys.HYDROGENATION_REPORT, hydrogenationReport(2));
        context.put(ContextKeys.RESIDUE_STATES, states(state("A:1", "SER")));

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> new HydrogenOptimizationStage().run(context));

        assertTrue(error.getMessage().contains("Missing residue state"));
    }

    @Test
    void optimizesHydrogenPositionsWithoutChangingHeavyAtomOrder() throws Exception {
        Residue ser = serine(1);
        Residue acceptor = aspartateAcceptor(2);
        PipelineContext context = contextWith(List.of(ser, acceptor));
        context.put(ContextKeys.HYDROGENATION_REPORT, hydrogenationReport(2));
        context.put(ContextKeys.RESIDUE_STATES, states(
                state("A:1", "SER"),
                state("A:2", "CASP")));

        new HydrogenOptimizationStage().run(context);

        List<Residue> optimized = context.require(ContextKeys.PROTEIN_RESIDUES);
        Residue optimizedSer = optimized.getFirst();
        assertEquals(heavyAtomNames(ser), heavyAtomNames(optimizedSer));
        assertEquals(hydrogenNames(ser), hydrogenNames(optimizedSer));
        assertTrue(ser.getAtom("HG").getPosition().distance(optimizedSer.getAtom("HG").getPosition()) > 1e-6);

        HydrogenOptimizationReport report = context.require(ContextKeys.HYDROGEN_OPTIMIZATION_REPORT);
        assertEquals(2, report.inputResidues());
        assertEquals(2, report.outputResidues());
        assertEquals(1, report.optimizedResidues());
        assertTrue(report.movedHydrogens() > 0);
        assertEquals(List.of("SER A:1"), report.optimizedResidueLabels());
    }

    @Test
    void preservesFixedHistidineTemplateDespiteEnvironmentFavoringOtherTautomer() throws Exception {
        Residue his = histidineHid(1);
        Residue acceptorNearNe2 = residue("ASP", 2,
                atom("OD1", "O", 1.5, 3.0, 0.0));
        PipelineContext context = contextWith(List.of(his, acceptorNearNe2));
        context.put(ContextKeys.HYDROGENATION_REPORT, hydrogenationReport(2));
        context.put(ContextKeys.RESIDUE_STATES, states(
                state("A:1", "HID"),
                state("A:2", "CASP")));

        new HydrogenOptimizationStage().run(context);

        Residue optimizedHis = residues(context).getFirst();
        assertTrue(hasAtom(optimizedHis, "HD1"));
        assertFalse(hasAtom(optimizedHis, "HE2"));
    }

    @Test
    void preservesHeavyAtomCoordinatesForAmideAndHistidineResidues() throws Exception {
        Residue gln = glutamine(1);
        Residue his = histidineHid(2);
        Residue asn = asparagine(3);
        Residue acceptor = residue("ASP", 4, atom("OD1", "O", 1.5, 3.0, 0.0));
        PipelineContext context = contextWith(List.of(gln, his, asn, acceptor));
        context.put(ContextKeys.HYDROGENATION_REPORT, hydrogenationReport(4));
        context.put(ContextKeys.RESIDUE_STATES, states(
                state("A:1", "GLN"),
                state("A:2", "HID"),
                state("A:3", "ASN"),
                state("A:4", "CASP")));

        new HydrogenOptimizationStage().run(context);

        List<Residue> optimized = residues(context);
        assertHeavyAtomCoordinatesUnchanged(gln, optimized.get(0));
        assertHeavyAtomCoordinatesUnchanged(his, optimized.get(1));
        assertHeavyAtomCoordinatesUnchanged(asn, optimized.get(2));
    }

    @Test
    void failsWhenOptimizationWouldChangeHydrogenIdentities() {
        Residue his = histidineHid(1);
        Residue acceptorNearNe2 = residue("ASP", 2,
                atom("OD1", "O", 1.5, 3.0, 0.0));
        PipelineContext context = contextWith(List.of(his, acceptorNearNe2));
        context.put(ContextKeys.HYDROGENATION_REPORT, hydrogenationReport(2));
        context.put(ContextKeys.RESIDUE_STATES, states(
                state("A:1", "HIE"),
                state("A:2", "CASP")));

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> new HydrogenOptimizationStage().run(context));

        assertTrue(error.getMessage().contains("hydrogen identities"));
    }

    @Test
    void rejectsInvalidHydrogenClashCutoff() {
        PipelineContext context = contextWith(List.of(serine(1), aspartateAcceptor(2)));
        context.put(ContextKeys.HYDROGENATION_REPORT, hydrogenationReport(2));
        context.put(ContextKeys.RESIDUE_STATES, states(
                state("A:1", "SER"),
                state("A:2", "CASP")));
        context.put(ContextKeys.HYDROGEN_CLASH_CUTOFF, "NaN");

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> new HydrogenOptimizationStage().run(context));

        assertTrue(error.getMessage().contains(ContextKeys.HYDROGEN_CLASH_CUTOFF));
    }

    @Test
    void failsWhenConfiguredAmberParametersCannotLoad() {
        PipelineContext context = contextWith(List.of(serine(1), aspartateAcceptor(2)));
        context.put(ContextKeys.HYDROGENATION_REPORT, hydrogenationReport(2));
        context.put(ContextKeys.RESIDUE_STATES, states(
                state("A:1", "SER"),
                state("A:2", "CASP")));
        context.put(ContextKeys.AMBER_PARM_PATH, tempDir.resolve("missing.dat"));

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> new HydrogenOptimizationStage().run(context));

        assertTrue(error.getMessage().contains("Amber Lennard-Jones"));
    }

    @Test
    void reportListsAreDefensiveCopies() throws Exception {
        PipelineContext context = contextWith(List.of(serine(1), aspartateAcceptor(2)));
        context.put(ContextKeys.HYDROGENATION_REPORT, hydrogenationReport(2));
        context.put(ContextKeys.RESIDUE_STATES, states(
                state("A:1", "SER"),
                state("A:2", "CASP")));

        new HydrogenOptimizationStage().run(context);

        HydrogenOptimizationReport report = context.require(ContextKeys.HYDROGEN_OPTIMIZATION_REPORT);
        assertThrows(UnsupportedOperationException.class,
                () -> report.optimizedResidueLabels().add("ALA A:9"));
    }

    private PipelineContext contextWith(List<Residue> residues) {
        PipelineContext context = new PipelineContext(tempDir, tempDir.resolve("run"));
        context.put(ContextKeys.PROTEIN_RESIDUES, residues);
        context.put(ContextKeys.HYDROGEN_CLASH_CUTOFF, 0.05);
        return context;
    }

    private HydrogenationReport hydrogenationReport(int residues) {
        return new HydrogenationReport(residues, residues, 0, 1, List.of(), List.of());
    }

    private Map<String, ResidueState> states(ResidueState... states) {
        Map<String, ResidueState> map = new LinkedHashMap<>();
        for (ResidueState state : states) {
            map.put(state.residueKey(), state);
        }
        return map;
    }

    private ResidueState state(String key, String amberTemplate) {
        return new ResidueState(key, amberTemplate, amberTemplate, amberTemplate,
                amberTemplate.startsWith("N"), amberTemplate.startsWith("C"), amberTemplate.endsWith("CYX"), "");
    }

    private Residue serine(int number) {
        return residue("SER", number,
                atom("CA", "C", 0.0, 0.0, 0.0),
                atom("CB", "C", 0.0, 1.5, 0.0),
                atom("OG", "O", 0.0, 2.5, 0.0),
                atom("HG", "H", 0.5, 2.8, 0.0));
    }

    private Residue aspartateAcceptor(int number) {
        return residue("ASP", number,
                atom("CA", "C", 5.0, 0.0, 0.0),
                atom("CB", "C", 5.0, 1.5, 0.0),
                atom("CG", "C", 5.0, 2.5, 0.0),
                atom("OD1", "O", 1.5, 4.5, 0.0),
                atom("OD2", "O", 6.0, 3.0, 0.0));
    }

    private Residue asparagine(int number) {
        return residue("ASN", number,
                atom("CA", "C", 0.0, 0.0, 0.0),
                atom("CB", "C", 0.0, 1.5, 0.0),
                atom("CG", "C", 0.0, 2.5, 0.0),
                atom("OD1", "O", 1.2, 3.0, 0.0),
                atom("ND2", "N", -1.2, 3.0, 0.0),
                atom("HD21", "H", -1.5, 3.5, 0.0),
                atom("HD22", "H", -1.5, 2.5, 0.0));
    }

    private Residue glutamine(int number) {
        return residue("GLN", number,
                atom("CA", "C", 0.0, 0.0, 0.0),
                atom("CB", "C", 0.0, 1.5, 0.0),
                atom("CG", "C", 0.0, 2.5, 0.0),
                atom("CD", "C", 0.0, 3.5, 0.0),
                atom("OE1", "O", 1.2, 4.0, 0.0),
                atom("NE2", "N", -1.2, 4.0, 0.0),
                atom("HE21", "H", -1.5, 4.5, 0.0),
                atom("HE22", "H", -1.5, 3.5, 0.0));
    }

    private Residue histidineHid(int number) {
        return residue("HIS", number,
                atom("CA", "C", 0.0, 0.0, 0.0),
                atom("CB", "C", 0.0, 1.5, 0.0),
                atom("CG", "C", 0.0, 2.5, 0.0),
                atom("ND1", "N", -0.5, 3.0, 0.0),
                atom("CD2", "C", 0.0, 3.5, 0.0),
                atom("CE1", "C", -0.5, 3.5, 0.0),
                atom("NE2", "N", 0.5, 3.0, 0.0),
                atom("HB2", "H", -0.5, 1.8, 0.0),
                atom("HB3", "H", 0.5, 1.8, 0.0),
                atom("HD2", "H", 0.0, 4.5, 0.0),
                atom("HE1", "H", -1.0, 4.0, 0.0),
                atom("HD1", "H", -0.8, 2.5, 0.0));
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
                .bFactor(80.0)
                .charge(0.0)
                .occupancy(1.0)
                .element(Element.builder()
                        .symbol(element)
                        .atomicNumber("H".equals(element) ? 1 : 0)
                        .atomicMass(0.0)
                        .covalentRadius(0.0)
                        .vdwRadius(0.0)
                        .build())
                .build();
    }

    private List<Residue> residues(PipelineContext context) {
        return context.require(ContextKeys.PROTEIN_RESIDUES);
    }

    private boolean hasAtom(Residue residue, String atomName) {
        return residue.getAtoms().stream().anyMatch(atom -> atomName.equals(atom.getName()));
    }

    private List<String> heavyAtomNames(Residue residue) {
        return residue.getAtoms().stream()
                .filter(atom -> !"H".equals(atom.getElement().getSymbol()))
                .map(Atom::getName)
                .toList();
    }

    private List<String> hydrogenNames(Residue residue) {
        return residue.getAtoms().stream()
                .filter(atom -> "H".equals(atom.getElement().getSymbol()))
                .map(Atom::getName)
                .sorted()
                .toList();
    }

    private void assertHeavyAtomCoordinatesUnchanged(Residue before, Residue after) {
        for (Atom beforeAtom : before.getAtoms()) {
            if ("H".equals(beforeAtom.getElement().getSymbol())) continue;
            Atom afterAtom = after.getAtom(beforeAtom.getName());
            assertEquals(beforeAtom.getPosition(), afterAtom.getPosition(), beforeAtom.getName());
        }
    }
}
