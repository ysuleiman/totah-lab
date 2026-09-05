package totah.lab.mettl7.campaign.v2;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import totah.lab.daedalus.docking.DockingInput;
import totah.lab.daedalus.docking.PocketGridBox;
import totah.lab.daedalus.docking.VinaDockingOptions;
import totah.lab.daedalus.docking.VinaDockingRunner;
import totah.lab.daedalus.docking.VinaExecutionOptions;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.Executors;

/** Resumable, deterministic Java executor for the frozen V2 Vina ledger. */
public final class Mettl7V2DockingCampaignRunner {
    private final ObjectMapper mapper = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);

    public static void main(String[] args) throws Exception {
        if (args.length != 7) {
            throw new IllegalArgumentException("Usage: <repo-root> <vina> "
                    + "<receptor-manifest> <ligand-manifest> <output-root> "
                    + "<workers> <cpu-per-job>");
        }
        new Mettl7V2DockingCampaignRunner().run(
                Path.of(args[0]), Path.of(args[1]), Path.of(args[2]),
                Path.of(args[3]), Path.of(args[4]), Integer.parseInt(args[5]),
                Integer.parseInt(args[6]));
    }

    public Summary run(Path repositoryRoot, Path vina, Path receptorManifest,
            Path ligandManifest, Path outputRoot, int workers, int cpuPerJob)
            throws Exception {
        if (workers < 1 || cpuPerJob < 1
                || workers * cpuPerJob > Runtime.getRuntime().availableProcessors()) {
            throw new IllegalArgumentException("Invalid/oversubscribed CPU allocation");
        }
        Path root = repositoryRoot.toAbsolutePath().normalize();
        Path output = outputRoot.toAbsolutePath().normalize();
        Files.createDirectories(output.resolve("runs"));
        var plan = new Mettl7CartesianLedgerGenerator()
                .plan(receptorManifest, ligandManifest);
        if (!plan.ready()) {
            throw new IOException("Ledger is not ready: " + plan.blockers());
        }
        List<Mettl7CartesianLedgerGenerator.Row> executable = plan.rows().stream()
                .filter(row -> row.receptor().integrityStatus().equals("VALID"))
                .sorted(Comparator.comparing(Mettl7CartesianLedgerGenerator.Row::runId))
                .toList();
        List<Mettl7CartesianLedgerGenerator.Row> pending = executable.stream()
                .filter(row -> !completedValid(output, row.runId())).toList();
        Instant started = Instant.now();
        int completedThisInvocation = 0;
        int failedThisInvocation = 0;
        try (var executor = Executors.newFixedThreadPool(workers)) {
            var completion = new ExecutorCompletionService<RunReceipt>(executor);
            for (var row : pending) completion.submit(task(
                    root, vina, output, row, cpuPerJob));
            for (int index = 0; index < pending.size(); index++) {
                RunReceipt receipt = completion.take().get();
                if (receipt.status().equals("COMPLETED_VALID")) {
                    completedThisInvocation++;
                } else {
                    failedThisInvocation++;
                }
                if ((index + 1) % 25 == 0 || index + 1 == pending.size()) {
                    System.out.printf("PROGRESS completed_this_run=%d failed_this_run=%d remaining=%d%n",
                            completedThisInvocation, failedThisInvocation,
                            pending.size() - index - 1);
                }
            }
        }
        int valid = 0;
        int failed = 0;
        for (var row : executable) {
            if (completedValid(output, row.runId())) valid++; else failed++;
        }
        Summary summary = new Summary(plan.rows().size(), executable.size(),
                plan.rows().size() - executable.size(), valid, failed,
                executable.size() - valid - failed, workers, cpuPerJob,
                Duration.between(started, Instant.now()).toMillis() / 1000.0);
        writeAtomic(output.resolve("production_summary.json"), summary);
        return summary;
    }

    private Callable<RunReceipt> task(Path root, Path vina, Path output,
            Mettl7CartesianLedgerGenerator.Row row, int cpuPerJob) {
        return () -> execute(root, vina, output, row, cpuPerJob);
    }

    private RunReceipt execute(Path root, Path vina, Path output,
            Mettl7CartesianLedgerGenerator.Row row, int cpuPerJob) {
        Instant started = Instant.now();
        Path directory = output.resolve("runs").resolve(row.runId());
        Path poses = directory.resolve("poses.pdbqt");
        try {
            Files.createDirectories(directory);
            Path receptor = resolve(root, row.receptor().path());
            Path ligand = resolve(root, row.species().path());
            requireHash(receptor, row.receptor().sha256());
            requireHash(ligand, row.species().sha256());
            PocketGridBox box = row.receptor().paralog().equals("METTL7A")
                    ? Mettl7NativeDockingWindows.mettl7a()
                    : Mettl7NativeDockingWindows.mettl7b();
            VinaDockingOptions options = new VinaDockingOptions(
                    box.center().x(), box.center().y(), box.center().z(),
                    box.size().x(), box.size().y(), box.size().z(),
                    Mettl7MechanisticMatrixV2Protocol.EXHAUSTIVENESS, row.seed());
            var result = new VinaDockingRunner(vina).run(
                    new DockingInput(receptor, ligand, Optional.empty()), options,
                    Mettl7MechanisticMatrixV2Protocol.poseOutputOptions(),
                    new VinaExecutionOptions(cpuPerJob), poses);
            Files.writeString(directory.resolve("vina.log"), result.output());
            String status = result.exitCode() == 0 && Files.isRegularFile(poses)
                    && Files.size(poses) > 0 && !result.poses().isEmpty()
                    ? "COMPLETED_VALID" : "COMPLETED_INVALID";
            RunReceipt receipt = new RunReceipt(row.runId(), status,
                    result.exitCode(), result.poses().size(), receptor.toString(),
                    row.receptor().sha256(), ligand.toString(), row.species().sha256(),
                    Files.isRegularFile(poses) ? sha256(poses) : "",
                    row.seed(), cpuPerJob,
                    Duration.between(started, Instant.now()).toMillis() / 1000.0,
                    "");
            writeAtomic(directory.resolve("receipt.json"), receipt);
            return receipt;
        } catch (Exception exception) {
            RunReceipt receipt = new RunReceipt(row.runId(), "TECHNICAL_FAILURE",
                    -1, 0, row.receptor().path(), row.receptor().sha256(),
                    row.species().path(), row.species().sha256(), "", row.seed(),
                    cpuPerJob, Duration.between(started, Instant.now()).toMillis() / 1000.0,
                    exception.getMessage() == null
                            ? exception.getClass().getName() : exception.getMessage());
            try {
                writeAtomic(directory.resolve("receipt.json"), receipt);
            } catch (IOException ignored) {
                // The returned failure still reaches the campaign summary.
            }
            return receipt;
        }
    }

    private boolean completedValid(Path output, String runId) {
        Path receipt = output.resolve("runs").resolve(runId).resolve("receipt.json");
        if (!Files.isRegularFile(receipt)) return false;
        try {
            return "COMPLETED_VALID".equals(
                    mapper.readTree(receipt.toFile()).path("status").asText());
        } catch (IOException exception) {
            return false;
        }
    }

    private void writeAtomic(Path target, Object value) throws IOException {
        Files.createDirectories(target.toAbsolutePath().normalize().getParent());
        Path staging = target.resolveSibling(target.getFileName() + ".tmp");
        mapper.writeValue(staging.toFile(), value);
        Files.move(staging, target, StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE);
    }

    private static Path resolve(Path root, String value) {
        Path path = Path.of(value);
        return (path.isAbsolute() ? path : root.resolve(path))
                .toAbsolutePath().normalize();
    }

    private static void requireHash(Path path, String expected) throws IOException {
        String actual = sha256(path);
        if (!actual.equals(expected)) {
            throw new IOException("SHA-256 mismatch for " + path);
        }
    }

    private static String sha256(Path path) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (var input = Files.newInputStream(path)) {
                byte[] buffer = new byte[8192];
                for (int read; (read = input.read(buffer)) >= 0;) {
                    if (read > 0) digest.update(buffer, 0, read);
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    public record RunReceipt(String runId, String status, int vinaExitCode,
            int parsedPoseCount, String receptorPath, String receptorSha256,
            String ligandPath, String ligandSha256, String posesSha256,
            int seed, int cpuPerJob, double elapsedSeconds, String error) {}

    public record Summary(int authoritativeExpectedRows, int executableRuns,
            int predeclaredTechnicalFailureRows, int completedValid, int failed,
            int remaining, int workers, int cpuPerJob, double invocationWallSeconds) {}
}
