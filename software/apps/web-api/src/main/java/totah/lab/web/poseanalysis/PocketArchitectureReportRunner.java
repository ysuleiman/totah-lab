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
 * Gated one-shot batch runner printing the pocket-architecture
 * comparison of two docking poses (each pose's assigned FPOCKET
 * pocket, compared geometrically: alpha-sphere architecture, backbone
 * displacement, ligand space, wall geometry). Launched with
 * {@code --spring.main.web-application-type=none
 * --totah.pocket-architecture-report.enabled=true} plus:
 * <ul>
 *   <li>{@code totah.pocket-architecture-report.pose-a} — side A pose
 *       id (default 1157340);</li>
 *   <li>{@code totah.pocket-architecture-report.pose-b} — side B pose
 *       id (default 1157320);</li>
 *   <li>{@code totah.pocket-architecture-report.pose-a-dir} /
 *       {@code .pose-b-dir} — optional DiffDock output directory for
 *       that side (target_protein.pdb + rank SDF) instead of a DB
 *       pose; the receptor file must hash-match a pocket-bearing DB
 *       structure artifact;</li>
 *   <li>{@code totah.pocket-architecture-report.pose-a-rank} /
 *       {@code .pose-b-rank} — DiffDock rank for a directory side
 *       (default 1);</li>
 *   <li>{@code totah.pocket-architecture-report.dry-run} — default
 *       true; honored as a log annotation (the runner is read-only).
 *       </li>
 * </ul>
 */
@Component
@ConditionalOnProperty(
        name = "totah.pocket-architecture-report.enabled",
        havingValue = "true"
)
public class PocketArchitectureReportRunner implements CommandLineRunner {

    private static final Logger LOG = LoggerFactory.getLogger(
            PocketArchitectureReportRunner.class);

    private final PocketArchitectureReportService reportService;
    private final long poseA;
    private final long poseB;
    private final String poseADir;
    private final String poseBDir;
    private final int poseARank;
    private final int poseBRank;
    private final boolean dryRun;

    public PocketArchitectureReportRunner(
            PocketArchitectureReportService reportService,
            @Value("${totah.pocket-architecture-report.pose-a:1157340}")
            long poseA,
            @Value("${totah.pocket-architecture-report.pose-b:1157320}")
            long poseB,
            @Value("${totah.pocket-architecture-report.pose-a-dir:}")
            String poseADir,
            @Value("${totah.pocket-architecture-report.pose-b-dir:}")
            String poseBDir,
            @Value("${totah.pocket-architecture-report.pose-a-rank:1}")
            int poseARank,
            @Value("${totah.pocket-architecture-report.pose-b-rank:1}")
            int poseBRank,
            @Value("${totah.pocket-architecture-report.dry-run:true}")
            boolean dryRun
    ) {
        this.reportService =
                Objects.requireNonNull(reportService, "reportService");
        this.poseA = poseA;
        this.poseB = poseB;
        this.poseADir = poseADir;
        this.poseBDir = poseBDir;
        this.poseARank = poseARank;
        this.poseBRank = poseBRank;
        this.dryRun = dryRun;
    }

    @Override
    public void run(String... args) {
        Path directoryA = directory(poseADir);
        Path directoryB = directory(poseBDir);
        LOG.info("Pocket architecture report{}: side A={}, side B={}",
                dryRun ? " (dry-run — read-only)" : "",
                directoryA != null
                        ? directoryA + " rank " + poseARank
                        : "pose " + poseA,
                directoryB != null
                        ? directoryB + " rank " + poseBRank
                        : "pose " + poseB);

        System.out.println(reportService.report(
                directoryA == null ? poseA : null,
                directoryA,
                poseARank,
                directoryB == null ? poseB : null,
                directoryB,
                poseBRank
        ));
    }

    private static Path directory(String value) {
        return value == null || value.isBlank()
                ? null
                : Path.of(value.trim());
    }
}
