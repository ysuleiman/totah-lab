package totah.lab.daedalus.stage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import totah.lab.gaia.molecule.Ligand;
import totah.lab.gaia.molecule.Protein;
import totah.lab.gaia.structure.Chain;
import totah.lab.gaia.structure.Residue;
import totah.lab.gaia.structure.Structure;
import totah.lab.hephaestus.client.HephaestusClient;
import totah.lab.hephaestus.ligand.LigandPreparationOptions;
import totah.lab.hephaestus.ligand.LigandPreparationResult;
import totah.lab.hephaestus.model.PreparationIssue;
import totah.lab.hephaestus.model.PreparedLigand;
import totah.lab.hephaestus.model.PreparedProtein;
import totah.lab.hephaestus.model.Severity;
import totah.lab.hephaestus.receptor.ReceptorPreparationOptions;
import totah.lab.hephaestus.receptor.ReceptorPreparationResult;
import totah.lab.hephaestus.validation.ValidationReport;
import totah.lab.hermes.file.writer.pdbqt.PdbqtWriteResult;
import totah.lab.hermes.file.writer.pdbqt.validation.PdbqtValidationReport;
import totah.lab.daedalus.ContextKeys;
import totah.lab.daedalus.PipelineContext;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LigandPreparationStageTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void preparesLigandAndPublishesPdbqtPath() throws Exception {
        StubClient client = new StubClient();
        PipelineContext context = new PipelineContext(
                temporaryDirectory, temporaryDirectory)
                .with(ContextKeys.LIGAND_PATH,
                        temporaryDirectory.resolve("ligand.sdf"));

        new LigandPreparationStage(client).run(context);

        assertEquals(temporaryDirectory.resolve("ligand.sdf"),
                client.receivedSdf);
        assertSame(client.ligandResult,
                context.require(ContextKeys.LIGAND_PREPARATION_RESULT));
        assertEquals(temporaryDirectory.resolve("ligand.pdbqt")
                        .toAbsolutePath().normalize().toString(),
                context.require(ContextKeys.LIGAND_PDBQT_PATH));
        assertTrue(Files.isRegularFile(
                temporaryDirectory.resolve("ligand.pdbqt")));
    }

    @Test
    void preparationErrorsFailTheStage() {
        StubClient client = new StubClient();
        client.ligandResult = new LigandPreparationResult(
                client.ligandResult.preparedLigand(),
                List.of(new PreparationIssue(
                        Severity.ERROR, "BROKEN", "broken ligand")));
        PipelineContext context = new PipelineContext(
                temporaryDirectory, temporaryDirectory)
                .with(ContextKeys.LIGAND_PATH,
                        temporaryDirectory.resolve("ligand.sdf"));

        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                () -> new LigandPreparationStage(client).run(context));
        assertTrue(failure.getMessage().contains("BROKEN"));
    }

    @Test
    void missingLigandPathFailsClearly() {
        PipelineContext context = new PipelineContext(
                temporaryDirectory, temporaryDirectory);

        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                () -> new LigandPreparationStage(new StubClient())
                        .run(context));
        assertTrue(failure.getMessage()
                .contains(ContextKeys.LIGAND_PATH));
    }

    @Test
    void outputOverrideIsHonored() throws Exception {
        StubClient client = new StubClient();
        Path custom = temporaryDirectory.resolve("custom-ligand.pdbqt");
        PipelineContext context = new PipelineContext(
                temporaryDirectory, temporaryDirectory)
                .with(ContextKeys.LIGAND_PATH,
                        temporaryDirectory.resolve("ligand.sdf"))
                .with(ContextKeys.LIGAND_PDBQT_PATH, custom);

        new LigandPreparationStage(client).run(context);

        assertEquals(custom.toAbsolutePath().normalize().toString(),
                context.require(ContextKeys.LIGAND_PDBQT_PATH));
    }

    private static Ligand ligand() {
        return new Ligand("L", "ligand", null, null, null, null,
                new Structure(List.of(new Chain("L", List.of(
                        new Residue("LIG", 1, List.of()))))));
    }

    private static final class StubClient implements HephaestusClient {
        private LigandPreparationResult ligandResult =
                new LigandPreparationResult(
                        PreparedLigand.of(ligand()), List.of());
        private Path receivedSdf;

        @Override
        public ReceptorPreparationResult prepareReceptor(
                Protein protein, ReceptorPreparationOptions options) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ReceptorPreparationResult prepareReceptor(
                Path input, ReceptorPreparationOptions options) {
            throw new UnsupportedOperationException();
        }

        @Override
        public PdbqtWriteResult prepareAndWriteReceptor(
                Path input, Path output,
                ReceptorPreparationOptions options) {
            throw new UnsupportedOperationException();
        }

        @Override
        public PdbqtWriteResult writePreparedReceptor(
                PreparedProtein preparedProtein, Path output) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ValidationReport validatePreparedProtein(
                PreparedProtein preparedProtein) {
            throw new UnsupportedOperationException();
        }

        @Override
        public LigandPreparationResult prepareLigand(
                Ligand ligand, LigandPreparationOptions options) {
            throw new UnsupportedOperationException();
        }

        @Override
        public LigandPreparationResult prepareLigand(
                Path sdfInput, LigandPreparationOptions options) {
            receivedSdf = sdfInput;
            return ligandResult;
        }

        @Override
        public Path prepareAndWriteLigand(
                Path sdfInput, Path output,
                LigandPreparationOptions options) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Path writePreparedLigand(
                PreparedLigand preparedLigand, Path output)
                throws IOException {
            Path written = output.toAbsolutePath().normalize();
            Files.writeString(written,
                    "ROOT\nENDROOT\nTORSDOF 0\n");
            return written;
        }

        @Override
        public ValidationReport validatePreparedLigand(
                PreparedLigand preparedLigand) {
            return ValidationReport.validReport();
        }

        @Override
        public PdbqtValidationReport validatePdbqt(Path input) {
            throw new UnsupportedOperationException();
        }

        @Override
        public PdbqtValidationReport validateLigandPdbqt(Path input) {
            throw new UnsupportedOperationException();
        }

        @Override
        public PdbqtValidationReport validateFlexiblePdbqt(
                Path rigidInput, Path flexibleInput) {
            throw new UnsupportedOperationException();
        }
    }
}
