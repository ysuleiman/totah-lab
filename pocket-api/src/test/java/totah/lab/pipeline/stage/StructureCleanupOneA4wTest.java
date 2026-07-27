package totah.lab.pipeline.stage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;
import org.biojava.nbio.structure.chem.DownloadChemCompProvider;
import totah.lab.io.StructureIO;
import totah.lab.ligand.LigandPreparationOrchestrator;
import totah.lab.ligand.LigandPreparer;
import totah.lab.ligand.SelectedLigandPreparation;
import totah.lab.pipeline.ContextKeys;
import totah.lab.pipeline.PipelineContext;
import totah.lab.pipeline.cleanup.StructureCleanupResult;
import totah.lab.pipeline.report.StructureCleanupReport;
import totah.lab.protein.Residue;
import totah.lab.protein.ResidueClassificationEvidence;

import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StructureCleanupOneA4wTest {

    @TempDir
    Path tempDir;

    @Test
    void reducedCcdIdentifiesTysButFallsBackForQwe() throws Exception {
        List<Residue> loaded = StructureIO.load(resourcePath("/pipeline/1A4W.pdb")).getResidues();
        Residue tys = requireResidue(loaded, "TYS", "I", 363);
        Residue qwe = requireResidue(loaded, "QWE", "H", 373);

        ResidueClassificationEvidence tysEvidence = tys.getResidueClassificationEvidence();
        assertNotNull(tysEvidence);
        assertTrue(tysEvidence.available());
        assertFalse(tysEvidence.standard());
        assertTrue(tysEvidence.polymeric());
        assertFalse(tysEvidence.water());
        assertEquals("TYR", tysEvidence.parentComponentId());
        assertEquals("lPeptideLinking", tysEvidence.residueType());
        assertEquals("peptide", tysEvidence.polymerType());

        ResidueClassificationEvidence qweEvidence = qwe.getResidueClassificationEvidence();
        assertNotNull(qweEvidence);
        assertFalse(qweEvidence.available());
        assertFalse(qweEvidence.polymeric());
        assertFalse(qweEvidence.water());
    }

    @Test
    void cleanupKeepsTysAndExtractsQweWithoutReorderingReceptor() throws Exception {
        List<Residue> loaded = StructureIO.load(resourcePath("/pipeline/1A4W.pdb")).getResidues();
        Residue tys = requireResidue(loaded, "TYS", "I", 363);
        Residue qwe = requireResidue(loaded, "QWE", "H", 373);
        List<String> qweAtomOrder = atomNames(qwe);

        PipelineContext context = new PipelineContext(tempDir, tempDir.resolve("run"));
        context.put(ContextKeys.PROTEIN_RESIDUES, loaded);

        new StructureCleanupStage().run(context);

        List<Residue> receptor = context.require(ContextKeys.PROTEIN_RESIDUES);
        List<Residue> extractedLigands = context.require(ContextKeys.EXTRACTED_LIGANDS);
        StructureCleanupReport report = context.require(ContextKeys.STRUCTURE_CLEANUP_REPORT);

        assertSame(tys, requireResidue(receptor, "TYS", "I", 363));
        assertFalse(receptor.contains(qwe));
        assertEquals(1, extractedLigands.size());
        assertSame(qwe, extractedLigands.getFirst());
        assertEquals(qweAtomOrder, atomNames(extractedLigands.getFirst()));
        assertTrue(report.keptSpecialResidues().contains("TYS I:363"));
        assertPreservesInputOrder(loaded, receptor);
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "RUN_ONLINE_CCD_TESTS", matches = "true")
    void onlineLookupEnrichesAndCachesQweWithoutChangingCleanupDisposition() throws Exception {
        Path cacheDirectory = tempDir.resolve("ccd-cache");
        PipelineContext context = new PipelineContext(tempDir, tempDir.resolve("run"))
                .with(ContextKeys.TARGET_PDB_PATH, resourcePath("/pipeline/1A4W.pdb"))
                .with(ContextKeys.CCD_ONLINE_LOOKUP, true)
                .with(ContextKeys.CCD_CACHE_DIRECTORY, cacheDirectory);

        new TargetLoadStage().run(context);

        List<Residue> loaded = context.require(ContextKeys.PROTEIN_RESIDUES);
        Residue qwe = requireResidue(loaded, "QWE", "H", 373);
        ResidueClassificationEvidence evidence = qwe.getResidueClassificationEvidence();
        assertNotNull(evidence);
        assertTrue(evidence.available());
        assertFalse(evidence.polymeric());
        assertEquals("peptideLike", evidence.residueType());
        assertEquals("otherPolymer", evidence.polymerType());
        assertTrue(Files.isRegularFile(cacheDirectory.resolve("chemcomp/QWE.cif.gz")));

        new StructureCleanupStage().run(context);

        List<Residue> extractedLigands = context.require(ContextKeys.EXTRACTED_LIGANDS);
        assertEquals(1, extractedLigands.size());
        assertSame(qwe, extractedLigands.getFirst());

        StructureCleanupResult cleanupResult =
                context.require(ContextKeys.STRUCTURE_CLEANUP_RESULT);
        DownloadChemCompProvider provider =
                new DownloadChemCompProvider(cacheDirectory.toString());
        SelectedLigandPreparation prepared =
                new LigandPreparationOrchestrator(
                        new LigandPreparer(provider))
                        .prepareOnly(cleanupResult)
                        .orElseThrow();
        assertSame(qwe, prepared.selectedLigand().residue());
        assertTrue(prepared.preparation().pdbqt().startsWith("ROOT"));
        assertTrue(prepared.preparation().pdbqt().contains("TORSDOF "));
    }

    private Residue requireResidue(
            List<Residue> residues,
            String name,
            String chain,
            int number) {
        return residues.stream()
                .filter(residue -> name.equals(residue.getName()))
                .filter(residue -> chain.equals(residue.getChain()))
                .filter(residue -> number == residue.getNumber())
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "Missing residue " + name + " " + chain + ":" + number));
    }

    private void assertPreservesInputOrder(List<Residue> input, List<Residue> output) {
        int previousInputIndex = -1;
        for (Residue residue : output) {
            int inputIndex = indexOfIdentity(input, residue);
            assertTrue(inputIndex > previousInputIndex,
                    () -> "Receptor residue order changed at " + residue);
            previousInputIndex = inputIndex;
        }
    }

    private int indexOfIdentity(List<Residue> residues, Residue target) {
        for (int index = 0; index < residues.size(); index++) {
            if (residues.get(index) == target) {
                return index;
            }
        }
        return -1;
    }

    private List<String> atomNames(Residue residue) {
        return residue.getAtoms().stream()
                .map(atom -> atom.getName())
                .toList();
    }

    private Path resourcePath(String resourceName) throws Exception {
        URL resource = getClass().getResource(resourceName);
        assertNotNull(resource, resourceName + " resource missing");
        return Path.of(resource.toURI());
    }
}
