package totah.lab;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;

public final class ParallelFpocketRunner {

    private static final String PDB_GZIP_SUFFIX = ".pdb.gz";

    private final Path fpocketExecutable;
    private final Path outputDirectory;
    private final int workerCount;

    public ParallelFpocketRunner(
            Path fpocketExecutable,
            Path outputDirectory,
            int workerCount
    ) {
        this.fpocketExecutable = fpocketExecutable;
        this.outputDirectory = outputDirectory;
        this.workerCount = workerCount;
    }

    public void run(Path alphaFoldDirectory)
            throws IOException, InterruptedException {

        Files.createDirectories(outputDirectory);

        List<Path> inputs;

        try (Stream<Path> files = Files.walk(alphaFoldDirectory)) {
            inputs = files
                    .filter(Files::isRegularFile)
                    .filter(ParallelFpocketRunner::isPdbGzip)
                    .sorted()
                    .toList();
        }

        System.out.printf(
                "Found %,d AlphaFold structures; workers=%d%n",
                inputs.size(),
                workerCount
        );

        ExecutorService executor =
                Executors.newFixedThreadPool(workerCount);

        CompletionService<FpocketResult> completion =
                new ExecutorCompletionService<>(executor);

        int submitted = 0;
        int skipped = 0;

        try {
            for (Path input : inputs) {
                String baseName = removeSuffix(
                        input.getFileName().toString(),
                        PDB_GZIP_SUFFIX
                );

                if (hasCompletedOutput(baseName)) {
                    skipped++;

                    System.out.printf(
                            "SKIP completed: %s%n",
                            baseName
                    );

                    continue;
                }

                completion.submit(() -> process(input));
                submitted++;
            }

            System.out.printf(
                    "Submitted %,d; skipped %,d completed structures%n",
                    submitted,
                    skipped
            );

            if (submitted == 0) {
                System.out.println("All structures are already complete.");
                return;
            }

            int succeeded = 0;
            int failed = 0;
            Instant start = Instant.now();

            for (int i = 0; i < submitted; i++) {
                Future<FpocketResult> future = completion.take();

                try {
                    FpocketResult result = future.get();

                    if (result.success()) {
                        succeeded++;
                    } else {
                        failed++;

                        System.err.printf(
                                "FAILED %s: %s%n",
                                result.source().getFileName(),
                                result.message()
                        );
                    }

                } catch (ExecutionException e) {
                    failed++;

                    Throwable cause = e.getCause();

                    System.err.println(
                            "Worker failed: "
                                    + (cause == null ? e : cause)
                    );
                }

                int completed = i + 1;

                if (completed % 100 == 0 || completed == submitted) {
                    Duration elapsed =
                            Duration.between(start, Instant.now());

                    System.out.printf(
                            "Completed %,d / %,d; "
                                    + "success=%,d; failed=%,d; "
                                    + "skipped=%,d; elapsed=%s%n",
                            completed,
                            submitted,
                            succeeded,
                            failed,
                            skipped,
                            elapsed
                    );
                }
            }

        } finally {
            executor.shutdown();

            if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
                executor.shutdownNow();
                executor.awaitTermination(30, TimeUnit.SECONDS);
            }
        }
    }

    private FpocketResult process(Path compressedPdb) {
        String baseName = removeSuffix(
                compressedPdb.getFileName().toString(),
                PDB_GZIP_SUFFIX
        );

        Path workDirectory = null;

        try {
            /*
             * Files.createTempDirectory appends a random unique suffix:
             *
             * AF-P51801-F1-model_v6-123456789...
             */
            workDirectory = Files.createTempDirectory(
                    outputDirectory,
                    baseName + "-"
            );

            Path temporaryPdb =
                    workDirectory.resolve(baseName + ".pdb");

            decompress(compressedPdb, temporaryPdb);

            ProcessBuilder processBuilder = new ProcessBuilder(
                    fpocketExecutable.toString(),
                    "-f",
                    temporaryPdb.toString()
            );

            processBuilder.directory(workDirectory.toFile());
            processBuilder.redirectErrorStream(true);
            processBuilder.redirectOutput(
                    workDirectory.resolve("fpocket.log").toFile()
            );

            Process process = processBuilder.start();

            boolean finished =
                    process.waitFor(5, TimeUnit.MINUTES);

            if (!finished) {
                process.destroyForcibly();
                process.waitFor(30, TimeUnit.SECONDS);

                return new FpocketResult(
                        compressedPdb,
                        false,
                        "fpocket timed out; work directory: "
                                + workDirectory
                );
            }

            int exitCode = process.exitValue();

            Files.deleteIfExists(temporaryPdb);

            if (exitCode != 0) {
                return new FpocketResult(
                        compressedPdb,
                        false,
                        "fpocket exited with code "
                                + exitCode
                                + "; see "
                                + workDirectory.resolve("fpocket.log")
                );
            }

            Path generatedOutput =
                    workDirectory.resolve(baseName + "_out");

            if (!isCompletedFpocketOutput(
                    generatedOutput,
                    baseName
            )) {
                return new FpocketResult(
                        compressedPdb,
                        false,
                        "fpocket output is incomplete: "
                                + generatedOutput
                );
            }

            return new FpocketResult(
                    compressedPdb,
                    true,
                    generatedOutput.toString()
            );

        } catch (Exception e) {
            return new FpocketResult(
                    compressedPdb,
                    false,
                    e.getMessage() == null
                            ? e.getClass().getName()
                            : e.getMessage()
            );
        }
    }

    /**
     * Finds any prior timestamped/randomized work directory for this
     * structure and checks whether it contains a complete fpocket result.
     */
    private boolean hasCompletedOutput(String baseName)
            throws IOException {

        if (!Files.isDirectory(outputDirectory)) {
            return false;
        }

        String workDirectoryPrefix = baseName + "-";

        try (Stream<Path> directories =
                     Files.list(outputDirectory)) {

            return directories
                    .filter(Files::isDirectory)
                    .filter(directory ->
                            directory.getFileName()
                                    .toString()
                                    .startsWith(workDirectoryPrefix)
                    )
                    .anyMatch(directory -> {
                        Path generatedOutput =
                                directory.resolve(baseName + "_out");

                        return isCompletedFpocketOutput(
                                generatedOutput,
                                baseName
                        );
                    });
        }
    }

    /**
     * Directory existence alone is not enough because a failed run may
     * leave a partial directory behind.
     */
    private static boolean isCompletedFpocketOutput(
            Path generatedOutput,
            String baseName
    ) {
        if (!Files.isDirectory(generatedOutput)) {
            return false;
        }

        Path infoFile =
                generatedOutput.resolve(baseName + "_info.txt");

        Path pocketsDirectory =
                generatedOutput.resolve("pockets");

        return Files.isRegularFile(infoFile)
                && Files.isDirectory(pocketsDirectory);
    }

    private static void decompress(
            Path source,
            Path destination
    ) throws IOException {

        try (InputStream fileInput =
                     Files.newInputStream(source);
             InputStream gzipInput =
                     new GZIPInputStream(fileInput);
             OutputStream output =
                     Files.newOutputStream(
                             destination,
                             StandardOpenOption.CREATE,
                             StandardOpenOption.TRUNCATE_EXISTING
                     )) {

            gzipInput.transferTo(output);
        }
    }

    private static boolean isPdbGzip(Path path) {
        return path.getFileName()
                .toString()
                .toLowerCase(Locale.ROOT)
                .endsWith(PDB_GZIP_SUFFIX);
    }

    private static String removeSuffix(
            String value,
            String suffix
    ) {
        if (!value.endsWith(suffix)) {
            throw new IllegalArgumentException(
                    "Expected suffix " + suffix + ": " + value
            );
        }

        return value.substring(
                0,
                value.length() - suffix.length()
        );
    }

    public record FpocketResult(
            Path source,
            boolean success,
            String message
    ) {
    }

    public static void main(String[] args)
            throws IOException, InterruptedException {

        Path alphaFoldDirectory = Path.of(
                "/Users/yazan/artifacts/"
                        + "UP000005640_9606_HUMAN_v6"
        );

        Path outputDirectory = Path.of(
                "/Users/yazan/artifacts/"
                        + "UP000005640_9606_HUMAN_v6_pockets/"
                        + "fpocket-human"
        );

        Path fpocketExecutable = Path.of(
                "/opt/homebrew/Caskroom/miniforge/base/bin/fpocket"
        );

        int availableProcessors =
                Runtime.getRuntime().availableProcessors();

        int workers = Math.max(
                1,
                Math.min(availableProcessors - 1, 8)
        );

        new ParallelFpocketRunner(
                fpocketExecutable,
                outputDirectory,
                workers
        ).run(alphaFoldDirectory);
    }
}