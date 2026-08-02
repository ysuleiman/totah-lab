package totah.lab.daedalus.stage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import totah.lab.gaia.molecule.Protein;
import totah.lab.gaia.structure.Chain;
import totah.lab.gaia.structure.Residue;
import totah.lab.gaia.structure.Structure;
import totah.lab.hephaestus.client.HephaestusClient;
import totah.lab.hephaestus.model.PreparedProtein;
import totah.lab.hephaestus.receptor.ReceptorPreparationOptions;
import totah.lab.hephaestus.receptor.ReceptorPreparationResult;
import totah.lab.hephaestus.validation.ValidationReport;
import totah.lab.hermes.file.writer.pdbqt.PdbqtWriteResult;
import totah.lab.hermes.file.writer.pdbqt.validation.PdbqtValidationReport;
import totah.lab.daedalus.ContextKeys;
import totah.lab.daedalus.PipelineContext;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class ReceptorPreparationStageTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void delegatesLoadedProteinAndPublishesPreparedArtifacts() throws Exception {
        Protein protein = protein();
        PreparedProtein prepared = PreparedProtein.of(protein);
        StubClient client = new StubClient(prepared);
        PipelineContext context = new PipelineContext(
                temporaryDirectory, temporaryDirectory)
                .with(ContextKeys.TARGET_PROTEIN, protein);

        new ReceptorPreparationStage(client).run(context);

        assertSame(protein, client.receivedProtein);
        assertSame(prepared, context.require(ContextKeys.PREPARED_PROTEIN));
        assertEquals(temporaryDirectory.resolve("prepared_receptor.pdbqt")
                        .toAbsolutePath().normalize().toString(),
                context.require(ContextKeys.RECEPTOR_PDBQT));
    }

    private Protein protein() {
        return new Protein("target", null, "target", null, null, null,
                new Structure(List.of(new Chain("A", List.of(
                        new Residue("ALA", 1, List.of()))))));
    }

    private static final class StubClient implements HephaestusClient {
        private final PreparedProtein preparedProtein;
        private Protein receivedProtein;

        private StubClient(PreparedProtein preparedProtein) {
            this.preparedProtein = preparedProtein;
        }

        @Override
        public ReceptorPreparationResult prepareReceptor(
                Protein protein, ReceptorPreparationOptions options) {
            receivedProtein = protein;
            return new ReceptorPreparationResult(preparedProtein, List.of());
        }

        @Override
        public ReceptorPreparationResult prepareReceptor(
                Path input, ReceptorPreparationOptions options) {
            throw new UnsupportedOperationException();
        }

        @Override
        public PdbqtWriteResult prepareAndWriteReceptor(
                Path input, Path output, ReceptorPreparationOptions options) {
            throw new UnsupportedOperationException();
        }

        @Override
        public PdbqtWriteResult writePreparedReceptor(
                PreparedProtein preparedProtein, Path output) {
            Path normalized = output.toAbsolutePath().normalize();
            return new PdbqtWriteResult(normalized, null, 0, 0, 0, 0);
        }

        @Override
        public ValidationReport validatePreparedProtein(
                PreparedProtein preparedProtein) {
            return ValidationReport.validReport();
        }

        @Override
        public PdbqtValidationReport validatePdbqt(Path input) {
            throw new UnsupportedOperationException();
        }

        @Override
        public PdbqtValidationReport validateFlexiblePdbqt(
                Path rigidInput, Path flexibleInput) {
            throw new UnsupportedOperationException();
        }
    }
}
