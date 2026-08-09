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
 * Gated one-shot batch runner comparing the 7B v6 pocket that
 * dominates the DiffDock occupancy (FPOCKET pocket 8) against every 7A
 * pocket, with the SAM/cofactor overlap of the pocket. Launched with
 * {@code --spring.main.web-application-type=none
 * --totah.pocket8-homology.enabled=true} plus:
 * <ul>
 *   <li>{@code totah.pocket8-homology.dir} — the 7B DiffDock output
 *       directory (default the canonical METTL7B v6 directory);</li>
 *   <li>{@code totah.pocket8-homology.run-a} — 7A reference run
 *       (default 2829);</li>
 *   <li>{@code totah.pocket8-homology.query-pocket-id} — DB id of the
 *       query pocket (default 10 = 7B FPOCKET pocket 8);</li>
 *   <li>{@code totah.pocket8-homology.control-pocket-b-id} /
 *       {@code .control-pocket-a-id} — positive-control pockets
 *       (defaults 5 and 19: 7B pocket 3 vs 7A pocket 1);</li>
 *   <li>{@code totah.pocket8-homology.sam-complex-b} /
 *       {@code .sam-complex-a} — SAM complex PDB paths (7A has none by
 *       default: the overlap is reported NOT_AVAILABLE);</li>
 *   <li>{@code totah.pocket8-homology.dry-run} — default true;
 *       honored as a log annotation (read-only).</li>
 * </ul>
 */
@Component
@ConditionalOnProperty(
        name = "totah.pocket8-homology.enabled",
        havingValue = "true"
)
public class Pocket8HomologyRunner implements CommandLineRunner {

    private static final Logger LOG =
            LoggerFactory.getLogger(Pocket8HomologyRunner.class);

    private static final String DEFAULT_DIR =
            "/Users/yazan/totah-lab/experiments/METTL7B-v6_diffdock";
    private static final String DEFAULT_SAM_B =
            "/Users/yazan/artifacts/targets/Q6UX53"
                    + "/METTL7B_SAM_pose1_complex.pdb";
    private static final String DEFAULT_SAM_A =
            "/Users/yazan/artifacts/targets/Q9H8H3"
                    + "/METTL7A_SAM_pose1_complex.pdb";

    private final Pocket8HomologyService homologyService;
    private final String directory;
    private final long runA;
    private final long queryPocketId;
    private final long controlPocketBId;
    private final long controlPocketAId;
    private final String samComplexB;
    private final String samComplexA;
    private final boolean dryRun;

    public Pocket8HomologyRunner(
            Pocket8HomologyService homologyService,
            @Value("${totah.pocket8-homology.dir:" + DEFAULT_DIR + "}")
            String directory,
            @Value("${totah.pocket8-homology.run-a:2829}")
            long runA,
            @Value("${totah.pocket8-homology.query-pocket-id:10}")
            long queryPocketId,
            @Value("${totah.pocket8-homology.control-pocket-b-id:5}")
            long controlPocketBId,
            @Value("${totah.pocket8-homology.control-pocket-a-id:19}")
            long controlPocketAId,
            @Value("${totah.pocket8-homology.sam-complex-b:"
                    + DEFAULT_SAM_B + "}")
            String samComplexB,
            @Value("${totah.pocket8-homology.sam-complex-a:"
                    + DEFAULT_SAM_A + "}")
            String samComplexA,
            @Value("${totah.pocket8-homology.dry-run:true}")
            boolean dryRun
    ) {
        this.homologyService =
                Objects.requireNonNull(homologyService,
                        "homologyService");
        this.directory = directory;
        this.runA = runA;
        this.queryPocketId = queryPocketId;
        this.controlPocketBId = controlPocketBId;
        this.controlPocketAId = controlPocketAId;
        this.samComplexB = samComplexB;
        this.samComplexA = samComplexA;
        this.dryRun = dryRun;
    }

    @Override
    public void run(String... args) {
        LOG.info("Pocket 8 homology scan{}: dir={}, run A={}, query"
                        + " pocket id={}",
                dryRun ? " (dry-run — read-only)" : "",
                directory, runA, queryPocketId);

        System.out.println(homologyService.report(
                Path.of(directory.trim()),
                runA,
                queryPocketId,
                controlPocketBId,
                controlPocketAId,
                Path.of(samComplexB.trim()),
                Path.of(samComplexA.trim())
        ));
    }
}
