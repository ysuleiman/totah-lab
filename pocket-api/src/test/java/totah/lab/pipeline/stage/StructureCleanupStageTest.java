package totah.lab.pipeline.stage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import totah.lab.pipeline.ContextKeys;
import totah.lab.pipeline.PipelineContext;
import totah.lab.protein.Atom;
import totah.lab.protein.Element;
import totah.lab.protein.Point3D;
import totah.lab.protein.Residue;
import totah.lab.protein.ResidueClassificationEvidence;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StructureCleanupStageTest {

    @TempDir
    Path tempDir;

    @Test
    void keepsStandardAminoAcidsInInputOrder() {
        PipelineContext context = contextWith(residue("CYS", 32, atom("CA", "C")),
                residue("LYS", 33, atom("CA", "C")));

        new StructureCleanupStage().run(context);

        List<Residue> residues = context.require(ContextKeys.PROTEIN_RESIDUES);
        assertEquals(List.of("CYS", "LYS"), residueNames(residues));
        StructureCleanupReport report = context.require(ContextKeys.STRUCTURE_CLEANUP_REPORT);
        assertEquals(2, report.inputResidues());
        assertEquals(2, report.outputResidues());
        assertTrue(report.removedWaters().isEmpty());
        assertTrue(report.removedMetals().isEmpty());
        assertTrue(report.keptSpecialResidues().isEmpty());
    }

    @Test
    void removesWatersByDefault() {
        PipelineContext context = contextWith(residue("CYS", 32, atom("CA", "C")),
                residue("HOH", 501, atom("O", "O")),
                residue("WAT", 502, atom("O", "O")));

        new StructureCleanupStage().run(context);

        List<Residue> residues = context.require(ContextKeys.PROTEIN_RESIDUES);
        assertEquals(List.of("CYS"), residueNames(residues));
        StructureCleanupReport report = context.require(ContextKeys.STRUCTURE_CLEANUP_REPORT);
        assertEquals(List.of("HOH A:501", "WAT A:502"), report.removedWaters());
    }

    @Test
    void rejectsWaterRetentionBecauseWaterDockingPolicyIsNotImplemented() {
        PipelineContext context = contextWith(residue("CYS", 32, atom("CA", "C")),
                residue("HOH", 501, atom("O", "O")));
        context.put(ContextKeys.REMOVE_WATERS, false);

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> new StructureCleanupStage().run(context));

        assertTrue(error.getMessage().contains("water retention is not supported"));
    }

    @Test
    void keepsMseAsKnownSpecialResidueForLaterNormalization() {
        PipelineContext context = contextWith(residue("CYS", 32, atom("CA", "C")),
                residue("MSE", 40, atom("SE", "Se")));

        new StructureCleanupStage().run(context);

        List<Residue> residues = context.require(ContextKeys.PROTEIN_RESIDUES);
        assertEquals(List.of("CYS", "MSE"), residueNames(residues));
        StructureCleanupReport report = context.require(ContextKeys.STRUCTURE_CLEANUP_REPORT);
        assertEquals(List.of("MSE A:40"), report.keptSpecialResidues());
    }

    @Test
    void keepsTysAsKnownSpecialResidueForExplicitAmberTemplateSupport() {
        PipelineContext context = contextWith(residue("CYS", 32, atom("CA", "C")),
                residue("TYS", 40, atom("S", "S")));

        new StructureCleanupStage().run(context);

        List<Residue> residues = context.require(ContextKeys.PROTEIN_RESIDUES);
        assertEquals(List.of("CYS", "TYS"), residueNames(residues));
        StructureCleanupReport report = context.require(ContextKeys.STRUCTURE_CLEANUP_REPORT);
        assertEquals(List.of("TYS A:40"), report.keptSpecialResidues());
    }

    @Test
    void removesMonoatomicMetalsByDefault() {
        PipelineContext context = contextWith(residue("CYS", 32, atom("CA", "C")),
                residue("ZN", 701, atom("ZN", "Zn")));

        new StructureCleanupStage().run(context);

        List<Residue> residues = context.require(ContextKeys.PROTEIN_RESIDUES);
        assertEquals(List.of("CYS"), residueNames(residues));
        StructureCleanupReport report = context.require(ContextKeys.STRUCTURE_CLEANUP_REPORT);
        assertEquals(List.of("ZN A:701"), report.removedMetals());
        assertTrue(report.keptSpecialResidues().isEmpty());
    }

    @Test
    void removesKnownMonoatomicIonsByDefault() {
        PipelineContext context = contextWith(residue("CYS", 32, atom("CA", "C")),
                residue("CL", 702, atom("CL", "Cl")));

        new StructureCleanupStage().run(context);

        List<Residue> residues = context.require(ContextKeys.PROTEIN_RESIDUES);
        assertEquals(List.of("CYS"), residueNames(residues));
        StructureCleanupReport report = context.require(ContextKeys.STRUCTURE_CLEANUP_REPORT);
        assertEquals(List.of("CL A:702"), report.removedMetals());
    }

    @Test
    void keepsMonoatomicMetalsWhenConfigured() {
        PipelineContext context = contextWith(residue("CYS", 32, atom("CA", "C")),
                residue("ZN", 701, atom("ZN", "Zn")));
        context.put(ContextKeys.KEEP_METALS, true);

        new StructureCleanupStage().run(context);

        List<Residue> residues = context.require(ContextKeys.PROTEIN_RESIDUES);
        assertEquals(List.of("CYS", "ZN"), residueNames(residues));
        StructureCleanupReport report = context.require(ContextKeys.STRUCTURE_CLEANUP_REPORT);
        assertTrue(report.removedMetals().isEmpty());
        assertEquals(List.of("ZN A:701"), report.keptSpecialResidues());
    }

    @Test
    void keepsKnownMonoatomicIonsWhenConfigured() {
        PipelineContext context = contextWith(residue("CYS", 32, atom("CA", "C")),
                residue("CL", 702, atom("CL", "Cl")));
        context.put(ContextKeys.KEEP_METALS, true);

        new StructureCleanupStage().run(context);

        List<Residue> residues = context.require(ContextKeys.PROTEIN_RESIDUES);
        assertEquals(List.of("CYS", "CL"), residueNames(residues));
        StructureCleanupReport report = context.require(ContextKeys.STRUCTURE_CLEANUP_REPORT);
        assertTrue(report.removedMetals().isEmpty());
        assertEquals(List.of("CL A:702"), report.keptSpecialResidues());
    }

    @Test
    void keepsConfiguredSpecialResidueFromList() {
        PipelineContext context = contextWith(residue("CYS", 32, atom("CA", "C")),
                residue("SAM", 801, atom("C1", "C")));
        context.put(ContextKeys.ALLOWED_SPECIAL_RESIDUES, List.of("SAM"));

        new StructureCleanupStage().run(context);

        List<Residue> residues = context.require(ContextKeys.PROTEIN_RESIDUES);
        assertEquals(List.of("CYS", "SAM"), residueNames(residues));
        StructureCleanupReport report = context.require(ContextKeys.STRUCTURE_CLEANUP_REPORT);
        assertEquals(List.of("SAM A:801"), report.keptSpecialResidues());
    }

    @Test
    void keepsConfiguredSpecialResidueFromCommaSeparatedString() {
        PipelineContext context = contextWith(residue("CYS", 32, atom("CA", "C")),
                residue("NAG", 802, atom("C1", "C")));
        context.put(ContextKeys.ALLOWED_SPECIAL_RESIDUES, "SAM, NAG");

        new StructureCleanupStage().run(context);

        List<Residue> residues = context.require(ContextKeys.PROTEIN_RESIDUES);
        assertEquals(List.of("CYS", "NAG"), residueNames(residues));
    }

    @Test
    void extractsUnknownMultiAtomResidueAsLigandByDefault() {
        PipelineContext context = contextWith(residue("CYS", 32, atom("CA", "C")),
                residue("QWE", 373, atom("C1", "C"), atom("N1", "N")));

        new StructureCleanupStage().run(context);

        List<Residue> residues = context.require(ContextKeys.PROTEIN_RESIDUES);
        List<Residue> ligands = context.require(ContextKeys.EXTRACTED_LIGANDS);
        assertEquals(List.of("CYS"), residueNames(residues));
        assertEquals(List.of("QWE"), residueNames(ligands));
    }

    @Test
    void keepsSupportedModifiedAminoAcidUsingCcdEvidence() {
        Residue tys = residue("TYS", 40, atom("S", "S")).toBuilder()
                .residueClassificationEvidence(evidence(
                        true, false, true, false, "TYR", "lPeptideLinking", "peptide"))
                .build();
        PipelineContext context = contextWith(residue("CYS", 32, atom("CA", "C")), tys);

        new StructureCleanupStage().run(context);

        List<Residue> residues = context.require(ContextKeys.PROTEIN_RESIDUES);
        assertEquals(List.of("CYS", "TYS"), residueNames(residues));
    }

    @Test
    void extractsExplicitCcdNonPolymerAsLigand() {
        Residue ligand = residue("QWE", 373, atom("C1", "C"), atom("N1", "N")).toBuilder()
                .residueClassificationEvidence(evidence(
                        true, false, false, false, null, "nonPolymer", null))
                .build();
        PipelineContext context = contextWith(residue("CYS", 32, atom("CA", "C")), ligand);

        new StructureCleanupStage().run(context);

        List<Residue> ligands = context.require(ContextKeys.EXTRACTED_LIGANDS);
        assertEquals(List.of("QWE"), residueNames(ligands));
    }

    @Test
    void extractsNonPolymericCcdPeptideLikeComponentAsLigand() {
        Residue ligand = residue("QWE", 373, atom("C1", "C"), atom("N1", "N")).toBuilder()
                .residueClassificationEvidence(evidence(
                        true, false, false, false, null, "peptideLike", "otherPolymer"))
                .build();
        PipelineContext context = contextWith(residue("CYS", 32, atom("CA", "C")), ligand);

        new StructureCleanupStage().run(context);

        List<Residue> ligands = context.require(ContextKeys.EXTRACTED_LIGANDS);
        assertEquals(List.of("QWE"), residueNames(ligands));
    }

    @Test
    void fallsBackToLegacyRulesWhenCcdEvidenceIsUnavailable() {
        Residue cys = residue("CYS", 32, atom("CA", "C")).toBuilder()
                .residueClassificationEvidence(evidence(
                        false, false, false, false, null, null, null))
                .build();
        PipelineContext context = contextWith(cys);

        new StructureCleanupStage().run(context);

        List<Residue> residues = context.require(ContextKeys.PROTEIN_RESIDUES);
        assertEquals(List.of("CYS"), residueNames(residues));
    }

    @Test
    void rejectsWhenCleanupRemovesEverything() {
        PipelineContext context = contextWith(residue("HOH", 501, atom("O", "O")),
                residue("QWE", 373, atom("C1", "C"), atom("N1", "N")));

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> new StructureCleanupStage().run(context));

        assertTrue(error.getMessage().contains("removed every residue"));
    }

    @Test
    void requiresLoadedResidues() {
        PipelineContext context = new PipelineContext(tempDir, tempDir.resolve("run"));

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> new StructureCleanupStage().run(context));

        assertTrue(error.getMessage().contains(ContextKeys.PROTEIN_RESIDUES));
    }

    @Test
    void rejectsEmptyLoadedResidueList() {
        PipelineContext context = new PipelineContext(tempDir, tempDir.resolve("run"));
        context.put(ContextKeys.PROTEIN_RESIDUES, List.of());

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> new StructureCleanupStage().run(context));

        assertTrue(error.getMessage().contains("Run TargetLoadStage first"));
    }

    @Test
    void reportListsAreDefensiveCopies() {
        PipelineContext context = contextWith(residue("CYS", 32, atom("CA", "C")),
                residue("HOH", 501, atom("O", "O")));

        new StructureCleanupStage().run(context);

        StructureCleanupReport report = context.require(ContextKeys.STRUCTURE_CLEANUP_REPORT);
        assertThrows(UnsupportedOperationException.class, () -> report.removedWaters().add("HOH A:999"));
        assertFalse(report.removedWaters().isEmpty());
    }

    @Test
    void extractedLigandListIsImmutable() {
        PipelineContext context = contextWith(residue("CYS", 32, atom("CA", "C")),
                residue("QWE", 373, atom("C1", "C"), atom("N1", "N")));

        new StructureCleanupStage().run(context);

        List<Residue> ligands = context.require(ContextKeys.EXTRACTED_LIGANDS);
        assertThrows(UnsupportedOperationException.class,
                () -> ligands.add(residue("BAD", 999, atom("C1", "C"))));
    }

    private PipelineContext contextWith(Residue... residues) {
        PipelineContext context = new PipelineContext(tempDir, tempDir.resolve("run"));
        context.put(ContextKeys.PROTEIN_RESIDUES, List.of(residues));
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

    private Atom atom(String name, String element) {
        return Atom.builder()
                .name(name)
                .position(new Point3D(0.0, 0.0, 0.0))
                .occupancy(1.0)
                .bFactor(20.0)
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

    private ResidueClassificationEvidence evidence(
            boolean available,
            boolean standard,
            boolean polymeric,
            boolean water,
            String parentComponentId,
            String residueType,
            String polymerType) {
        return new ResidueClassificationEvidence(
                available,
                standard,
                polymeric,
                water,
                parentComponentId,
                residueType,
                polymerType);
    }

    private List<String> residueNames(List<Residue> residues) {
        return residues.stream()
                .map(Residue::getName)
                .toList();
    }
}
