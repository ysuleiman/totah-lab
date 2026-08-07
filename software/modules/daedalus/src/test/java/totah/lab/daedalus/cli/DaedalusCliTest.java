package totah.lab.daedalus.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DaedalusCliTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void topLevelHelpIsGeneratedFromRegisteredCommandsOnly() {
        DaedalusCli cli = new DaedalusCli();

        assertEquals(Set.of("dock-prep", "compare-ligand-prep",
                "diagnose-ad4-typing", "version", "help"),
                cli.registry().names());
        for (String command : cli.registry().names()) {
            assertTrue(cli.topLevelHelp().contains("    " + command + "\n"));
        }
        assertFalse(cli.topLevelHelp().contains("dock-pockets"));
        assertFalse(cli.topLevelHelp().contains("import"));
    }

    @Test
    void everyRegisteredCommandSupportsHelp() {
        DaedalusCli cli = new DaedalusCli();

        for (String command : cli.registry().names()) {
            RunResult result = run(cli, command, "--help");
            assertEquals(CliExitCode.SUCCESS, result.exitCode(), command);
            assertTrue(result.output().contains("USAGE"), command);
        }
    }

    @Test
    void dockPrepHelpAdvertisesTheBoxContract() {
        String help = new DaedalusCli().registry()
                .find("dock-prep").orElseThrow().help();

        for (String option : List.of("--target", "--ligand", "--out",
                "--box", "--pocket-id", "--padding", "--vina",
                "--overwrite", "--help")) {
            assertTrue(help.contains(option), option);
        }
        assertTrue(help.contains("Exactly one of --box / --pocket-id"));
    }

    @Test
    void boxAndPocketIdAreExclusive() {
        RunResult result = run(new DaedalusCli(), "dock-prep",
                "--target", "t.pdb", "--ligand", "l.sdf",
                "--out", temporaryDirectory.toString(),
                "--box", "0,0,0,20,20,20", "--pocket-id", "7");

        assertEquals(CliExitCode.INVALID_ARGUMENTS, result.exitCode());
        assertTrue(result.error().contains("exactly one"));
    }

    @Test
    void paddingOnlyAppliesToPocketId() {
        RunResult result = run(new DaedalusCli(), "dock-prep",
                "--target", "t.pdb", "--ligand", "l.sdf",
                "--out", temporaryDirectory.toString(),
                "--box", "0,0,0,20,20,20", "--padding", "10");

        assertEquals(CliExitCode.INVALID_ARGUMENTS, result.exitCode());
        assertTrue(result.error().contains("--padding"));
    }

    @Test
    void vinaRequiresABox() {
        RunResult result = run(new DaedalusCli(), "dock-prep",
                "--target", "t.pdb", "--ligand", "l.sdf",
                "--out", temporaryDirectory.toString(),
                "--vina", "/bin/true");

        assertEquals(CliExitCode.INVALID_ARGUMENTS, result.exitCode());
        assertTrue(result.error().contains("--vina requires"));
    }

    @Test
    void malformedBoxIsRejected() {
        RunResult result = run(new DaedalusCli(), "dock-prep",
                "--target", "t.pdb", "--ligand", "l.sdf",
                "--out", temporaryDirectory.toString(),
                "--box", "0,0,0,20,20");

        assertEquals(CliExitCode.INVALID_ARGUMENTS, result.exitCode());
        assertTrue(result.error().contains("--box"));
    }

    @Test
    void missingTargetIsAnIoFailure() {
        RunResult result = run(new DaedalusCli(), "dock-prep",
                "--target",
                temporaryDirectory.resolve("missing.pdb").toString(),
                "--ligand",
                temporaryDirectory.resolve("ligand.sdf").toString(),
                "--out", temporaryDirectory.resolve("runs").toString());

        assertEquals(CliExitCode.IO_FAILURE, result.exitCode());
    }

    @Test
    void dockPrepRunsEndToEndWithoutVinaAndWithoutBox() throws Exception {
        Path out = temporaryDirectory.resolve("runs");

        RunResult result = run(new DaedalusCli(), "dock-prep",
                "--target", resource("Q6UX53/Q6UX53_TMT1B_HUMAN.pdb"),
                "--ligand", resource("ligand/SAM.sdf"),
                "--out", out.toString());

        assertEquals(CliExitCode.SUCCESS, result.exitCode(),
                result.error());
        assertTrue(result.output().contains("Receptor PDBQT:"),
                result.output());
        assertTrue(result.output().contains("Ligand PDBQT:"),
                result.output());
        assertTrue(result.output().contains("Search box: (none)"),
                result.output());

        Path runDirectory = Files.list(out).findFirst().orElseThrow();
        assertTrue(Files.isRegularFile(
                runDirectory.resolve("prepared_receptor.pdbqt")));
        assertTrue(Files.isRegularFile(
                runDirectory.resolve("ligand.pdbqt")));
    }

    @Test
    void explicitBoxIsAcceptedWithoutVina() throws Exception {
        RunResult result = run(new DaedalusCli(), "dock-prep",
                "--target", resource("Q6UX53/Q6UX53_TMT1B_HUMAN.pdb"),
                "--ligand", resource("ligand/SAM.sdf"),
                "--out", temporaryDirectory.resolve("runs").toString(),
                "--box", "1,2,3,20,22,24");

        assertEquals(CliExitCode.SUCCESS, result.exitCode(),
                result.error());
        assertTrue(result.output().contains(
                "Search box: center 1.000 2.000 3.000,"
                        + " size 20.0 22.0 24.0"), result.output());
    }

    @Test
    void compareLigandPrepHelpAdvertisesOptions() {
        String help = new DaedalusCli().registry()
                .find("compare-ligand-prep").orElseThrow().help();

        for (String option : List.of("--count", "--report",
                "--reference-dir", "--help")) {
            assertTrue(help.contains(option), option);
        }
    }

    @Test
    void compareLigandPrepRejectsInvalidCount() {
        RunResult result = run(new DaedalusCli(), "compare-ligand-prep",
                "--count", "0");

        assertEquals(CliExitCode.INVALID_ARGUMENTS, result.exitCode());
        assertTrue(result.error().contains("--count"));
    }

    private static String resource(String name) {
        try {
            return Path.of(DaedalusCliTest.class.getClassLoader()
                    .getResource(name).toURI()).toString();
        } catch (URISyntaxException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private RunResult run(DaedalusCli cli, String... arguments) {
        StringWriter output = new StringWriter();
        StringWriter error = new StringWriter();
        int exitCode = cli.run(arguments,
                new PrintWriter(output), new PrintWriter(error));
        return new RunResult(exitCode, output.toString(), error.toString());
    }

    private record RunResult(int exitCode, String output, String error) {
    }
}
