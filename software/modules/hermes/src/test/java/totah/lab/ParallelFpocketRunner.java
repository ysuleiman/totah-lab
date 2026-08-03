package totah.lab;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.*;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;

public final class ParallelFpocketRunner {

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

    public void run(Path alphaFoldDirectory) throws IOException, InterruptedException {
        Files.createDirectories(outputDirectory);

        List<Path> inputs;

        try (Stream<Path> files = Files.walk(alphaFoldDirectory)) {
            inputs = files
                    .filter(Files::isRegularFile)
                    .filter(ParallelFpocketRunner::isPdbGzip)
                    .toList();
        }

        System.out.printf(
                "Found %,d AlphaFold structures; workers=%d%n",
                inputs.size(),
                workerCount
        );

        ExecutorService executor = Executors.newFixedThreadPool(workerCount);
        CompletionService<FpocketResult> completion =
                new ExecutorCompletionService<>(executor);

        AtomicInteger submitted = new AtomicInteger();

        for (Path input : inputs) {
            completion.submit(() -> process(input));
            submitted.incrementAndGet();
        }

        int succeeded = 0;
        int failed = 0;
        Instant start = Instant.now();

        try {
            for (int i = 0; i < submitted.get(); i++) {
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
                    System.err.println("Worker failed: " + e.getCause());
                }

                int completed = i + 1;

                if (completed % 100 == 0 || completed == inputs.size()) {
                    Duration elapsed = Duration.between(start, Instant.now());

                    System.out.printf(
                            "Completed %,d / %,d; success=%,d; failed=%,d; elapsed=%s%n",
                            completed,
                            inputs.size(),
                            succeeded,
                            failed,
                            elapsed
                    );
                }
            }
        } finally {
            executor.shutdownNow();
            executor.awaitTermination(30, TimeUnit.SECONDS);
        }
    }

    private FpocketResult process(Path compressedPdb) {
        String baseName = removeSuffix(
                compressedPdb.getFileName().toString(),
                ".pdb.gz"
        );

        Path workDirectory = null;

        try {
            workDirectory = Files.createTempDirectory(
                    outputDirectory,
                    baseName + "-"
            );

            Path temporaryPdb = workDirectory.resolve(baseName + ".pdb");
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

            boolean finished = process.waitFor(5, TimeUnit.MINUTES);

            if (!finished) {
                process.destroyForcibly();

                return new FpocketResult(
                        compressedPdb,
                        false,
                        "fpocket timed out"
                );
            }

            int exitCode = process.exitValue();

            Files.deleteIfExists(temporaryPdb);

            if (exitCode != 0) {
                return new FpocketResult(
                        compressedPdb,
                        false,
                        "fpocket exited with code " + exitCode
                );
            }

            Path generatedOutput =
                    workDirectory.resolve(baseName + "_out");

            if (!Files.isDirectory(generatedOutput)) {
                return new FpocketResult(
                        compressedPdb,
                        false,
                        "Expected output directory was not created"
                );
            }

            /*
             * Parse generatedOutput here.
             *
             * Recommended:
             * 1. Read <baseName>_info.txt.
             * 2. Store pocket descriptors in your database.
             * 3. Retain only pockets needed for later comparison.
             * 4. Delete large temporary files after ingestion.
             */

            return new FpocketResult(
                    compressedPdb,
                    true,
                    generatedOutput.toString()
            );

        } catch (Exception e) {
            return new FpocketResult(
                    compressedPdb,
                    false,
                    e.getMessage()
            );
        }
    }

    private static void decompress(Path source, Path destination)
            throws IOException {

        try (InputStream fileInput = Files.newInputStream(source);
             InputStream gzipInput = new GZIPInputStream(fileInput);
             OutputStream output = Files.newOutputStream(
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
                .endsWith(".pdb.gz");
    }

    private static String removeSuffix(String value, String suffix) {
        if (!value.endsWith(suffix)) {
            throw new IllegalArgumentException(
                    "Expected suffix " + suffix + ": " + value
            );
        }

        return value.substring(0, value.length() - suffix.length());
    }

    public record FpocketResult(
            Path source,
            boolean success,
            String message
    ) {
    }

    public static void main(String[] args)
            throws IOException, InterruptedException {

        Path alphaFoldDirectory =
                Path.of("/Users/yazan/artifacts/UP000005640_9606_HUMAN_v6");

        Path outputDirectory =
                Path.of("/Users/yazan/artifacts/UP000005640_9606_HUMAN_v6_pockets/fpocket-human");

        Path fpocketExecutable =
                Path.of("/opt/homebrew/Caskroom/miniforge/base/bin/fpocket");

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
