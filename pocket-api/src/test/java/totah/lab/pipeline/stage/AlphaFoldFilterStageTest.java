package totah.lab.pipeline.stage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import totah.lab.io.StructureIO;
import totah.lab.pipeline.ContextKeys;
import totah.lab.pipeline.PipelineContext;
import totah.lab.protein.Atom;
import totah.lab.protein.Element;
import totah.lab.protein.Point3D;
import totah.lab.protein.Residue;
import totah.lab.protein.Structure;

import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AlphaFoldFilterStageTest {

    @TempDir
    Path tempDir;

    @Test
    void noOpsWhenCutoffIsNotConfigured() throws Exception {
        List<Residue> residues = List.of(residue("CYS", 1,
                atom("N", "N", 20.0),
                atom("CA", "C", 20.0)));
        PipelineContext context = contextWith(residues);

        new AlphaFoldFilterStage().run(context);

        assertSame(residues, context.require(ContextKeys.PROTEIN_RESIDUES));
        assertFalse(context.containsKey(ContextKeys.ALPHAFOLD_CONFIDENCE_REPORT));
    }

    @Test
    void keepsWholeResidueWhenAnyBackboneAtomMeetsCutoff() throws Exception {
        Residue residue = residue("LYS", 33,
                atom("N", "N", 30.0),
                atom("CA", "C", 95.0),
                atom("C", "C", 30.0),
                atom("CB", "C", 10.0),
                atom("NZ", "N", 10.0));
        PipelineContext context = contextWith(List.of(residue));
        context.put(ContextKeys.PLDDT_CUTOFF, 90.0);

        new AlphaFoldFilterStage().run(context);

        List<Residue> filtered = context.require(ContextKeys.PROTEIN_RESIDUES);
        assertEquals(1, filtered.size());
        assertEquals(List.of("N", "CA", "C", "CB", "NZ"), atomNames(filtered.getFirst()));
        AlphaFoldConfidenceReport report = context.require(ContextKeys.ALPHAFOLD_CONFIDENCE_REPORT);
        assertEquals(90.0, report.cutoff());
        assertEquals(1, report.inputResidues());
        assertEquals(1, report.outputResidues());
        assertTrue(report.droppedResidues().isEmpty());
    }

    @Test
    void dropsWholeResidueWhenNoBackboneAtomMeetsCutoff() throws Exception {
        Residue kept = residue("CYS", 32,
                atom("N", "N", 91.0),
                atom("CA", "C", 20.0),
                atom("SG", "S", 20.0));
        Residue dropped = residue("LYS", 33,
                atom("N", "N", 20.0),
                atom("CA", "C", 20.0),
                atom("C", "C", 20.0),
                atom("NZ", "N", 99.0));
        PipelineContext context = contextWith(List.of(kept, dropped));
        context.put(ContextKeys.PLDDT_CUTOFF, 90.0);

        new AlphaFoldFilterStage().run(context);

        List<Residue> filtered = context.require(ContextKeys.PROTEIN_RESIDUES);
        assertEquals(List.of("CYS"), residueNames(filtered));
        AlphaFoldConfidenceReport report = context.require(ContextKeys.ALPHAFOLD_CONFIDENCE_REPORT);
        assertEquals(List.of("LYS A:33"), report.droppedResidues());
    }

    @Test
    void acceptsStringCutoff() throws Exception {
        PipelineContext context = contextWith(List.of(residue("CYS", 32,
                atom("N", "N", 75.0))));
        context.put(ContextKeys.PLDDT_CUTOFF, "70.0");

        new AlphaFoldFilterStage().run(context);

        AlphaFoldConfidenceReport report = context.require(ContextKeys.ALPHAFOLD_CONFIDENCE_REPORT);
        assertEquals(70.0, report.cutoff());
    }

    @Test
    void rejectsNonNumericCutoff() {
        PipelineContext context = contextWith(List.of(residue("CYS", 32,
                atom("N", "N", 75.0))));
        context.put(ContextKeys.PLDDT_CUTOFF, "high");

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> new AlphaFoldFilterStage().run(context));

        assertTrue(error.getMessage().contains("must be numeric"));
    }

    @Test
    void rejectsOutOfRangeCutoff() {
        PipelineContext context = contextWith(List.of(residue("CYS", 32,
                atom("N", "N", 75.0))));
        context.put(ContextKeys.PLDDT_CUTOFF, 101.0);

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> new AlphaFoldFilterStage().run(context));

        assertTrue(error.getMessage().contains("0-100"));
    }

    @Test
    void rejectsMissingResidues() {
        PipelineContext context = new PipelineContext(tempDir, tempDir.resolve("run"));
        context.put(ContextKeys.PLDDT_CUTOFF, 70.0);

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> new AlphaFoldFilterStage().run(context));

        assertTrue(error.getMessage().contains(ContextKeys.PROTEIN_RESIDUES));
    }

    @Test
    void rejectsEmptyResidueList() {
        PipelineContext context = contextWith(List.of());
        context.put(ContextKeys.PLDDT_CUTOFF, 70.0);

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> new AlphaFoldFilterStage().run(context));

        assertTrue(error.getMessage().contains("Run StructureCleanupStage first"));
    }

    @Test
    void rejectsWhenFilteringRemovesEverything() {
        PipelineContext context = contextWith(List.of(residue("CYS", 32,
                atom("N", "N", 20.0),
                atom("CA", "C", 20.0),
                atom("C", "C", 20.0))));
        context.put(ContextKeys.PLDDT_CUTOFF, 90.0);

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> new AlphaFoldFilterStage().run(context));

        assertTrue(error.getMessage().contains("removed every residue"));
    }

    @Test
    void reportDroppedResiduesIsDefensiveCopy() throws Exception {
        PipelineContext context = contextWith(List.of(
                residue("CYS", 32, atom("N", "N", 90.0)),
                residue("LYS", 33, atom("N", "N", 10.0))));
        context.put(ContextKeys.PLDDT_CUTOFF, 70.0);

        new AlphaFoldFilterStage().run(context);

        AlphaFoldConfidenceReport report = context.require(ContextKeys.ALPHAFOLD_CONFIDENCE_REPORT);
        assertThrows(UnsupportedOperationException.class, () -> report.droppedResidues().add("ALA A:99"));
    }

    @Test
    void fixtureCountsRemainStableWithoutAtomTrimming() throws Exception {
        Structure structure = StructureIO.load(resourcePath("/Q6UX53/Q6UX53_TMT1B_HUMAN.pdb"));
        List<Residue> beforeList = structure.getResidues();
        int residue33Atoms = beforeList.get(32).getAtomCount();

        PipelineContext context = contextWith(beforeList);
        context.put(ContextKeys.PLDDT_CUTOFF, 70.0);

        new AlphaFoldFilterStage().run(context);

        List<Residue> filtered = context.require(ContextKeys.PROTEIN_RESIDUES);
        assertEquals(240, filtered.size());
        assertEquals(residue33Atoms, filtered.stream()
                .filter(r -> r.getNumber() == 33)
                .findFirst()
                .orElseThrow()
                .getAtomCount());
    }

    private PipelineContext contextWith(List<Residue> residues) {
        PipelineContext context = new PipelineContext(tempDir, tempDir.resolve("run"));
        context.put(ContextKeys.PROTEIN_RESIDUES, residues);
        return context;
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

    private Atom atom(String name, String element, double bFactor) {
        return Atom.builder()
                .name(name)
                .position(new Point3D(0.0, 0.0, 0.0))
                .occupancy(1.0)
                .bFactor(bFactor)
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

    private Path resourcePath(String resourceName) throws URISyntaxException {
        return Path.of(getClass().getResource(resourceName).toURI());
    }

    private List<String> atomNames(Residue residue) {
        return residue.getAtoms().stream()
                .map(Atom::getName)
                .toList();
    }

    private List<String> residueNames(List<Residue> residues) {
        return residues.stream()
                .map(Residue::getName)
                .toList();
    }
}
