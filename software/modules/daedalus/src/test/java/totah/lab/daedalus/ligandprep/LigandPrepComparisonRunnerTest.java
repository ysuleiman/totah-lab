package totah.lab.daedalus.ligandprep;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import totah.lab.daedalus.ligandprep.LigandPrepComparisonRunner.Outcome;
import totah.lab.gaia.molecule.Ligand;
import totah.lab.gaia.molecule.Protein;
import totah.lab.gaia.structure.Chain;
import totah.lab.gaia.structure.Residue;
import totah.lab.gaia.structure.Structure;
import totah.lab.hephaestus.client.HephaestusClient;
import totah.lab.hephaestus.ligand.LigandPreparationOptions;
import totah.lab.hephaestus.ligand.LigandPreparationResult;
import totah.lab.hephaestus.model.PreparedLigand;
import totah.lab.hephaestus.model.PreparedProtein;
import totah.lab.hephaestus.receptor.ReceptorPreparationOptions;
import totah.lab.hephaestus.receptor.ReceptorPreparationResult;
import totah.lab.hephaestus.validation.ValidationReport;
import totah.lab.hermes.file.pdbqt.PdbqtWriteResult;
import totah.lab.hermes.file.pdbqt.validation.PdbqtValidationReport;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LigandPrepComparisonRunnerTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void comparesPreparedLigandAgainstMeekoReference() throws Exception {
        Path reference = Files.createDirectories(
                temporaryDirectory.resolve("reference"));
        Path meeko = reference.resolve("meeko.pdbqt");
        Files.writeString(meeko, String.join("\n",
                "ROOT",
                atomLine(1, "C", 0, 0, 0, 0.04, "C"),
                atomLine(2, "O", 1.4, 0, 0, -0.31, "OA"),
                "ENDROOT",
                "TORSDOF 3"));

        Path sdf = reference.resolve("ligand.sdf");
        Files.writeString(sdf, "fake sdf");

        LigandPrepSample sample = new LigandPrepSample(
                "compound-1", "CO", sdf, meeko);

        List<Outcome> outcomes = new LigandPrepComparisonRunner(
                count -> List.of(sample),
                new StubClient(),
                temporaryDirectory.resolve("work")
        ).run(1);

        assertEquals(1, outcomes.size());
        Outcome outcome = outcomes.get(0);
        assertEquals(LigandPrepComparisonRunner.STATUS_OK,
                outcome.status());
        assertEquals(1.0,
                outcome.comparison().ad4TypeAgreement(), 1e-9);
        assertEquals(1, outcome.comparison().torsdofDelta());

        String csv = LigandPrepComparisonRunner.csv(outcomes);
        assertTrue(csv.contains("compound-1"));
        assertTrue(csv.contains("OK"));

        String summary = LigandPrepComparisonRunner.summary(outcomes);
        assertTrue(summary.contains("Sampled: 1"));
        assertTrue(summary.contains("Prepared and compared: 1"));
        assertTrue(summary.contains("AD4 type agreement"));
    }

    @Test
    void hydrogenFailuresAreRecordedNotFatal() throws Exception {
        Path reference = Files.createDirectories(
                temporaryDirectory.resolve("reference"));
        Path sdf = reference.resolve("ligand.sdf");
        Files.writeString(sdf, "fake");
        Path meeko = reference.resolve("meeko.pdbqt");
        Files.writeString(meeko,
                "ROOT\n" + atomLine(1, "C", 0, 0, 0, 0.0, "C")
                        + "\nENDROOT\nTORSDOF 0\n");

        LigandPrepSample sample = new LigandPrepSample(
                "compound-2", "C", sdf, meeko);

        StubClient client = new StubClient();
        client.prepareFailure = new IllegalArgumentException(
                "SDF ligand topology requires explicit hydrogen atoms");

        List<Outcome> outcomes = new LigandPrepComparisonRunner(
                count -> List.of(sample),
                client,
                temporaryDirectory.resolve("work")
        ).run(1);

        assertEquals(LigandPrepComparisonRunner.STATUS_FAILED,
                outcomes.get(0).status());
        assertEquals("missing-hydrogens",
                outcomes.get(0).failureCategory());

        String summary = LigandPrepComparisonRunner.summary(outcomes);
        assertTrue(summary.contains("Failed: 1"));
        assertTrue(summary.contains("missing-hydrogens: 1"));
    }

    private static final class StubClient implements HephaestusClient {
        private RuntimeException prepareFailure;

        @Override
        public LigandPreparationResult prepareLigand(
                Path sdfInput, LigandPreparationOptions options) {
            if (prepareFailure != null) throw prepareFailure;
            return new LigandPreparationResult(
                    PreparedLigand.of(new Ligand("L", "ligand",
                            null, null, null, null,
                            new Structure(List.of(new Chain("L",
                                    List.of(new Residue("LIG", 1,
                                            List.of()))))))),
                    List.of());
        }

        @Override
        public Path writePreparedLigand(
                PreparedLigand preparedLigand, Path output)
                throws IOException {
            Path written = output.toAbsolutePath().normalize();
            Files.writeString(written, String.join("\n",
                    "ROOT",
                    atomLine(1, "C1", 0, 0, 0, 0.05, "C"),
                    atomLine(2, "O1", 1.4, 0, 0, -0.30, "OA"),
                    "ENDROOT",
                    "TORSDOF 2") + "\n");
            return written;
        }

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
        public Path prepareAndWriteLigand(
                Path sdfInput, Path output,
                LigandPreparationOptions options) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ValidationReport validatePreparedLigand(
                PreparedLigand preparedLigand) {
            throw new UnsupportedOperationException();
        }

        @Override
        public PdbqtValidationReport validateLigandPdbqt(Path input) {
            throw new UnsupportedOperationException();
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

    static String atomLine(
            int serial,
            String name,
            double x,
            double y,
            double z,
            double charge,
            String ad4Type
    ) {
        return String.format(
                java.util.Locale.ROOT,
                "ATOM  %5d %-4s %3s %1s%4d    "
                        + "%8.3f%8.3f%8.3f%6.2f%6.2f    %6.3f %-2s",
                serial, name, "LIG", "L", 1,
                x, y, z, 1.0, 0.0, charge, ad4Type);
    }
}
