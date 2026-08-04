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
 * Command-line entry point for {@link AlphaSphereBackfillService}.
 * Gated by {@code totah.alpha-sphere-backfill.enabled=true} so it never
 * runs during normal startup or tests. Launch with
 * {@code --spring.main.web-application-type=none}:
 *
 * <pre>
 * --totah.alpha-sphere-backfill.enabled=true
 * [--totah.alpha-sphere-backfill.dry-run=false]
 * [--totah.alpha-sphere-backfill.structure-accession=AF-P51801-F1-model_v6]
 * </pre>
 *
 * Dry run ({@code totah.alpha-sphere-backfill.dry-run}, default
 * {@code true}) classifies every candidate without writing anything.
 * Each structure is backfilled in its own transaction; a failure rolls
 * back only that structure and the run continues.
 */
@Component
@ConditionalOnProperty(
        name = "totah.alpha-sphere-backfill.enabled",
        havingValue = "true"
)
public class AlphaSphereBackfillRunner implements CommandLineRunner {

    private static final Logger LOG =
            LoggerFactory.getLogger(AlphaSphereBackfillRunner.class);

    private final AlphaSphereBackfillService backfillService;
    private final String structureAccession;
    private final boolean dryRun;

    public AlphaSphereBackfillRunner(
            AlphaSphereBackfillService backfillService,
            @Value("${totah.alpha-sphere-backfill"
                    + ".structure-accession:}")
            String structureAccession,
            @Value("${totah.alpha-sphere-backfill.dry-run:true}")
            boolean dryRun
    ) {
        this.backfillService = Objects.requireNonNull(backfillService);
        this.structureAccession = structureAccession == null
                ? null
                : structureAccession.trim();
        this.dryRun = dryRun;
    }

    @Override
    public void run(String... args) {
        String filter = structureAccession == null
                || structureAccession.isBlank()
                ? null
                : structureAccession;

        List<Long> structureIds =
                backfillService.findStructureIdsMissingSpheres(filter);

        LOG.info("Alpha-sphere backfill{} starting: {} structures with "
                        + "sphere-less FPOCKET pockets{}",
                dryRun ? " DRY RUN (no database writes)" : "",
                structureIds.size(),
                filter == null ? "" : " (accession " + filter + ")");

        int structuresFailed = 0;
        int pocketsBackfilled = 0;
        int spheresInserted = 0;
        int alreadyHadSpheres = 0;

        for (long structureId : structureIds) {
            try {
                AlphaSphereBackfillService.StructureBackfillResult result =
                        dryRun
                                ? backfillService.previewStructure(
                                        structureId)
                                : backfillService.backfillStructure(
                                        structureId);

                pocketsBackfilled += result.pocketsBackfilled();
                spheresInserted += result.spheresInserted();
                alreadyHadSpheres += result.alreadyHadSpheres();

                LOG.info("Structure {}: pocketsBackfilled={}, "
                                + "spheresInserted={}, alreadyHadSpheres={}",
                        structureId,
                        result.pocketsBackfilled(),
                        result.spheresInserted(),
                        result.alreadyHadSpheres());
                result.missingVertFiles().forEach(path ->
                        LOG.warn("Structure {}: missing vert file {}",
                                structureId, path));
                result.unparseableVertFiles().forEach(path ->
                        LOG.warn("Structure {}: unparseable vert file {}",
                                structureId, path));
                result.ambiguousArtifacts().forEach(location ->
                        LOG.warn("Structure {}: ambiguous artifact {}",
                                structureId, location));
            } catch (Exception exception) {
                structuresFailed++;
                LOG.error("Structure {}: backfill failed, rolled back "
                                + "this structure only: {}",
                        structureId,
                        exception.getMessage(),
                        exception);
            }
        }

        LOG.info("Alpha-sphere backfill summary: structures={}, "
                        + "structuresFailed={}, pocketsBackfilled={}, "
                        + "spheresInserted={}, alreadyHadSpheres={}",
                structureIds.size(),
                structuresFailed,
                pocketsBackfilled,
                spheresInserted,
                alreadyHadSpheres);
    }
}
