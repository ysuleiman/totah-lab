package totah.lab.pipeline.stage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import totah.lab.pipeline.ContextKeys;
import totah.lab.pipeline.PipelineContext;
import totah.lab.pipeline.report.HydrogenationReport;
import totah.lab.pipeline.residue.ResidueState;
import totah.lab.protein.Atom;
import totah.lab.protein.Element;
import totah.lab.protein.Point3D;
import totah.lab.protein.Residue;
import totah.lab.topology.AmberResidueTemplateLibrary;
import totah.lab.topology.ResidueTemplate;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReceptorHydrogenationStageTest {

    @TempDir
    Path tempDir;

    @Test
    void requiresResidueStatesFromResidueStateAssignmentStage() {
        PipelineContext context = contextWith(List.of(alanine(1)));

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> new ReceptorHydrogenationStage().run(context));

        assertTrue(error.getMessage().contains(ContextKeys.RESIDUE_STATES));
    }

    @Test
    void rejectsEmptyResidueInput() {
        PipelineContext context = contextWith(List.of());
        context.put(ContextKeys.RESIDUE_STATES, Map.of());

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> new ReceptorHydrogenationStage().run(context));

        assertTrue(error.getMessage().contains("Run ResidueStateAssignmentStage first"));
    }

    @Test
    void rejectsResidueWithoutAssignedState() {
        PipelineContext context = contextWith(List.of(alanine(1), glycine(2)));
        context.put(ContextKeys.RESIDUE_STATES, states(state("A:1", "ALA", "NALA")));

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> new ReceptorHydrogenationStage().run(context));

        assertTrue(error.getMessage().contains("Missing residue state"));
    }

    @Test
    void stripsExistingHydrogensAndPublishesReport() throws Exception {
        Residue residue = alanine(1).toBuilder()
                .atoms(List.of(
                        atom("N", "N", 0.0, 0.0, 0.0),
                        atom("CA", "C", 1.5, 0.0, 0.0),
                        atom("C", "C", 2.0, 1.4, 0.0),
                        atom("O", "O", 2.9, 1.8, 0.0),
                        atom("CB", "C", 1.3, -0.8, 1.2),
                        atom("HOLD", "H", -9.0, -9.0, -9.0)))
                .build();
        PipelineContext context = contextWith(List.of(residue));
        context.put(ContextKeys.RESIDUE_STATES, states(state("A:1", "ALA", "NALA")));

        new ReceptorHydrogenationStage().run(context);

        List<Residue> result = context.require(ContextKeys.PROTEIN_RESIDUES);
        assertFalse(hasAtom(result.getFirst(), "HOLD"));
        assertTrue(hydrogenCount(result) > 0);

        HydrogenationReport report = context.require(ContextKeys.HYDROGENATION_REPORT);
        assertEquals(1, report.inputResidues());
        assertEquals(1, report.outputResidues());
        assertEquals(1, report.strippedHydrogens());
        assertEquals(hydrogenCount(result), report.outputHydrogens());
        assertEquals(List.of("A:1 -> NALA"), report.assignedTemplates());
    }

    @Test
    void usesAshTemplateRatherThanGlobalPhForAspHydrogenation() throws Exception {
        PipelineContext context = contextWith(List.of(aspartate(1)));
        context.put(ContextKeys.PH, 7.4);
        context.put(ContextKeys.RESIDUE_STATES, states(state("A:1", "ASP", "ASH")));

        new ReceptorHydrogenationStage().run(context);

        Residue result = residues(context).getFirst();
        assertTrue(hasAtom(result, "HD2"));
    }

    @Test
    void usesGlhTemplateRatherThanGlobalPhForGluHydrogenation() throws Exception {
        PipelineContext context = contextWith(List.of(glutamate(1)));
        context.put(ContextKeys.PH, 7.4);
        context.put(ContextKeys.RESIDUE_STATES, states(state("A:1", "GLU", "GLH")));

        new ReceptorHydrogenationStage().run(context);

        Residue result = residues(context).getFirst();
        assertTrue(hasAtom(result, "HE2"));
    }

    @Test
    void glycineHydrogensUseAmberTemplateNames() throws Exception {
        PipelineContext context = contextWith(List.of(glycine(1)));
        context.put(ContextKeys.RESIDUE_STATES, states(state("A:1", "GLY", "NGLY")));

        new ReceptorHydrogenationStage().run(context);

        Residue result = residues(context).getFirst();
        assertFalse(hasAtom(result, "HA1"));
        assertTrue(hasAtom(result, "HA2"));
        assertTrue(hasAtom(result, "HA3"));
    }

    @Test
    void skipsSideChainHydrogenWhenTetrahedralAnchorsAreIncomplete() throws Exception {
        Residue incompleteValine = residue("VAL", 1,
                atom("N", "N", 0.0, 0.0, 0.0),
                atom("CA", "C", 1.5, 0.0, 0.0),
                atom("C", "C", 2.0, 1.4, 0.0),
                atom("O", "O", 2.9, 1.8, 0.0),
                atom("CB", "C", 1.3, -0.8, 1.2),
                atom("CG1", "C", 2.4, -1.4, 1.8));
        PipelineContext context = contextWith(List.of(incompleteValine));
        context.put(ContextKeys.RESIDUE_STATES, states(state("A:1", "VAL", "NVAL")));

        new ReceptorHydrogenationStage().run(context);

        Residue result = residues(context).getFirst();
        assertFalse(hasAtom(result, "HB"));
        assertTrue(hasAtom(result, "HG11"));
        assertTrue(hasAtom(result, "HA"));
    }

    @Test
    void usesLynTemplateForNeutralLysineAtPhysiologicPh() throws Exception {
        PipelineContext context = contextWith(List.of(lysine(1)));
        context.put(ContextKeys.PH, 7.4);
        context.put(ContextKeys.RESIDUE_STATES, states(state("A:1", "LYS", "LYN")));

        new ReceptorHydrogenationStage().run(context);

        Residue result = residues(context).getFirst();
        assertEquals(2, atomNameCount(result, "HZ"));
    }

    @Test
    void skipsLysineHydrogensWhenSideChainAnchorsAreIncomplete() throws Exception {
        Residue incompleteLysine = residue("LYS", 1,
                atom("N", "N", 0.0, 0.0, 0.0),
                atom("CA", "C", 1.5, 0.0, 0.0),
                atom("C", "C", 2.0, 1.4, 0.0),
                atom("O", "O", 2.9, 1.8, 0.0),
                atom("CB", "C", 1.3, -0.8, 1.2),
                atom("CG", "C", 2.3, -1.5, 1.9));
        PipelineContext context = contextWith(List.of(incompleteLysine));
        context.put(ContextKeys.RESIDUE_STATES, states(state("A:1", "LYS", "NLYS")));

        new ReceptorHydrogenationStage().run(context);

        Residue result = residues(context).getFirst();
        assertTrue(hasAtom(result, "HB2"));
        assertFalse(hasAtom(result, "HG2"));
        assertTrue(hasAtom(result, "HA"));
    }

    @Test
    void usesHistidineTemplateRatherThanGlobalHisSetting() throws Exception {
        PipelineContext context = contextWith(List.of(histidine(1)));
        context.put(ContextKeys.HIS_PROTONATION_STATE, "HID");
        context.put(ContextKeys.RESIDUE_STATES, states(state("A:1", "HIS", "HIP")));

        new ReceptorHydrogenationStage().run(context);

        Residue result = residues(context).getFirst();
        assertTrue(hasAtom(result, "HD1"));
        assertTrue(hasAtom(result, "HE2"));
    }

    @Test
    void usesCyxStateToSuppressThiolHydrogenAndPublishDisulfideEvenWhenDetectionDisabled() throws Exception {
        PipelineContext context = contextWith(List.of(cysteine(1), alanine(2), cysteine(3)));
        context.put(ContextKeys.DETECT_DISULFIDES, false);
        context.put(ContextKeys.RESIDUE_STATES, states(
                state("A:1", "CYS", "NCYX", true),
                state("A:2", "ALA", "ALA"),
                state("A:3", "CYS", "CCYX", true)));

        new ReceptorHydrogenationStage().run(context);

        List<Residue> result = residues(context);
        assertFalse(hasAtom(result.get(0), "HG"));
        assertFalse(hasAtom(result.get(2), "HG"));
        Set<Residue> disulfides = context.require(ContextKeys.DISULFIDE_BONDS);
        assertEquals(2, disulfides.size());

        HydrogenationReport report = context.require(ContextKeys.HYDROGENATION_REPORT);
        assertEquals(List.of("CYS A:1", "CYS A:3"), report.disulfideResidues());
    }

    @Test
    void usesTysTemplateForRingHydrogensAndCterminalCapWithoutSulfateProton() throws Exception {
        PipelineContext context = contextWith(List.of(tyrosineSulfate(363)));
        context.put(ContextKeys.RESIDUE_STATES, states(state("A:363", "TYS", "CTYS")));

        new ReceptorHydrogenationStage().run(context);

        Residue result = residues(context).getFirst();
        assertTrue(hasAtom(result, "HA"));
        assertTrue(hasAtom(result, "HB2"));
        assertTrue(hasAtom(result, "HB3"));
        assertTrue(hasAtom(result, "HD1"));
        assertTrue(hasAtom(result, "HD2"));
        assertTrue(hasAtom(result, "HE1"));
        assertTrue(hasAtom(result, "HE2"));
        assertTrue(hasAtom(result, "OXT"));
        assertFalse(hasAtom(result, "HH"),
                "TYS sulfate replaces phenolic OH protonation; do not add TYR HH");
    }

    @Test
    void q6ux53AddsExpectedBackboneAlphaHydrogensAtDefaultClashCutoff() throws Exception {
        PipelineContext context = new PipelineContext(tempDir, tempDir.resolve("run"));
        context.put(ContextKeys.TARGET_PDB_PATH, resourcePath("/Q6UX53/Q6UX53_TMT1B_HUMAN.pdb"));
        context.put(ContextKeys.PLDDT_CUTOFF, 50.0);

        new TargetLoadStage().run(context);
        new StructureCleanupStage().run(context);
        new AlphaFoldFilterStage().run(context);
        new ResidueStateAssignmentStage().run(context);
        new ReceptorHydrogenationStage().run(context);

        List<String> missingHa = residues(context).stream()
                .filter(this::isStandardNonGlycine)
                .filter(residue -> !hasAtom(residue, "HA"))
                .map(this::residueLabel)
                .toList();

        assertEquals(List.of(), missingHa,
                "Every standard non-glycine residue should keep its Amber alpha hydrogen");
    }

    @Test
    @SuppressWarnings("unchecked")
    void q6ux53AddsEveryAtomRequiredByAssignedAmberTemplatesAtDefaultClashCutoff() throws Exception {
        PipelineContext context = new PipelineContext(tempDir, tempDir.resolve("run"));
        context.put(ContextKeys.TARGET_PDB_PATH, resourcePath("/Q6UX53/Q6UX53_TMT1B_HUMAN.pdb"));
        context.put(ContextKeys.PLDDT_CUTOFF, 50.0);

        new TargetLoadStage().run(context);
        new StructureCleanupStage().run(context);
        new AlphaFoldFilterStage().run(context);
        new ResidueStateAssignmentStage().run(context);
        Map<String, ResidueState> states = context.require(ContextKeys.RESIDUE_STATES);
        new ReceptorHydrogenationStage().run(context);

        AmberResidueTemplateLibrary amber = AmberResidueTemplateLibrary.getInstance();
        List<String> missingAtoms = residues(context).stream()
                .flatMap(residue -> {
                    ResidueState state = states.get(residueKey(residue));
                    ResidueTemplate template = amber.getTemplate(state.amberTemplateName());
                    return template.getAtoms().stream()
                            .filter(atom -> !hasAtom(residue, atom.getName()))
                            .map(atom -> residueLabel(residue) + " missing " + atom.getName());
                })
                .toList();

        assertEquals(List.of(), missingAtoms,
                "Hydrogenation must populate the assigned Amber residue templates");
    }

    @Test
    void reportListsAreDefensiveCopies() throws Exception {
        PipelineContext context = contextWith(List.of(alanine(1)));
        context.put(ContextKeys.RESIDUE_STATES, states(state("A:1", "ALA", "NALA")));

        new ReceptorHydrogenationStage().run(context);

        HydrogenationReport report = context.require(ContextKeys.HYDROGENATION_REPORT);
        assertThrows(UnsupportedOperationException.class,
                () -> report.assignedTemplates().add("A:9 -> ALA"));
    }

    private PipelineContext contextWith(List<Residue> residues) {
        PipelineContext context = new PipelineContext(tempDir, tempDir.resolve("run"));
        context.put(ContextKeys.PROTEIN_RESIDUES, residues);
        context.put(ContextKeys.HYDROGEN_CLASH_CUTOFF, 0.05);
        return context;
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

    private Residue alanine(int number) {
        return residue("ALA", number,
                atom("N", "N", 0.0, 0.0, 0.0),
                atom("CA", "C", 1.5, 0.0, 0.0),
                atom("C", "C", 2.0, 1.4, 0.0),
                atom("O", "O", 2.9, 1.8, 0.0),
                atom("CB", "C", 1.3, -0.8, 1.2));
    }

    private Residue glycine(int number) {
        return residue("GLY", number,
                atom("N", "N", 3.0, 1.8, 0.0),
                atom("CA", "C", 4.5, 1.8, 0.0),
                atom("C", "C", 5.0, 3.2, 0.0),
                atom("O", "O", 5.9, 3.6, 0.0));
    }

    private Residue aspartate(int number) {
        return residue("ASP", number,
                atom("N", "N", 0.0, 0.0, 0.0),
                atom("CA", "C", 1.5, 0.0, 0.0),
                atom("C", "C", 2.0, 1.4, 0.0),
                atom("O", "O", 2.9, 1.8, 0.0),
                atom("CB", "C", 1.3, -0.8, 1.2),
                atom("CG", "C", 2.4, -1.4, 1.8),
                atom("OD1", "O", 3.4, -1.0, 1.8),
                atom("OD2", "O", 2.2, -2.5, 2.2));
    }

    private Residue glutamate(int number) {
        return residue("GLU", number,
                atom("N", "N", 0.0, 0.0, 0.0),
                atom("CA", "C", 1.5, 0.0, 0.0),
                atom("C", "C", 2.0, 1.4, 0.0),
                atom("O", "O", 2.9, 1.8, 0.0),
                atom("CB", "C", 1.3, -0.8, 1.2),
                atom("CG", "C", 2.3, -1.5, 1.9),
                atom("CD", "C", 3.4, -2.1, 2.5),
                atom("OE1", "O", 4.4, -1.6, 2.5),
                atom("OE2", "O", 3.2, -3.2, 2.9));
    }

    private Residue lysine(int number) {
        return residue("LYS", number,
                atom("N", "N", 0.0, 0.0, 0.0),
                atom("CA", "C", 1.5, 0.0, 0.0),
                atom("C", "C", 2.0, 1.4, 0.0),
                atom("O", "O", 2.9, 1.8, 0.0),
                atom("CB", "C", 1.3, -0.8, 1.2),
                atom("CG", "C", 2.3, -1.5, 1.9),
                atom("CD", "C", 3.3, -2.2, 2.6),
                atom("CE", "C", 4.3, -2.9, 3.3),
                atom("NZ", "N", 5.3, -3.6, 4.0));
    }

    private Residue histidine(int number) {
        return residue("HIS", number,
                atom("N", "N", 0.0, 0.0, 0.0),
                atom("CA", "C", 1.5, 0.0, 0.0),
                atom("C", "C", 2.0, 1.4, 0.0),
                atom("O", "O", 2.9, 1.8, 0.0),
                atom("CB", "C", 1.3, -0.8, 1.2),
                atom("CG", "C", 2.3, -1.5, 1.9),
                atom("ND1", "N", 3.2, -1.0, 2.2),
                atom("CD2", "C", 2.5, -2.7, 2.4),
                atom("CE1", "C", 4.0, -1.8, 2.8),
                atom("NE2", "N", 3.6, -3.0, 2.9));
    }

    private Residue cysteine(int number) {
        double offset = number * 5.0;
        return residue("CYS", number,
                atom("N", "N", offset, 0.0, 0.0),
                atom("CA", "C", offset + 1.5, 0.0, 0.0),
                atom("C", "C", offset + 2.0, 1.4, 0.0),
                atom("O", "O", offset + 2.9, 1.8, 0.0),
                atom("CB", "C", offset + 1.3, -0.8, 1.2),
                atom("SG", "S", offset + 2.2, -1.5, 2.4));
    }

    private Residue tyrosineSulfate(int number) {
        return residue("TYS", number,
                atom("N", "N", 15.901, 2.361, -4.748),
                atom("CA", "C", 16.482, 2.005, -3.448),
                atom("CB", "C", 15.434, 1.805, -2.340),
                atom("CG", "C", 14.580, 3.049, -2.234),
                atom("CD1", "C", 15.156, 4.304, -2.015),
                atom("CD2", "C", 13.219, 2.924, -2.545),
                atom("CE1", "C", 14.343, 5.432, -2.005),
                atom("CE2", "C", 12.411, 4.063, -2.563),
                atom("CZ", "C", 12.986, 5.299, -2.287),
                atom("OH", "O", 12.107, 6.361, -2.265),
                atom("S", "S", 11.153, 6.571, -0.919),
                atom("O1", "O", 10.528, 5.299, -0.666),
                atom("O2", "O", 12.111, 7.170, -0.043),
                atom("O3", "O", 10.170, 7.609, -1.318),
                atom("C", "C", 17.330, 0.734, -3.630),
                atom("O", "O", 18.126, 0.587, -2.682));
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
                .element(Element.fromSymbol(element))
                .build();
    }

    private List<Residue> residues(PipelineContext context) {
        return context.require(ContextKeys.PROTEIN_RESIDUES);
    }

    private int hydrogenCount(List<Residue> residues) {
        return residues.stream()
                .mapToInt(residue -> (int) residue.getAtoms().stream()
                        .filter(atom -> "H".equals(atom.getElement().getSymbol()))
                        .count())
                .sum();
    }

    private boolean hasAtom(Residue residue, String name) {
        return residue.getAtoms().stream().anyMatch(atom -> name.equals(atom.getName()));
    }

    private boolean isStandardNonGlycine(Residue residue) {
        return Set.of("ALA", "ARG", "ASN", "ASP", "CYS", "GLN", "GLU", "HIS",
                "ILE", "LEU", "LYS", "MET", "PHE", "PRO", "SER", "THR",
                "TRP", "TYR", "VAL").contains(residue.getName());
    }

    private String residueLabel(Residue residue) {
        return residue.getName() + " " + residue.getChain() + ":" + residue.getNumber();
    }

    private String residueKey(Residue residue) {
        return residue.getChain() + ":" + residue.getNumber();
    }

    private Path resourcePath(String resourceName) throws Exception {
        return Path.of(getClass().getResource(resourceName).toURI());
    }

    private int atomNameCount(Residue residue, String prefix) {
        return (int) residue.getAtoms().stream()
                .filter(atom -> atom.getName().startsWith(prefix))
                .count();
    }
}
