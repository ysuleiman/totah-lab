package totah.lab.web.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import totah.lab.web.persistence.StructureRepository;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Bulk orchestration runner for {@link AlphaFoldPocketImportService} over
 * an AlphaFold proteome download.
 *
 * Gated by {@code totah.bulk-import.enabled=true}, so it never runs during
 * normal application startup or tests. Intended to be launched with
 * {@code --spring.main.web-application-type=none}.
 *
 * Dry run ({@code totah.bulk-import.dry-run}, default {@code true}) only
 * does filesystem pairing plus the same gzip/PDB/fpocket parse-validation
 * the importer performs — it never touches the database.
 *
 * A real run calls the injected {@link AlphaFoldPocketImportService} bean
 * once per structure, so every structure is imported through the Spring
 * proxy in its own transaction and failures stay isolated. The import is
 * idempotent, so nothing is tracked between runs: a rerun simply retries
 * everything, including structures whose previous import failed (their
 * earlier failure is only ever a log line, never persisted skip state).
 *
 * Exit code: after a real run the JVM exits with 1 when any structure
 * failed, 0 otherwise. A dry run never calls {@code System.exit}.
 */
@Component
@ConditionalOnProperty(
        name = "totah.bulk-import.enabled",
        havingValue = "true"
)
public class AlphaFoldBulkImportRunner implements CommandLineRunner {

    private static final Logger LOG =
            LoggerFactory.getLogger(AlphaFoldBulkImportRunner.class);

    private static final int PROGRESS_INTERVAL = 100;
    private static final int FAILURE_SAMPLE_SIZE = 10;
    private static final int MAX_DEFAULT_WORKERS = 8;

    private final AlphaFoldPocketImportService importService;
    private final StructureRepository structureRepository;
    private final Path pdbDirectory;
    private final List<Path> fpocketRoots;
    private final boolean dryRun;
    private final boolean skipExisting;
    private final int workers;

    public AlphaFoldBulkImportRunner(
            AlphaFoldPocketImportService importService,
            StructureRepository structureRepository,
            @Value("${totah.bulk-import.pdb-dir:"
                    + "/Users/yazan/artifacts"
                    + "/UP000005640_9606_HUMAN_v6}")
            Path pdbDirectory,
            @Value("${totah.bulk-import.fpocket-dirs:"
                    + "/Users/yazan/artifacts"
                    + "/UP000005640_9606_HUMAN_v6/fpocket-human"
                    + ",/Users/yazan/artifacts"
                    + "/UP000005640_9606_HUMAN_v6_pockets/fpocket-human}")
            String fpocketRootsValue,
            @Value("${totah.bulk-import.dry-run:true}") boolean dryRun,
            @Value("${totah.bulk-import.skip-existing:false}")
            boolean skipExisting,
            @Value("${totah.bulk-import.workers:0}") int workers
    ) {
        this.importService = Objects.requireNonNull(importService);
        this.structureRepository =
                Objects.requireNonNull(structureRepository);
        this.pdbDirectory = Objects.requireNonNull(pdbDirectory);
        this.fpocketRoots = List.copyOf(
                java.util.Arrays.stream(
                                Objects.requireNonNull(fpocketRootsValue)
                                        .split(","))
                        .map(String::trim)
                        .filter(value -> !value.isEmpty())
                        .map(Path::of)
                        .toList()
        );
        this.dryRun = dryRun;
        this.skipExisting = skipExisting;
        this.workers = workers;
    }

    @Override
    public void run(String... args) throws Exception {
        if (!Files.isDirectory(pdbDirectory)) {
            throw new IllegalStateException(
                    "PDB directory does not exist: " + pdbDirectory
            );
        }
        for (Path root : fpocketRoots) {
            if (!Files.isDirectory(root)) {
                LOG.warn("fpocket root does not exist, ignoring: {}", root);
            }
        }

        if (dryRun) {
            LOG.info("AlphaFold bulk import DRY RUN starting "
                    + "(no database writes)");
        } else {
            LOG.warn("AlphaFold bulk import starting — "
                    + "this writes to the database");
        }

        AlphaFoldBulkImportPlanner.Plan plan =
                AlphaFoldBulkImportPlanner.plan(
                        pdbDirectory,
                        fpocketRoots
                );

        for (Path incomplete : plan.incompleteRunDirectories()) {
            LOG.info("Skipping incomplete fpocket run directory: {}",
                    incomplete);
        }
        for (Path duplicate : plan.pairedInMultipleRoots()) {
            LOG.info("fpocket output found in both roots for {}, "
                    + "using first match", duplicate.getFileName());
        }
        for (Path missing : plan.missingFpocket()) {
            LOG.info("No fpocket output found, skipping: {}",
                    missing.getFileName());
        }

        int total = plan.pairs().size();
        int skipped = plan.missingFpocket().size();
        int threadCount = workers > 0
                ? workers
                : Math.min(
                        Math.max(
                                Runtime.getRuntime()
                                        .availableProcessors() - 1,
                                1
                        ),
                        MAX_DEFAULT_WORKERS
                );

        LOG.info("Processing {} paired structures with {} workers "
                        + "({} of {} pdb.gz skipped, no fpocket output)",
                total, threadCount, skipped, plan.totalPdbFiles());

        AtomicInteger completed = new AtomicInteger();
        AtomicInteger succeeded = new AtomicInteger();
        AtomicInteger skippedAlreadyImported = new AtomicInteger();
        Queue<Failure> failures = new ConcurrentLinkedQueue<>();
        Instant started = Instant.now();

        try (ExecutorService pool =
                     Executors.newFixedThreadPool(threadCount)) {
            for (AlphaFoldBulkImportPlanner.StructurePair pair
                    : plan.pairs()) {
                if (skipExisting && isAlreadyImported(pair)) {
                    skippedAlreadyImported.incrementAndGet();
                    continue;
                }
                pool.submit(() -> {
                    try {
                        if (dryRun) {
                            importService.parseAndValidate(
                                    pair.compressedPdb(),
                                    pair.fpocketOutDirectory()
                            );
                        } else {
                            importService.importStructure(
                                    pair.compressedPdb(),
                                    pair.fpocketOutDirectory()
                            );
                        }
                        succeeded.incrementAndGet();
                    } catch (Exception exception) {
                        failures.add(new Failure(
                                pair.compressedPdb(),
                                failureMessage(exception)
                        ));
                        LOG.warn("{} failed for {}: {}",
                                dryRun ? "Validation" : "Import",
                                pair.compressedPdb().getFileName(),
                                failureMessage(exception));
                    } finally {
                        int done = completed.incrementAndGet();
                        if (done % PROGRESS_INTERVAL == 0) {
                            LOG.info("Progress {}/{} succeeded={} "
                                            + "failed={} skipped={} "
                                            + "elapsed={}",
                                    done,
                                    total,
                                    succeeded.get(),
                                    failures.size(),
                                    skipped,
                                    Duration.between(
                                            started,
                                            Instant.now()
                                    ));
                        }
                    }
                });
            }
        }

        if (dryRun) {
            LOG.info("Dry run summary: total pdb.gz={}, paired={}, "
                            + "missing fpocket={}, "
                            + "incomplete fpocket run directories={}, "
                            + "parse failures={}, elapsed={}",
                    plan.totalPdbFiles(),
                    total,
                    skipped,
                    plan.incompleteRunDirectories().size(),
                    failures.size(),
                    Duration.between(started, Instant.now()));
        } else {
            LOG.info("Bulk import summary: processed={}, succeeded={}, "
                            + "failed={}, skipped={}, "
                            + "skippedAlreadyImported={}, elapsed={}",
                    total,
                    succeeded.get(),
                    failures.size(),
                    skipped,
                    skippedAlreadyImported.get(),
                    Duration.between(started, Instant.now()));
        }

        failures.stream()
                .limit(FAILURE_SAMPLE_SIZE)
                .forEach(failure -> LOG.warn("Failure: {} — {}",
                        failure.path(),
                        failure.message()));
        if (failures.size() > FAILURE_SAMPLE_SIZE) {
            LOG.warn("... and {} more failures",
                    failures.size() - FAILURE_SAMPLE_SIZE);
        }

        if (!dryRun) {
            System.exit(failures.isEmpty() ? 0 : 1);
        }
    }

    private static String failureMessage(Exception exception) {
        return exception.getMessage() == null
                ? exception.getClass().getSimpleName()
                : exception.getMessage();
    }

    /*
     * A structure counts as already imported when its row exists — the
     * import is transactional per structure, so an existing row always
     * represents a complete import (a mid-import kill rolls back).
     */
    private boolean isAlreadyImported(
            AlphaFoldBulkImportPlanner.StructurePair pair
    ) {
        String filename = pair.compressedPdb().getFileName().toString();
        String structureAccession = filename.substring(
                0,
                filename.length()
                        - AlphaFoldBulkImportPlanner.PDB_SUFFIX.length()
        );
        return structureRepository
                .findBySourceAndSourceAccession(
                        AlphaFoldPocketImportService.STRUCTURE_SOURCE,
                        structureAccession
                )
                .isPresent();
    }

    private record Failure(
            Path path,
            String message
    ) {
    }
}
