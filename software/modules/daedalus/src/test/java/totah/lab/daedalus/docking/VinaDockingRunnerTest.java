package totah.lab.daedalus.docking;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VinaDockingRunnerTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void buildsCommandLineAndParsesPoses() throws Exception {
        Path receptor = touch("receptor.pdbqt");
        Path ligand = touch("ligand.pdbqt");
        Path argumentsFile = temporaryDirectory.resolve("args.txt");
        Path fakeVina = fakeVina("""
                #!/bin/bash
                printf '%%s\\n' "$@" > "%s"
                echo "   mode |   affinity | dist from best mode"
                echo "      1        -9.4      0.000      0.000"
                echo "      2        -8.7      1.111      2.222"
                exit 0
                """.formatted(argumentsFile));

        VinaDockingResult result = new VinaDockingRunner(fakeVina).run(
                new DockingInput(receptor, ligand, Optional.empty()),
                VinaDockingOptions.ofBox(1.0, 2.0, 3.0, 20.0, 22.0, 24.0)
                        .withSeed(42));

        assertEquals(0, result.exitCode());
        assertEquals(2, result.poses().size());
        assertEquals(-9.4, result.bestPose().orElseThrow().affinityKcalPerMol(), 1.0e-9);

        List<String> arguments = Files.readAllLines(argumentsFile);
        int receptorFlag = arguments.indexOf("--receptor");
        assertTrue(receptorFlag >= 0);
        assertEquals(receptor.toAbsolutePath().normalize().toString(),
                arguments.get(receptorFlag + 1));
        assertTrue(arguments.contains("--center_x"));
        assertEquals("3.0", arguments.get(arguments.indexOf("--center_z") + 1));
        assertEquals("24.0", arguments.get(arguments.indexOf("--size_z") + 1));
        assertEquals("8", arguments.get(arguments.indexOf("--exhaustiveness") + 1));
        assertEquals("42", arguments.get(arguments.indexOf("--seed") + 1));
    }

    @Test
    void passesFlexFileWhenPresent() throws Exception {
        Path receptor = touch("receptor.pdbqt");
        Path ligand = touch("ligand.pdbqt");
        Path flex = touch("flex.pdbqt");
        Path argumentsFile = temporaryDirectory.resolve("args.txt");
        Path fakeVina = fakeVina("""
                #!/bin/bash
                printf '%%s\\n' "$@" > "%s"
                exit 0
                """.formatted(argumentsFile));

        new VinaDockingRunner(fakeVina).run(
                new DockingInput(receptor, ligand, Optional.of(flex)),
                VinaDockingOptions.ofBox(0.0, 0.0, 0.0, 20.0, 20.0, 20.0));

        List<String> arguments = Files.readAllLines(argumentsFile);
        assertTrue(arguments.contains("--flex"));
        assertEquals(flex.toAbsolutePath().normalize().toString(),
                arguments.get(arguments.indexOf("--flex") + 1));
    }

    @Test
    void passesExplicitPoseOutputForSequentialDocking() throws Exception {
        Path receptor = touch("receptor.pdbqt");
        Path ligand = touch("ligand.pdbqt");
        Path argumentsFile = temporaryDirectory.resolve("output-args.txt");
        Path poseOutput = temporaryDirectory.resolve("poses/sam-out.pdbqt");
        Path fakeVina = fakeVina("""
                #!/bin/bash
                printf '%%s\n' "$@" > "%s"
                exit 0
                """.formatted(argumentsFile));

        new VinaDockingRunner(fakeVina).run(
                new DockingInput(receptor, ligand, Optional.empty()),
                VinaDockingOptions.ofBox(
                        0.0, 0.0, 0.0, 20.0, 20.0, 20.0),
                poseOutput);

        List<String> arguments = Files.readAllLines(argumentsFile);
        int outputFlag = arguments.indexOf("--out");
        assertTrue(outputFlag >= 0);
        assertEquals(
                poseOutput.toAbsolutePath().normalize().toString(),
                arguments.get(outputFlag + 1));
        assertTrue(Files.isDirectory(poseOutput.getParent()));
    }

    @Test
    void reportsNonZeroExitCode() throws Exception {
        Path receptor = touch("receptor.pdbqt");
        Path ligand = touch("ligand.pdbqt");
        Path fakeVina = fakeVina("""
                #!/bin/bash
                echo "something went wrong"
                exit 3
                """);

        VinaDockingResult result = new VinaDockingRunner(fakeVina).run(
                new DockingInput(receptor, ligand, Optional.empty()),
                VinaDockingOptions.ofBox(0.0, 0.0, 0.0, 20.0, 20.0, 20.0));

        assertEquals(3, result.exitCode());
        assertTrue(result.poses().isEmpty());
        assertTrue(result.output().contains("something went wrong"));
    }

    @Test
    void rejectsMissingExecutableAndInputs() throws Exception {
        Path receptor = touch("receptor.pdbqt");
        Path ligand = touch("ligand.pdbqt");
        VinaDockingOptions box =
                VinaDockingOptions.ofBox(0.0, 0.0, 0.0, 20.0, 20.0, 20.0);

        assertThrows(IllegalArgumentException.class, () -> new VinaDockingRunner(
                temporaryDirectory.resolve("missing-vina"))
                .run(new DockingInput(receptor, ligand, Optional.empty()), box));
        assertThrows(IllegalArgumentException.class, () -> new VinaDockingRunner(
                touch("vina")).run(new DockingInput(
                temporaryDirectory.resolve("missing.pdbqt"), ligand, Optional.empty()), box));
    }

    private Path touch(String name) throws IOException {
        Path path = temporaryDirectory.resolve(name);
        Files.writeString(path, "");
        return path;
    }

    private Path fakeVina(String script) throws IOException {
        Path path = temporaryDirectory.resolve("fake-vina");
        Files.writeString(path, script);
        Files.setPosixFilePermissions(path, PosixFilePermissions.fromString("rwxr-xr-x"));
        return path;
    }
}
