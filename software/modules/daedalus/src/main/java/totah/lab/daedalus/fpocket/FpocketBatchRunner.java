package totah.lab.daedalus.fpocket;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/** Runs fpocket reproducibly over RCSB biological-assembly mmCIF files. */
public final class FpocketBatchRunner {

    private static final Pattern INPUT_NAME = Pattern.compile(
            "^([0-9][A-Za-z0-9]{3})-assembly([1-9][0-9]*)\\.cif$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern POCKET_FILE = Pattern.compile(
            "^pocket([1-9][0-9]*)_atm\\.cif$");

    private final Path executable;
    private final Path cohortRoot;
    private final Path outputRoot;
    private final int workers;
    private final Duration timeout;
    private final boolean force;
    private final String version;

    public FpocketBatchRunner(Path executable, Path cohortRoot,
            Path outputRoot, int workers, Duration timeout, boolean force)
            throws IOException, InterruptedException {
        this.executable = Objects.requireNonNull(executable).toAbsolutePath();
        this.cohortRoot = Objects.requireNonNull(cohortRoot).toAbsolutePath();
        this.outputRoot = Objects.requireNonNull(outputRoot).toAbsolutePath();
        if (workers < 1) {
            throw new IllegalArgumentException("workers must be positive");
        }
        this.workers = workers;
        this.timeout = Objects.requireNonNull(timeout);
        this.force = force;
        this.version = detectVersion(executable);
    }

    public BatchSummary run() throws IOException, InterruptedException {
        Files.createDirectories(outputRoot);
        List<AssemblyInput> inputs = discoverInputs();
        ExecutorService executor = Executors.newFixedThreadPool(workers);
        List<Future<Result>> futures = new ArrayList<>(inputs.size());
        try {
            for (AssemblyInput input : inputs) {
                futures.add(executor.submit((Callable<Result>) () -> process(input)));
            }
            List<Result> results = new ArrayList<>(inputs.size());
            for (Future<Result> future : futures) {
                try {
                    results.add(future.get());
                } catch (java.util.concurrent.ExecutionException e) {
                    throw new IOException("fpocket worker failed", e.getCause());
                }
            }
            results.sort((a, b) -> a.pdbId().compareTo(b.pdbId()));
            writeManifest(results);
            return BatchSummary.from(results, version, outputRoot);
        } finally {
            executor.shutdown();
            if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        }
    }

    private List<AssemblyInput> discoverInputs() throws IOException {
        try (Stream<Path> files = Files.list(cohortRoot)) {
            return files.filter(Files::isRegularFile)
                    .map(this::toInput)
                    .filter(Objects::nonNull)
                    .sorted((a, b) -> a.pdbId().compareTo(b.pdbId()))
                    .toList();
        }
    }

    private AssemblyInput toInput(Path source) {
        Matcher matcher = INPUT_NAME.matcher(source.getFileName().toString());
        if (!matcher.matches()) {
            return null;
        }
        return new AssemblyInput(matcher.group(1).toUpperCase(Locale.ROOT),
                Integer.parseInt(matcher.group(2)), source.toAbsolutePath());
    }

    private Result process(AssemblyInput input) {
        Instant started = Instant.now();
        String base = input.pdbId() + "-assembly" + input.assemblyId();
        Path entryDirectory = outputRoot.resolve(base);
        Path resultDirectory = entryDirectory.resolve(base + "_out");
        Path log = entryDirectory.resolve("fpocket.log");
        Path staging = entryDirectory.resolve(".staging");
        try {
            String hash = sha256(input.source());
            if (!force && validOutput(resultDirectory, base)) {
                return result(input, hash, "SKIPPED", countPockets(resultDirectory),
                        entryDirectory, started, Instant.now(), 0, "");
            }
            deleteTree(staging);
            Files.createDirectories(staging);
            Path executionInput = staging.resolve(base + ".cif");
            Files.copy(input.source(), executionInput,
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.COPY_ATTRIBUTES);
            ProcessBuilder builder = new ProcessBuilder(executable.toString(),
                    "-f", executionInput.toString());
            builder.directory(staging.toFile());
            builder.redirectErrorStream(true);
            builder.redirectOutput(log.toFile());
            Process process = builder.start();
            if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                process.destroyForcibly();
                process.waitFor(30, TimeUnit.SECONDS);
                return result(input, hash, "FAILED", 0, entryDirectory,
                        started, Instant.now(), -1, "fpocket timed out");
            }
            int exit = process.exitValue();
            Path generated = staging.resolve(base + "_out");
            if (exit != 0 || !validOutput(generated, base)) {
                return result(input, hash, "FAILED", 0, entryDirectory,
                        started, Instant.now(), exit,
                        exit == 0 ? "fpocket output failed validation"
                                : "fpocket exited with code " + exit);
            }
            deleteTree(resultDirectory);
            Files.move(generated, resultDirectory,
                    StandardCopyOption.ATOMIC_MOVE);
            int pockets = countPockets(resultDirectory);
            deleteTree(staging);
            Result result = result(input, hash, "SUCCESS", pockets,
                    entryDirectory, started, Instant.now(), exit, "");
            writeAtomically(entryDirectory.resolve("provenance.json"),
                    result.toJson() + System.lineSeparator());
            return result;
        } catch (Exception e) {
            return result(input, "", "FAILED", 0, entryDirectory, started,
                    Instant.now(), -1, e.getMessage() == null
                            ? e.getClass().getName() : e.getMessage());
        }
    }

    private Result result(AssemblyInput input, String hash, String status,
            int pockets, Path directory, Instant started, Instant completed,
            int exitCode, String error) {
        return new Result(input.pdbId(), input.assemblyId(), input.source(),
                input.source().getFileName().toString(), hash, version,
                List.of(executable.toString(), "-f", input.source().toString()),
                "DIRECT_MMCIF", status, pockets, directory, started, completed,
                exitCode, error);
    }

    static boolean validOutput(Path directory, String base) throws IOException {
        Path info = directory.resolve(base + "_info.txt");
        Path pockets = directory.resolve("pockets");
        if (!Files.isRegularFile(info) || Files.size(info) == 0
                || !Files.isDirectory(pockets)) {
            return false;
        }
        try (Stream<Path> files = Files.list(pockets)) {
            for (Path atomFile : files.filter(Files::isRegularFile)
                    .filter(path -> POCKET_FILE.matcher(
                            path.getFileName().toString()).matches()).toList()) {
                String number = atomFile.getFileName().toString()
                        .replace("pocket", "").replace("_atm.cif", "");
                Path vertices = pockets.resolve("pocket" + number + "_vert.pqr");
                if (Files.size(atomFile) == 0 || !Files.isRegularFile(vertices)
                        || Files.size(vertices) == 0) {
                    return false;
                }
            }
        }
        return true;
    }

    static int countPockets(Path directory) throws IOException {
        Path pockets = directory.resolve("pockets");
        if (!Files.isDirectory(pockets)) {
            return 0;
        }
        try (Stream<Path> files = Files.list(pockets)) {
            return (int) files.filter(Files::isRegularFile)
                    .filter(path -> POCKET_FILE.matcher(
                            path.getFileName().toString()).matches())
                    .count();
        }
    }

    private void writeManifest(List<Result> results) throws IOException {
        StringBuilder jsonl = new StringBuilder();
        for (Result result : results) {
            jsonl.append(result.toJson()).append(System.lineSeparator());
        }
        writeAtomically(outputRoot.resolve("manifest.jsonl"), jsonl.toString());
    }

    private static void writeAtomically(Path destination, String content)
            throws IOException {
        Files.createDirectories(destination.getParent());
        Path temporary = destination.resolveSibling(destination.getFileName()
                + ".tmp");
        Files.writeString(temporary, content, StandardCharsets.UTF_8);
        Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE);
    }

    private static String sha256(Path path) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (var input = Files.newInputStream(path)) {
                byte[] buffer = new byte[64 * 1024];
                for (int read; (read = input.read(buffer)) >= 0;) {
                    digest.update(buffer, 0, read);
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private static String detectVersion(Path executable)
            throws IOException, InterruptedException {
        Process process = new ProcessBuilder(executable.toString())
                .redirectErrorStream(true).start();
        String output;
        try (var stream = process.getInputStream()) {
            output = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
        process.waitFor(30, TimeUnit.SECONDS);
        String plainOutput = output.replaceAll("\\u001B\\[[;\\d]*m", "");
        Matcher matcher = Pattern.compile("fpocket\\s+([0-9]+(?:\\.[0-9]+)*)",
                Pattern.CASE_INSENSITIVE).matcher(plainOutput);
        return matcher.find() ? matcher.group(1) : "UNKNOWN";
    }

    private static void deleteTree(Path root) throws IOException {
        if (!Files.exists(root)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(root)) {
            for (Path path : paths.sorted((a, b) -> b.compareTo(a)).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }

    private record AssemblyInput(String pdbId, int assemblyId, Path source) {}

    public record Result(String pdbId, int assemblyId, Path sourceMmcif,
            String sourceFilename, String inputSha256, String fpocketVersion,
            List<String> command, String inputMode, String status,
            int pocketCount, Path outputDirectory, Instant startedAt,
            Instant completedAt, int exitCode, String error) {
        String toJson() {
            return "{\"pdb_id\":" + quote(pdbId)
                    + ",\"assembly_id\":" + assemblyId
                    + ",\"source_mmcif\":" + quote(sourceMmcif.toString())
                    + ",\"source_filename\":" + quote(sourceFilename)
                    + ",\"input_sha256\":" + quote(inputSha256)
                    + ",\"fpocket_version\":" + quote(fpocketVersion)
                    + ",\"fpocket_command\":" + quote(String.join(" ", command))
                    + ",\"input_mode\":" + quote(inputMode)
                    + ",\"status\":" + quote(status)
                    + ",\"pocket_count\":" + pocketCount
                    + ",\"output_directory\":" + quote(outputDirectory.toString())
                    + ",\"started_at\":" + quote(startedAt.toString())
                    + ",\"completed_at\":" + quote(completedAt.toString())
                    + ",\"exit_code\":" + exitCode
                    + ",\"error\":" + quote(error) + "}";
        }
        private static String quote(String value) {
            return "\"" + value.replace("\\", "\\\\")
                    .replace("\"", "\\\"").replace("\n", "\\n")
                    .replace("\r", "\\r") + "\"";
        }
    }

    public record BatchSummary(int attempted, int successful, int failed,
            int skipped, int totalPockets, String fpocketVersion,
            Path outputRoot) {
        static BatchSummary from(List<Result> results, String version,
                Path outputRoot) {
            int successful = (int) results.stream()
                    .filter(r -> r.status().equals("SUCCESS")).count();
            int failed = (int) results.stream()
                    .filter(r -> r.status().equals("FAILED")).count();
            int skipped = results.size() - successful - failed;
            int pockets = results.stream().mapToInt(Result::pocketCount).sum();
            return new BatchSummary(results.size(), successful, failed, skipped,
                    pockets, version, outputRoot);
        }
    }
}
