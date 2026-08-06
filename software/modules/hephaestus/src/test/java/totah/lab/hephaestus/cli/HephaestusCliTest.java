package totah.lab.hephaestus.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import totah.lab.hephaestus.client.HephaestusCapabilities;
import totah.lab.hephaestus.client.HephaestusCapability;
import totah.lab.hephaestus.client.HephaestusClient;
import totah.lab.gaia.chemistry.Element;
import totah.lab.gaia.geometry.Point3D;
import totah.lab.gaia.molecule.Ligand;
import totah.lab.gaia.molecule.Protein;
import totah.lab.gaia.structure.Atom;
import totah.lab.gaia.structure.Chain;
import totah.lab.gaia.structure.Residue;
import totah.lab.gaia.structure.Structure;
import totah.lab.hephaestus.ligand.LigandPreparationOptions;
import totah.lab.hephaestus.ligand.LigandPreparationResult;
import totah.lab.hephaestus.model.PreparationIssue;
import totah.lab.hephaestus.model.PreparedLigand;
import totah.lab.hephaestus.model.Severity;
import totah.lab.hephaestus.model.PreparedProtein;
import totah.lab.hephaestus.receptor.ReceptorPreparationOptions;
import totah.lab.hephaestus.receptor.ReceptorPreparationResult;
import totah.lab.hephaestus.validation.ValidationReport;
import totah.lab.hermes.file.writer.pdbqt.PdbqtWriter;
import totah.lab.hermes.file.writer.pdbqt.PdbqtWriteResult;
import totah.lab.hermes.file.writer.pdbqt.validation.PdbqtValidationCode;
import totah.lab.hermes.file.writer.pdbqt.validation.PdbqtValidationIssue;
import totah.lab.hermes.file.writer.pdbqt.validation.PdbqtValidationReport;
import totah.lab.hermes.file.writer.pdbqt.validation.PdbqtValidationSeverity;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HephaestusCliTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void topLevelHelpIsGeneratedFromRegisteredCommandsOnly() {
        HephaestusCli cli = cli(new StubClient());

        assertEquals(Set.of("prepare-receptor", "prepare-ligand",
                "validate-pdbqt", "validate-ligand-pdbqt",
                "validate-flex-pdbqt", "version", "help"),
                cli.registry().names());
        for (String command : cli.registry().names()) {
            assertTrue(cli.topLevelHelp().contains("    " + command + "\n"));
        }
        assertFalse(cli.topLevelHelp().contains("prepare-flex-receptor"));
        assertFalse(cli.topLevelHelp().contains("validate-prepared"));
        assertFalse(cli.topLevelHelp().contains("inspect"));
        assertFalse(cli.topLevelHelp().contains("convert"));
    }

    @Test
    void everyRegisteredCommandSupportsHelp() {
        HephaestusCli cli = cli(new StubClient());

        for (String command : cli.registry().names()) {
            RunResult result = run(cli, command, "--help");
            assertEquals(CliExitCode.SUCCESS, result.exitCode(), command);
            assertTrue(result.output().contains("USAGE"), command);
        }
    }

    @Test
    void prepareHelpReflectsActualDefaultsAndOptions() {
        String help = cli(new StubClient()).registry()
                .find("prepare-receptor").orElseThrow().help();

        assertTrue(help.contains("Preparation pH. Default: 7.4."));
        assertTrue(help.contains("Histidine protonation state. Default: HIE."));
        assertTrue(help.contains("Default: 2.2."));
        for (String option : List.of("--input", "--output", "--ph",
                "--plddt-cutoff", "--his", "--detect-disulfides",
                "--disulfide-cutoff", "--remove-waters", "--keep-metals",
                "--strict", "--overwrite", "--help")) {
            assertTrue(help.contains(option), option);
        }
        assertFalse(help.contains("--threads"));
        assertFalse(help.contains("--output-format"));
    }

    @Test
    void everyAdvertisedPrepareOptionIsParsed() throws IOException {
        StubClient client = new StubClient();
        client.prepareFailure = new IOException("expected read failure");

        RunResult result = run(cli(client), "prepare-receptor",
                "--input", temporaryDirectory.resolve("input.pdb").toString(),
                "--output", temporaryDirectory.resolve("output.pdbqt").toString(),
                "--ph", "6.5", "--plddt-cutoff", "80", "--his", "HID",
                "--detect-disulfides", "--disulfide-cutoff", "2.3",
                "--remove-waters", "--keep-metals", "--strict", "--overwrite");

        assertEquals(CliExitCode.IO_FAILURE, result.exitCode());
        assertFalse(result.error().contains("Unknown option"));
    }

    @Test
    void unsupportedEnumValueIsRejected() {
        RunResult result = run(cli(new StubClient()),
                "prepare-receptor", "--input", "input.pdb", "--output",
                temporaryDirectory.resolve("output.pdbqt").toString(),
                "--his", "HIS");

        assertEquals(CliExitCode.INVALID_ARGUMENTS, result.exitCode());
        assertTrue(result.error().contains("Unsupported value for --his"));
    }

    @Test
    void validationErrorsAndStrictWarningsUseStableExitCode() throws IOException {
        StubClient client = new StubClient();
        PdbqtValidationIssue warning = new PdbqtValidationIssue(
                PdbqtValidationSeverity.WARNING,
                PdbqtValidationCode.UNSUPPORTED_RECORD,
                "warning", "line 1");
        client.pdbqtReport = new PdbqtValidationReport(List.of(warning));

        assertEquals(CliExitCode.SUCCESS,
                run(cli(client), "validate-pdbqt", "--input", "a.pdbqt")
                        .exitCode());
        assertEquals(CliExitCode.VALIDATION_ERROR,
                run(cli(client), "validate-pdbqt", "--input", "a.pdbqt",
                        "--strict").exitCode());
        assertEquals(CliExitCode.SUCCESS,
                run(cli(client), "validate-ligand-pdbqt", "--input",
                        "a.pdbqt").exitCode());
        assertEquals(CliExitCode.VALIDATION_ERROR,
                run(cli(client), "validate-ligand-pdbqt", "--input",
                        "a.pdbqt", "--strict").exitCode());
    }

    @Test
    void exitCodeContractIsStable() {
        assertEquals(List.of(0, 1, 2, 3, 4, 5, 6), List.of(
                CliExitCode.SUCCESS, CliExitCode.VALIDATION_ERROR,
                CliExitCode.INVALID_ARGUMENTS, CliExitCode.IO_FAILURE,
                CliExitCode.PREPARATION_FAILURE, CliExitCode.EXPORT_FAILURE,
                CliExitCode.INTERNAL_FAILURE));
    }

    @Test
    void capabilitiesReportOnlyImplementedLibraryFeatures() {
        assertEquals(Set.of(
                HephaestusCapability.PREPARE_RIGID_RECEPTOR,
                HephaestusCapability.VALIDATE_PREPARED_PROTEIN,
                HephaestusCapability.VALIDATE_PDBQT,
                HephaestusCapability.VALIDATE_LIGAND_PDBQT,
                HephaestusCapability.VALIDATE_FLEXIBLE_PDBQT,
                HephaestusCapability.PREPARE_LIGAND),
                HephaestusCapabilities.supported());
        assertFalse(HephaestusCapabilities.supported().contains(
                HephaestusCapability.PREPARE_FLEXIBLE_RECEPTOR));
    }

    @Test
    void prepareLigandHelpReflectsActualOptions() {
        String help = cli(new StubClient()).registry()
                .find("prepare-ligand").orElseThrow().help();

        for (String option : List.of("--input", "--output",
                "--largest-fragment", "--no-hydrogens", "--no-charges",
                "--no-atom-types", "--overwrite", "--help")) {
            assertTrue(help.contains(option), option);
        }
        // Unimplemented library options are not advertised.
        assertFalse(help.contains("--protonation-states"));
        assertFalse(help.contains("--tautomers"));
        assertFalse(help.contains("--conformers"));
    }

    @Test
    void prepareLigandWritesSummaryOnSuccess() {
        StubClient client = new StubClient();
        client.ligandResult = new LigandPreparationResult(
                PreparedLigand.of(ligand()), List.of());

        RunResult result = run(cli(client), "prepare-ligand",
                "--input", temporaryDirectory.resolve("ligand.sdf").toString(),
                "--output", temporaryDirectory.resolve("ligand.pdbqt").toString(),
                "--largest-fragment", "--no-hydrogens", "--no-charges",
                "--no-atom-types", "--overwrite");

        assertEquals(CliExitCode.SUCCESS, result.exitCode(),
                result.error());
        assertTrue(result.output().contains("Ligand atoms: 2"),
                result.output());
        assertTrue(result.output().contains("Rotatable bonds: 2"),
                result.output());
        assertTrue(result.output().contains("Wrote ligand PDBQT: "),
                result.output());
    }

    @Test
    void prepareLigandInputFailureUsesIoExitCode() {
        StubClient client = new StubClient();
        client.ligandFailure = new IOException("expected read failure");

        RunResult result = run(cli(client), "prepare-ligand",
                "--input", temporaryDirectory.resolve("missing.sdf").toString(),
                "--output", temporaryDirectory.resolve("out.pdbqt").toString());

        assertEquals(CliExitCode.IO_FAILURE, result.exitCode());
    }

    @Test
    void prepareLigandErrorsUsePreparationExitCode() {
        StubClient client = new StubClient();
        client.ligandResult = new LigandPreparationResult(
                PreparedLigand.of(ligand()),
                List.of(new PreparationIssue(
                        Severity.ERROR, "BROKEN", "broken ligand")));

        RunResult result = run(cli(client), "prepare-ligand",
                "--input", temporaryDirectory.resolve("ligand.sdf").toString(),
                "--output", temporaryDirectory.resolve("out.pdbqt").toString());

        assertEquals(CliExitCode.PREPARATION_FAILURE, result.exitCode());
        assertTrue(result.error().contains("BROKEN"), result.error());
    }

    @Test
    void prepareLigandRefusesToOverwriteWithoutFlag() throws IOException {
        Path existing = temporaryDirectory.resolve("existing.pdbqt");
        Files.writeString(existing, "ROOT\nENDROOT\n");

        RunResult result = run(cli(new StubClient()), "prepare-ligand",
                "--input", temporaryDirectory.resolve("ligand.sdf").toString(),
                "--output", existing.toString());

        assertEquals(CliExitCode.EXPORT_FAILURE, result.exitCode());
        assertTrue(result.error().contains("--overwrite"), result.error());
    }

    private static Ligand ligand() {
        return new Ligand("L", "ligand", null, null, null, null,
                new Structure(List.of(new Chain("L", List.of(
                        new Residue("LIG", 1, List.of(
                                new Atom(1, "C1", null, null,
                                        new Point3D(0, 0, 0),
                                        0.0, 1.0, 0.0, Element.C, null),
                                new Atom(2, "O1", null, null,
                                        new Point3D(1.4, 0, 0),
                                        -0.3, 1.0, 0.0, Element.O, null))))))));
    }

    private HephaestusCli cli(HephaestusClient client) {
        return new HephaestusCli(client, new PdbqtWriter());
    }

    private RunResult run(HephaestusCli cli, String... arguments) {
        StringWriter output = new StringWriter();
        StringWriter error = new StringWriter();
        int exitCode = cli.run(arguments,
                new PrintWriter(output), new PrintWriter(error));
        return new RunResult(exitCode, output.toString(), error.toString());
    }

    private record RunResult(int exitCode, String output, String error) {
    }

    private static final class StubClient implements HephaestusClient {
        private IOException prepareFailure;
        private IOException ligandFailure;
        private LigandPreparationResult ligandResult;
        private PdbqtValidationReport pdbqtReport =
                new PdbqtValidationReport(List.of());

        @Override
        public ReceptorPreparationResult prepareReceptor(
                Protein protein, ReceptorPreparationOptions options) {
            throw new UnsupportedOperationException("not needed by this test");
        }

        @Override
        public ReceptorPreparationResult prepareReceptor(
                Path input, ReceptorPreparationOptions options)
                throws IOException {
            if (prepareFailure != null) throw prepareFailure;
            throw new UnsupportedOperationException("not needed by this test");
        }

        @Override
        public PdbqtWriteResult prepareAndWriteReceptor(
                Path input, Path output, ReceptorPreparationOptions options) {
            throw new UnsupportedOperationException("not needed by this test");
        }

        @Override
        public PdbqtWriteResult writePreparedReceptor(
                PreparedProtein preparedProtein, Path output) {
            throw new UnsupportedOperationException("not needed by this test");
        }

        @Override
        public ValidationReport validatePreparedProtein(
                PreparedProtein preparedProtein) {
            throw new UnsupportedOperationException("not needed by this test");
        }

        @Override
        public LigandPreparationResult prepareLigand(
                Ligand ligand, LigandPreparationOptions options) {
            throw new UnsupportedOperationException("not needed by this test");
        }

        @Override
        public LigandPreparationResult prepareLigand(
                Path sdfInput, LigandPreparationOptions options)
                throws IOException {
            if (ligandFailure != null) throw ligandFailure;
            if (ligandResult != null) return ligandResult;
            throw new UnsupportedOperationException("not needed by this test");
        }

        @Override
        public Path prepareAndWriteLigand(
                Path sdfInput, Path output, LigandPreparationOptions options) {
            throw new UnsupportedOperationException("not needed by this test");
        }

        @Override
        public Path writePreparedLigand(
                PreparedLigand preparedLigand, Path output)
                throws IOException {
            Path written = output.toAbsolutePath().normalize();
            Files.writeString(written, String.join("\n",
                    "ROOT",
                    "ATOM      1  C1  LIG L   1       0.000   0.000   0.000  1.00  0.00     0.100 C ",
                    "ATOM      2  O1  LIG L   1       1.400   0.000   0.000  1.00  0.00    -0.300 O ",
                    "ENDROOT",
                    "TORSDOF 2") + "\n");
            return written;
        }

        @Override
        public ValidationReport validatePreparedLigand(
                PreparedLigand preparedLigand) {
            throw new UnsupportedOperationException("not needed by this test");
        }

        @Override
        public PdbqtValidationReport validatePdbqt(Path input) {
            return pdbqtReport;
        }

        @Override
        public PdbqtValidationReport validateLigandPdbqt(Path input) {
            return pdbqtReport;
        }

        @Override
        public PdbqtValidationReport validateFlexiblePdbqt(
                Path rigidInput, Path flexibleInput) {
            return pdbqtReport;
        }
    }
}
