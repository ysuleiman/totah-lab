package totah.lab.athena.energy.openmm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import totah.lab.athena.energy.EnergyEvaluationException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/** Shared, non-scientific process/receipt plumbing for OpenMM operations. */
public final class OpenMmProcessExecutor {
    public static final Duration DEFAULT_EXECUTION_TIMEOUT = Duration.ofMinutes(30);
    private final Path pythonExecutable;
    private final Path runnerScript;
    private final Path receiptDirectory;
    private final ObjectMapper mapper = new ObjectMapper();
    private final Duration executionTimeout;

    public OpenMmProcessExecutor(Path pythonExecutable, Path runnerScript, Path receiptDirectory) {
        this(pythonExecutable, runnerScript, receiptDirectory, DEFAULT_EXECUTION_TIMEOUT);
    }

    public OpenMmProcessExecutor(Path pythonExecutable, Path runnerScript, Path receiptDirectory,
            Duration executionTimeout) {
        this.pythonExecutable = requireFile(pythonExecutable, "pythonExecutable");
        this.runnerScript = requireFile(runnerScript, "runnerScript");
        this.receiptDirectory = Objects.requireNonNull(receiptDirectory,
                "receiptDirectory").toAbsolutePath().normalize();
        this.executionTimeout = requirePositive(executionTimeout);
    }

    public Execution execute(ObjectNode request) throws EnergyEvaluationException {
        Process process = null;
        try {
            Files.createDirectories(receiptDirectory);
            String id = Instant.now().toEpochMilli() + "-" + UUID.randomUUID();
            Path requestPath = receiptDirectory.resolve(id + ".request.json");
            Path responsePath = receiptDirectory.resolve(id + ".response.json");
            Path outputPath = receiptDirectory.resolve(id + ".process.log");
            mapper.writerWithDefaultPrettyPrinter().writeValue(requestPath.toFile(), request);
            process = new ProcessBuilder(pythonExecutable.toString(), runnerScript.toString(),
                    requestPath.toString(), responsePath.toString()).redirectErrorStream(true)
                    .redirectOutput(outputPath.toFile()).start();
            if (!process.waitFor(executionTimeout.toMillis(), TimeUnit.MILLISECONDS)) {
                terminate(process);
                throw failure("OpenMM process timed out after " + executionTimeout, null);
            }
            String output = Files.readString(outputPath, StandardCharsets.UTF_8);
            int exit = process.exitValue();
            if (exit != 0) throw failure("OpenMM process failed (exit " + exit + "): " + output.strip(), null);
            JsonNode response = mapper.readTree(responsePath.toFile());
            return new Execution(response, requestPath, responsePath, Map.of(
                    "execution.request.sha256", OpenMmSystemManifestLoader.sha256(requestPath),
                    "execution.receipt.sha256", OpenMmSystemManifestLoader.sha256(responsePath),
                    "execution.receipt.path", responsePath.toString()));
        } catch (IOException exception) {
            throw failure("OpenMM I/O failure: " + exception.getMessage(), exception);
        } catch (InterruptedException exception) {
            if (process != null) terminateWithoutWaiting(process);
            Thread.currentThread().interrupt();
            throw failure("OpenMM process interrupted", exception);
        }
    }

    public ObjectMapper mapper() { return mapper; }

    private static Path requireFile(Path path, String name) {
        Path normalized = Objects.requireNonNull(path, name).toAbsolutePath().normalize();
        if (!Files.isRegularFile(normalized)) throw new IllegalArgumentException(name + " is not a file: " + normalized);
        return normalized;
    }

    private static Duration requirePositive(Duration timeout) {
        Objects.requireNonNull(timeout, "executionTimeout");
        if (timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("executionTimeout must be positive");
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

    private static EnergyEvaluationException failure(String message, Exception cause) {
        var exception = new EnergyEvaluationException(
                EnergyEvaluationException.FailureCode.ENGINE_UNAVAILABLE, message);
        if (cause != null) exception.initCause(cause);
        return exception;
    }

    public record Execution(JsonNode response, Path requestPath, Path responsePath,
            Map<String, String> receiptProvenance) {
        public Execution { receiptProvenance = Map.copyOf(receiptProvenance); }
    }
}
