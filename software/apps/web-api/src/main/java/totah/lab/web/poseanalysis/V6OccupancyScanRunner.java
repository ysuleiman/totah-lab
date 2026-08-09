package totah.lab.web.poseanalysis;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Gated one-shot batch runner scanning every ranked DiffDock pose of
 * an output directory (default: the canonical METTL7B v6 run) and
 * reporting the pocket each rank's pose occupies. Launched with
 * {@code --spring.main.web-application-type=none
 * --totah.v6-occupancy-scan.enabled=true} plus:
 * <ul>
 *   <li>{@code totah.v6-occupancy-scan.dir} — the DiffDock output
 *       directory (default the canonical METTL7B v6 directory);</li>
 *   <li>{@code totah.v6-occupancy-scan.ranks} — number of ranks to
 *       scan (default 20);</li>
 *   <li>{@code totah.v6-occupancy-scan.homologous-pocket-id} — DB id
 *       of the homologous-site pocket (default 3 = the imported
 *       canonical-v6 197-sphere SAM superpocket; FPOCKET pocket 1 in
 *       that run);</li>
 *   <li>{@code totah.v6-occupancy-scan.dry-run} — default true;
 *       honored as a log annotation (read-only).</li>
 * </ul>
 */
@Component
@ConditionalOnProperty(
        name = "totah.v6-occupancy-scan.enabled",
        havingValue = "true"
)
public class V6OccupancyScanRunner implements CommandLineRunner {

    private static final Logger LOG =
            LoggerFactory.getLogger(V6OccupancyScanRunner.class);

    private static final String DEFAULT_DIR =
            "/Users/yazan/totah-lab/experiments/METTL7B-v6_diffdock";

    private final V6OccupancyScanService scanService;
    private final String directory;
    private final int ranks;
    private final long homologousPocketId;
    private final boolean dryRun;

    public V6OccupancyScanRunner(
            V6OccupancyScanService scanService,
            @Value("${totah.v6-occupancy-scan.dir:" + DEFAULT_DIR + "}")
            String directory,
            @Value("${totah.v6-occupancy-scan.ranks:20}")
            int ranks,
            @Value("${totah.v6-occupancy-scan.homologous-pocket-id:3}")
            long homologousPocketId,
            @Value("${totah.v6-occupancy-scan.dry-run:true}")
            boolean dryRun
    ) {
        this.scanService =
                Objects.requireNonNull(scanService, "scanService");
        this.directory = directory;
        this.ranks = ranks;
        this.homologousPocketId = homologousPocketId;
        this.dryRun = dryRun;
    }

    @Override
    public void run(String... args) {
        LOG.info("V6 occupancy scan{}: dir={}, ranks={}, homologous"
                        + " pocket id={}",
                dryRun ? " (dry-run — read-only)" : "",
                directory, ranks, homologousPocketId);

        System.out.println(scanService.report(
                Path.of(directory.trim()),
                ranks,
                homologousPocketId
        ));
    }
}
