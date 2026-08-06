package totah.lab.daedalus.stage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import totah.lab.daedalus.ContextKeys;
import totah.lab.daedalus.PipelineContext;
import totah.lab.daedalus.docking.DockingInput;
import totah.lab.daedalus.docking.VinaDockingOptions;
import totah.lab.daedalus.docking.VinaDockingResult;
import totah.lab.daedalus.docking.VinaDockingRunner;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VinaDockingStageTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void missingSearchBoxFailsWithClearStageError() throws IOException {
        PipelineContext context = contextWithInput();

        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                () -> stage(fakeVina("""
                        AutoDock Vina v1.2.5
                        """)).run(context));
        assertTrue(failure.getMessage()
                .contains(ContextKeys.VINA_DOCKING_OPTIONS));
    }

    @Test
    void missingVinaExecutableIsAStageErrorNotACrash() throws IOException {
        PipelineContext context = contextWithInput()
                .with(ContextKeys.VINA_DOCKING_OPTIONS,
                        VinaDockingOptions.ofBox(
                                0, 0, 0, 20, 20, 20));

        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                () -> stage(temporaryDirectory.resolve("no-vina-here"))
                        .run(context));
        assertTrue(failure.getMessage()
                .contains("Vina execution failed"));
    }

    @Test
    void nonZeroVinaExitIsAStageErrorWithOutput() throws IOException {
        PipelineContext context = contextWithInput()
                .with(ContextKeys.VINA_DOCKING_OPTIONS,
                        VinaDockingOptions.ofBox(
                                0, 0, 0, 20, 20, 20));

        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                () -> stage(fakeVina("""
                        something went wrong in vina
                        """, 2)).run(context));
        assertTrue(failure.getMessage()
                .contains("Vina exited with code 2"));
        assertTrue(failure.getMessage()
                .contains("something went wrong in vina"));
    }

    @Test
    void successfulRunPublishesTheDockingResult() throws Exception {
        PipelineContext context = contextWithInput()
                .with(ContextKeys.VINA_DOCKING_OPTIONS,
                        VinaDockingOptions.ofBox(
                                0, 0, 0, 20, 20, 20));

        stage(fakeVina("""
                AutoDock Vina v1.2.5
                Detected 8 CPUs
                Reading input ... done.

                   mode |   affinity | dist from best mode
                        | (kcal/mol) | rmsd l.b.| rmsd u.b.
                   -----+------------+----------+----------
                      1        -7.5      0.000      0.000

                Writing output ... done.
                """)).run(context);

        VinaDockingResult result =
                context.require(ContextKeys.DOCKING_RESULT);
        assertEquals(0, result.exitCode());
        assertEquals(1, result.poses().size());
        assertEquals(-7.5, result.bestPose().orElseThrow().affinityKcalPerMol());
    }

    private PipelineContext contextWithInput() throws IOException {
        Path receptor = temporaryDirectory.resolve("receptor.pdbqt");
        Path ligand = temporaryDirectory.resolve("ligand.pdbqt");
        Files.writeString(receptor, "ROOT\nENDROOT\n");
        Files.writeString(ligand, "ROOT\nENDROOT\nTORSDOF 0\n");
        return new PipelineContext(temporaryDirectory, temporaryDirectory)
                .with(ContextKeys.DOCKING_INPUT, new DockingInput(
                        receptor, ligand, Optional.empty()));
    }

    private VinaDockingStage stage(Path vinaExecutable) {
        return new VinaDockingStage(new VinaDockingRunner(vinaExecutable));
    }

    private Path fakeVina(String output) throws IOException {
        return fakeVina(output, 0);
    }

    private Path fakeVina(String output, int exitCode) throws IOException {
        Path script = temporaryDirectory.resolve(
                "fake-vina-" + exitCode + "-" + output.length() + ".sh");
        StringBuilder body = new StringBuilder("#!/bin/sh\n");
        for (String line : output.split("\n", -1)) {
            body.append("printf '%s\\n' '")
                    .append(line.replace("'", "'\\''"))
                    .append("'\n");
        }
        body.append("exit ").append(exitCode).append('\n');
        Files.writeString(script, body.toString());
        script.toFile().setExecutable(true);
        return script;
    }
}
