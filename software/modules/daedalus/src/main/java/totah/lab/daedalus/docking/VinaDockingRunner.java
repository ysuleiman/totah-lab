package totah.lab.daedalus.docking;

import totah.lab.daedalus.DockingProperties;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Minimal AutoDock Vina runner: builds the process from a validated
 * {@link DockingInput} and a caller-supplied search box, captures the
 * combined process output and parses the pose table. A non-zero exit code
 * is reported on the result, not hidden.
 */
public final class VinaDockingRunner {

    private final Path vinaExecutable;

    public VinaDockingRunner(Path vinaExecutable) {
        this.vinaExecutable = Objects.requireNonNull(vinaExecutable, "vinaExecutable");
    }

    public static VinaDockingRunner fromProperties(DockingProperties properties) {
        Objects.requireNonNull(properties, "properties");
        return new VinaDockingRunner(properties.vinaExecutable());
    }

    public VinaDockingResult run(
            DockingInput input,
            VinaDockingOptions options) throws IOException, InterruptedException {
        Objects.requireNonNull(input, "input");
        Objects.requireNonNull(options, "options");
        requireFile(vinaExecutable, "Vina executable");
        requireFile(input.receptorPdbqt(), "Receptor PDBQT");
        requireFile(input.ligandPdbqt(), "Ligand PDBQT");
        input.flexPdbqt().ifPresent(flex -> requireFile(flex, "Flex PDBQT"));

        List<String> command = new ArrayList<>(List.of(
                vinaExecutable.toAbsolutePath().normalize().toString(),
                "--receptor", input.receptorPdbqt().toAbsolutePath().normalize().toString(),
                "--ligand", input.ligandPdbqt().toAbsolutePath().normalize().toString(),
                "--center_x", Double.toString(options.centerX()),
                "--center_y", Double.toString(options.centerY()),
                "--center_z", Double.toString(options.centerZ()),
                "--size_x", Double.toString(options.sizeX()),
                "--size_y", Double.toString(options.sizeY()),
                "--size_z", Double.toString(options.sizeZ()),
                "--exhaustiveness", Integer.toString(options.exhaustiveness())));
        input.flexPdbqt().ifPresent(flex -> {
            command.add("--flex");
            command.add(flex.toAbsolutePath().normalize().toString());
        });
        if (options.seed() != null) {
            command.add("--seed");
            command.add(Integer.toString(options.seed()));
        }

        Process process = new ProcessBuilder(command)
                .redirectErrorStream(true)
                .start();
        String output;
        int exitCode;
        try {
            output = new String(
                    process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            exitCode = process.waitFor();
        } catch (InterruptedException exception) {
            process.destroy();
            Thread.currentThread().interrupt();
            throw exception;
        }
        return new VinaDockingResult(
                exitCode, VinaOutputParser.parse(output), output);
    }

    private static void requireFile(Path path, String description) {
        if (path == null || !Files.isRegularFile(path)) {
            throw new IllegalArgumentException(
                    description + " does not exist: " + path);
        }
    }
}
