package totah.lab.pipeline.stage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import totah.lab.pipeline.ContextKeys;
import totah.lab.pipeline.PipelineContext;
import totah.lab.pipeline.report.ResidueStateAssignmentReport;
import totah.lab.pipeline.residue.ResidueState;
import totah.lab.protein.Atom;
import totah.lab.protein.Element;
import totah.lab.protein.Point3D;
import totah.lab.protein.Residue;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResidueStateAssignmentStageTest {

    @TempDir
    Path tempDir;

    @Test
    void assignsAmberTemplatesForTerminiAndInternalStandardResidues() {
        Residue ala = residue("ALA", 1, atom("N", "N"), atom("CA", "C"));
        Residue lys = residue("LYS", 2, atom("N", "N"), atom("CA", "C"));
        Residue gly = residue("GLY", 3, atom("N", "N"), atom("CA", "C"));
        PipelineContext context = contextWith(List.of(ala, lys, gly));

        new ResidueStateAssignmentStage().run(context);

        List<Residue> prepared = context.require(ContextKeys.PROTEIN_RESIDUES);
        assertEquals(List.of("ALA", "LYS", "GLY"), residueNames(prepared));
        assertSame(ala, prepared.get(0));
        assertSame(lys, prepared.get(1));
        assertSame(gly, prepared.get(2));

        Map<String, ResidueState> states = context.require(ContextKeys.RESIDUE_STATES);
        assertState(states.get("A:1"), "NALA", true, false, false);
        assertState(states.get("A:2"), "LYS", false, false, false);
        assertState(states.get("A:3"), "CGLY", false, true, false);

        ResidueStateAssignmentReport report = context.require(ContextKeys.RESIDUE_STATE_REPORT);
        assertEquals(3, report.inputResidues());
        assertEquals(3, report.outputResidues());
        assertTrue(report.convertedResidues().isEmpty());
        assertEquals(List.of("ALA A:1 -> NALA", "LYS A:2 -> LYS", "GLY A:3 -> CGLY"),
                report.assignedTemplates());
    }

    @Test
    void appliesExplicitGlobalHistidineState() {
        PipelineContext context = contextWith(List.of(
                residue("ALA", 1, atom("N", "N")),
                residue("HIS", 2, atom("ND1", "N"), atom("NE2", "N")),
                residue("GLY", 3, atom("N", "N"))));
        context.put(ContextKeys.HIS_PROTONATION_STATE, "HID");

        new ResidueStateAssignmentStage().run(context);

        Map<String, ResidueState> states = context.require(ContextKeys.RESIDUE_STATES);
        assertEquals("HID", states.get("A:2").amberTemplateName());
    }

    @Test
    void appliesPerResidueOverridesFromMapAndString() {
        PipelineContext context = contextWith(List.of(
                residue("ALA", 1, atom("N", "N")),
                residue("HIS", 2, atom("ND1", "N")),
                residue("ASP", 3, atom("OD1", "O")),
                residue("GLY", 4, atom("N", "N"))));
        context.put(ContextKeys.RESIDUE_PROTONATION_OVERRIDES,
                Map.of("A:2", "HIP", "A:3", "ASH"));

        new ResidueStateAssignmentStage().run(context);

        Map<String, ResidueState> mapStates = context.require(ContextKeys.RESIDUE_STATES);
        assertEquals("HIP", mapStates.get("A:2").amberTemplateName());
        assertEquals("ASH", mapStates.get("A:3").amberTemplateName());

        PipelineContext stringContext = contextWith(List.of(
                residue("ALA", 1, atom("N", "N")),
                residue("HIS", 2, atom("ND1", "N")),
                residue("GLY", 3, atom("N", "N"))));
        stringContext.put(ContextKeys.RESIDUE_PROTONATION_OVERRIDES, "A:2=HIE");

        new ResidueStateAssignmentStage().run(stringContext);

        Map<String, ResidueState> stringStates = stringContext.require(ContextKeys.RESIDUE_STATES);
        assertEquals("HIE", stringStates.get("A:2").amberTemplateName());
    }

    @Test
    void preservesInsertionCodesInResidueStateKeysAndOverrides() {
        PipelineContext context = contextWith(List.of(
                residue("ALA", 10, ' ', atom("N", "N")),
                residue("HIS", 10, 'A', atom("ND1", "N"), atom("NE2", "N")),
                residue("GLY", 11, ' ', atom("N", "N"))));
        context.put(ContextKeys.RESIDUE_PROTONATION_OVERRIDES, "A:10A=HIP");

        new ResidueStateAssignmentStage().run(context);

        Map<String, ResidueState> states = context.require(ContextKeys.RESIDUE_STATES);
        assertEquals(3, states.size());
        assertState(states.get("A:10"), "NALA", true, false, false);
        assertState(states.get("A:10A"), "HIP", false, false, false);
        assertState(states.get("A:11"), "CGLY", false, true, false);
    }

    @Test
    void rejectsMalformedOverride() {
        PipelineContext context = contextWith(List.of(
                residue("ALA", 1, atom("N", "N")),
                residue("HIS", 2, atom("ND1", "N")),
                residue("GLY", 3, atom("N", "N"))));
        context.put(ContextKeys.RESIDUE_PROTONATION_OVERRIDES, "A:2:HIE");

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> new ResidueStateAssignmentStage().run(context));

        assertTrue(error.getMessage().contains("A:123=HIE"));
    }

    @Test
    void rejectsIncompatibleOverride() {
        PipelineContext context = contextWith(List.of(
                residue("ALA", 1, atom("N", "N")),
                residue("ALA", 2, atom("CA", "C")),
                residue("GLY", 3, atom("N", "N"))));
        context.put(ContextKeys.RESIDUE_PROTONATION_OVERRIDES, "A:2=HIE");

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> new ResidueStateAssignmentStage().run(context));

        assertTrue(error.getMessage().contains("not compatible"));
    }

    @Test
    void rejectsAutoHistidineStateWithoutExternalSolver() {
        PipelineContext context = contextWith(List.of(
                residue("ALA", 1, atom("N", "N")),
                residue("HIS", 2, atom("ND1", "N")),
                residue("GLY", 3, atom("N", "N"))));
        context.put(ContextKeys.HIS_PROTONATION_STATE, "AUTO");

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> new ResidueStateAssignmentStage().run(context));

        assertTrue(error.getMessage().contains("AUTO"));
    }

    @Test
    void convertsSelenomethionineToMethionineForAmberLookup() {
        Residue mse = residue("MSE", 2,
                atom("N", "N"),
                atom("SE", "SE"),
                atom("CE", "C"));
        PipelineContext context = contextWith(List.of(
                residue("ALA", 1, atom("N", "N")),
                mse,
                residue("GLY", 3, atom("N", "N"))));

        new ResidueStateAssignmentStage().run(context);

        List<Residue> prepared = context.require(ContextKeys.PROTEIN_RESIDUES);
        Residue met = prepared.get(1);
        assertEquals("MET", met.getName());
        assertEquals(List.of("N", "SD", "CE"), atomNames(met));
        assertEquals("S", met.getAtom("SD").getElement().getSymbol());

        Map<String, ResidueState> states = context.require(ContextKeys.RESIDUE_STATES);
        assertEquals("MET", states.get("A:2").amberTemplateName());
        ResidueStateAssignmentReport report = context.require(ContextKeys.RESIDUE_STATE_REPORT);
        assertEquals(List.of("MSE A:2 -> MET"), report.convertedResidues());
    }

    @Test
    void assignsExplicitTysTemplatesForInternalAndTerminalResidues() {
        PipelineContext context = contextWith(List.of(
                residue("TYS", 1, atom("N", "N")),
                residue("TYS", 2, atom("N", "N")),
                residue("TYS", 3, atom("N", "N"))));

        new ResidueStateAssignmentStage().run(context);

        Map<String, ResidueState> states = context.require(ContextKeys.RESIDUE_STATES);
        assertState(states.get("A:1"), "NTYS", true, false, false);
        assertState(states.get("A:2"), "TYS", false, false, false);
        assertState(states.get("A:3"), "CTYS", false, true, false);

        ResidueStateAssignmentReport report = context.require(ContextKeys.RESIDUE_STATE_REPORT);
        assertEquals(List.of("TYS A:1 -> NTYS", "TYS A:2 -> TYS", "TYS A:3 -> CTYS"),
                report.assignedTemplates());
    }

    @Test
    void detectsDisulfidesAndAssignsCyxTemplates() {
        PipelineContext context = contextWith(List.of(
                residue("CYS", 1, atomAt("SG", "S", 0.0, 0.0, 0.0)),
                residue("ALA", 2, atomAt("CA", "C", 8.0, 0.0, 0.0)),
                residue("CYS", 3, atomAt("SG", "S", 2.0, 0.0, 0.0))));

        new ResidueStateAssignmentStage().run(context);

        Map<String, ResidueState> states = context.require(ContextKeys.RESIDUE_STATES);
        assertState(states.get("A:1"), "NCYX", true, false, true);
        assertState(states.get("A:3"), "CCYX", false, true, true);

        ResidueStateAssignmentReport report = context.require(ContextKeys.RESIDUE_STATE_REPORT);
        assertEquals(List.of("CYS A:1", "CYS A:3"), report.disulfideResidues());
        Set<Residue> disulfides = context.require(ContextKeys.DISULFIDE_BONDS);
        assertEquals(2, disulfides.size());
    }

    @Test
    void appliesInternalPhDrivenStatesOnlyWhenAmberTemplateExists() {
        PipelineContext context = contextWith(List.of(
                residue("ALA", 1, atom("N", "N")),
                residue("ASP", 2, atom("OD1", "O")),
                residue("GLU", 3, atom("OE1", "O")),
                residue("GLY", 4, atom("N", "N"))));
        context.put(ContextKeys.PH, 2.0);

        new ResidueStateAssignmentStage().run(context);

        Map<String, ResidueState> states = context.require(ContextKeys.RESIDUE_STATES);
        assertEquals("ASH", states.get("A:2").amberTemplateName());
        assertEquals("GLH", states.get("A:3").amberTemplateName());

        PipelineContext highPh = contextWith(List.of(
                residue("ALA", 1, atom("N", "N")),
                residue("CYS", 2, atom("SG", "S")),
                residue("LYS", 3, atom("NZ", "N")),
                residue("GLY", 4, atom("N", "N"))));
        highPh.put(ContextKeys.PH, 12.0);
        highPh.put(ContextKeys.DETECT_DISULFIDES, false);

        new ResidueStateAssignmentStage().run(highPh);

        Map<String, ResidueState> highPhStates = highPh.require(ContextKeys.RESIDUE_STATES);
        assertEquals("CYM", highPhStates.get("A:2").amberTemplateName());
        assertEquals("LYN", highPhStates.get("A:3").amberTemplateName());
    }

    @Test
    void rejectsTerminalProtonationVariantWhenAmberLibraryHasNoTemplate() {
        PipelineContext context = contextWith(List.of(
                residue("ASP", 1, atom("OD1", "O")),
                residue("ALA", 2, atom("CA", "C")),
                residue("GLY", 3, atom("N", "N"))));
        context.put(ContextKeys.PH, 2.0);

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> new ResidueStateAssignmentStage().run(context));

        assertTrue(error.getMessage().contains("No Amber template 'NASH'"));
    }

    @Test
    void rejectsSingleResidueChainBecauseCombinedTerminalTemplateIsUnsupported() {
        PipelineContext context = contextWith(List.of(residue("ALA", 1, atom("CA", "C"))));

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> new ResidueStateAssignmentStage().run(context));

        assertTrue(error.getMessage().contains("both N- and C-terminal"));
    }

    @Test
    void reportListsAndResidueStatesAreDefensiveCopies() {
        PipelineContext context = contextWith(List.of(
                residue("ALA", 1, atom("N", "N")),
                residue("LYS", 2, atom("NZ", "N")),
                residue("GLY", 3, atom("N", "N"))));

        new ResidueStateAssignmentStage().run(context);

        ResidueStateAssignmentReport report = context.require(ContextKeys.RESIDUE_STATE_REPORT);
        assertThrows(UnsupportedOperationException.class,
                () -> report.assignedTemplates().add("ALA A:99 -> ALA"));

        Map<String, ResidueState> states = context.require(ContextKeys.RESIDUE_STATES);
        assertThrows(UnsupportedOperationException.class,
                () -> states.put("A:99", new ResidueState("A:99", "ALA", "ALA",
                        "ALA", false, false, false, "")));
    }

    private PipelineContext contextWith(List<Residue> residues) {
        PipelineContext context = new PipelineContext(tempDir, tempDir.resolve("run"));
        context.put(ContextKeys.PROTEIN_RESIDUES, residues);
        return context;
    }

    private Residue residue(String name, int number, Atom... atoms) {
        return residue(name, number, ' ', atoms);
    }

    private Residue residue(String name, int number, char insertionCode, Atom... atoms) {
        return Residue.builder()
                .name(name)
                .chain("A")
                .number(number)
                .insertionCode(insertionCode)
                .atoms(List.of(atoms))
                .build();
    }

    private Atom atom(String name, String element) {
        return atomAt(name, element, 0.0, 0.0, 0.0);
    }

    private Atom atomAt(String name, String element, double x, double y, double z) {
        return Atom.builder()
                .name(name)
                .position(new Point3D(x, y, z))
                .occupancy(1.0)
                .bFactor(80.0)
                .charge(0.0)
                .element(Element.builder()
                        .symbol(element)
                        .atomicNumber(0)
                        .atomicMass(0.0)
                        .covalentRadius(0.0)
                        .vdwRadius(0.0)
                        .build())
                .build();
    }

    private void assertState(ResidueState state, String amberTemplate,
                             boolean nTerminus, boolean cTerminus, boolean disulfide) {
        assertEquals(amberTemplate, state.amberTemplateName());
        assertEquals(nTerminus, state.nTerminus());
        assertEquals(cTerminus, state.cTerminus());
        assertEquals(disulfide, state.disulfide());
    }

    private List<String> residueNames(List<Residue> residues) {
        return residues.stream()
                .map(Residue::getName)
                .toList();
    }

    private List<String> atomNames(Residue residue) {
        return residue.getAtoms().stream()
                .map(Atom::getName)
                .toList();
    }
}
