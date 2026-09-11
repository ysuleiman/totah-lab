package totah.lab.daedalus.docking;

import totah.lab.daedalus.DockingProperties;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Minimal AutoDock Vina runner: builds the process from a validated
 * {@link DockingInput} and a caller-supplied search box, captures the
 * combined process output and parses the pose table. A non-zero exit code
 * is reported on the result, not hidden.
 */
public final class VinaDockingRunner {

    private final Path vinaExecutable;
    private final Duration defaultExecutionTimeout;

    public VinaDockingRunner(Path vinaExecutable) {
        this(vinaExecutable, VinaExecutionOptions.DEFAULT_EXECUTION_TIMEOUT);
    }

    public VinaDockingRunner(Path vinaExecutable, Duration defaultExecutionTimeout) {
        this.vinaExecutable = Objects.requireNonNull(vinaExecutable, "vinaExecutable");
        this.defaultExecutionTimeout = requirePositive(defaultExecutionTimeout);
    }

    public static VinaDockingRunner fromProperties(DockingProperties properties) {
        Objects.requireNonNull(properties, "properties");
        return new VinaDockingRunner(properties.vinaExecutable());
    }

    public VinaDockingResult run(
            DockingInput input,
            VinaDockingOptions options) throws IOException, InterruptedException {
        return run(input, options, null);
    }

    /**
     * Runs Vina with an explicit pose-output artifact for downstream workflows.
     */
    public VinaDockingResult run(
            DockingInput input,
            VinaDockingOptions options,
            Path poseOutput) throws IOException, InterruptedException {
        return run(input, options, null, poseOutput);
    }

    /**
     * Runs Vina with explicit output controls. Existing overloads intentionally
     * retain their historical behavior and continue to use Vina defaults.
     */
    public VinaDockingResult run(
            DockingInput input,
            VinaDockingOptions options,
            VinaPoseOutputOptions outputOptions,
            Path poseOutput) throws IOException, InterruptedException {
        return run(input, options, outputOptions, null, poseOutput);
    }

    /**
     * Runs Vina with explicit pose and per-process resource controls. This is
     * additive so existing callers retain Vina's historical CPU default.
     */
    public VinaDockingResult run(
            DockingInput input,
            VinaDockingOptions options,
            VinaPoseOutputOptions outputOptions,
            VinaExecutionOptions executionOptions,
            Path poseOutput) throws IOException, InterruptedException {
        Objects.requireNonNull(input, "input");
        Objects.requireNonNull(options, "options");
        requireFile(vinaExecutable, "Vina executable");
        requireFile(input.receptorPdbqt(), "Receptor PDBQT");
        requireFile(input.ligandPdbqt(), "Ligand PDBQT");
        input.flexPdbqt().ifPresent(flex -> requireFile(flex, "Flex PDBQT"));
        Path normalizedPoseOutput = normalizeOutput(poseOutput);

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
        if (normalizedPoseOutput != null) {
            command.add("--out");
            command.add(normalizedPoseOutput.toString());
        }
        if (options.seed() != null) {
            command.add("--seed");
            command.add(Integer.toString(options.seed()));
        }
        if (outputOptions != null) {
            command.add("--num_modes");
            command.add(Integer.toString(outputOptions.maximumModes()));
            command.add("--energy_range");
            command.add(Double.toString(
                    outputOptions.energyRangeKcalPerMol()));
        }
        if (executionOptions != null) {
            command.add("--cpu");
            command.add(Integer.toString(executionOptions.cpuThreads()));
        }

        String output;
        int exitCode;
        Path processLog = Files.createTempFile("vina-process-", ".log");
        Process process = null;
        Duration timeout = executionOptions == null ? defaultExecutionTimeout
                : executionOptions.executionTimeout();
        try {
            process = new ProcessBuilder(command).redirectErrorStream(true)
                    .redirectOutput(processLog.toFile()).start();
            if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                terminate(process);
                throw new IOException("Vina process timed out after " + timeout);
            }
            output = Files.readString(processLog, StandardCharsets.UTF_8);
            exitCode = process.exitValue();
        } catch (InterruptedException exception) {
            if (process != null) terminateWithoutWaiting(process);
            Thread.currentThread().interrupt();
            throw exception;
        } finally {
            Files.deleteIfExists(processLog);
        }
        return new VinaDockingResult(
                exitCode, VinaOutputParser.parse(output), output);
    }

    private static Path normalizeOutput(Path output) throws IOException {
        if (output == null) {
            return null;
        }
        Path normalized = output.toAbsolutePath().normalize();
        Path parent = normalized.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        return normalized;
    }

    private static void requireFile(Path path, String description) {
        if (path == null || !Files.isRegularFile(path)) {
            throw new IllegalArgumentException(
                    description + " does not exist: " + path);
        }
    }

    private static Duration requirePositive(Duration timeout) {
        Objects.requireNonNull(timeout, "defaultExecutionTimeout");
        if (timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("defaultExecutionTimeout must be positive");
        }
        return timeout;
    }

    private static void terminate(Process process) throws InterruptedException {
        terminateWithoutWaiting(process);
        process.waitFor(10, TimeUnit.SECONDS);
    }

    private static void terminateWithoutWaiting(Process process) {
        process.descendants().forEach(ProcessHandle::destroyForcibly);
        process.destroyForcibly();
    }
}
