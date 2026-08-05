package totah.lab.web.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

/**
 * Command-line entry point for {@link PocketShapeDescriptorService}'s
 * backfill: computes and persists the precomputed Stage 1 shape
 * descriptors of every FPOCKET pocket that has persisted alpha spheres
 * but no descriptor row yet. Gated by
 * {@code totah.shape-descriptor-backfill.enabled=true} so it never runs
 * during normal startup or tests. Launch with
 * {@code --spring.main.web-application-type=none}:
 *
 * <pre>
 * --totah.shape-descriptor-backfill.enabled=true
 * [--totah.shape-descriptor-backfill.dry-run=false]
 * </pre>
 *
 * Dry run ({@code totah.shape-descriptor-backfill.dry-run}, default
 * {@code true}) counts the candidates without writing anything. Each
 * structure is backfilled in its own transaction; a failure rolls back
 * only that structure and the run continues. Idempotent: re-running
 * finds no missing rows once every sphere-backed pocket is described.
 */
@Component
@ConditionalOnProperty(
        name = "totah.shape-descriptor-backfill.enabled",
        havingValue = "true"
)
public class ShapeDescriptorBackfillRunner implements CommandLineRunner {

    private static final Logger LOG =
            LoggerFactory.getLogger(ShapeDescriptorBackfillRunner.class);

    private final PocketShapeDescriptorService descriptorService;
    private final boolean dryRun;

    public ShapeDescriptorBackfillRunner(
            PocketShapeDescriptorService descriptorService,
            @Value("${totah.shape-descriptor-backfill.dry-run:true}")
            boolean dryRun
    ) {
        this.descriptorService =
                Objects.requireNonNull(descriptorService);
        this.dryRun = dryRun;
    }

    @Override
    public void run(String... args) {
        long startNanos = System.nanoTime();

        List<Long> structureIds =
                descriptorService.findStructureIdsMissingDescriptors();

        LOG.info("Shape-descriptor backfill{} starting: {} structures "
                        + "with un-described sphere-backed pockets",
                dryRun ? " DRY RUN (no database writes)" : "",
                structureIds.size());

        int structuresFailed = 0;
        int pocketsPending = 0;
        int descriptorsWritten = 0;

        for (long structureId : structureIds) {
            try {
                if (dryRun) {
                    int pending = descriptorService
                            .findPocketIdsMissingDescriptors(structureId)
                            .size();
                    pocketsPending += pending;
                    LOG.info("Structure {}: {} pockets pending",
                            structureId, pending);
                } else {
                    int written = descriptorService
                            .computeAndPersistForStructure(structureId);
                    descriptorsWritten += written;
                    LOG.info("Structure {}: {} descriptors written",
                            structureId, written);
                }
            } catch (Exception exception) {
                structuresFailed++;
                LOG.error("Structure {}: backfill failed, rolled back "
                                + "this structure only: {}",
                        structureId,
                        exception.getMessage(),
                        exception);
            }
        }

        long elapsedSeconds =
                (System.nanoTime() - startNanos) / 1_000_000_000L;

        LOG.info("Shape-descriptor backfill summary: structures={}, "
                        + "structuresFailed={}, pocketsPending={}, "
                        + "descriptorsWritten={}, elapsedSeconds={}",
                structureIds.size(),
                structuresFailed,
                pocketsPending,
                descriptorsWritten,
                elapsedSeconds);
    }
}
