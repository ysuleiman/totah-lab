package totah.lab.web.poseanalysis;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * Gated one-shot batch runner printing the differential-contact report
 * of one ligand docked against two receptors (sections A–E: contact
 * maps, aligned differential-contact table, residue-number mapping,
 * ranked mutation candidates). Launched with
 * {@code --spring.main.web-application-type=none
 * --totah.differential-contacts.enabled=true} plus the
 * {@code totah.differential-contacts.*} properties below.
 *
 * <p>The runner is read-only: no writes exist, so the dry-run flag
 * (default true, per the batch convention) only annotates the log —
 * there is nothing to withhold. Failures (unknown ligand, receptor,
 * pose or missing artifacts) abort with a clear message.</p>
 */
@Component
@ConditionalOnProperty(
        name = "totah.differential-contacts.enabled",
        havingValue = "true"
)
public class DifferentialContactReportRunner implements CommandLineRunner {

    private static final Logger LOG =
            LoggerFactory.getLogger(DifferentialContactReportRunner.class);

    private final DifferentialContactReportService reportService;
    private final String ligand;
    private final long receptorA;
    private final long receptorB;
    private final Long poseA;
    private final Long poseB;
    private final boolean dryRun;

    public DifferentialContactReportRunner(
            DifferentialContactReportService reportService,
            @Value("${totah.differential-contacts.ligand:}")
            String ligand,
            @Value("${totah.differential-contacts.receptor-a:}")
            String receptorA,
            @Value("${totah.differential-contacts.receptor-b:}")
            String receptorB,
            @Value("${totah.differential-contacts.pose-a:}")
            String poseA,
            @Value("${totah.differential-contacts.pose-b:}")
            String poseB,
            @Value("${totah.differential-contacts.dry-run:true}")
            boolean dryRun
    ) {
        this.reportService =
                Objects.requireNonNull(reportService, "reportService");
        this.ligand = ligand;
        this.receptorA = parseRequiredLong("receptor-a", receptorA);
        this.receptorB = parseRequiredLong("receptor-b", receptorB);
        this.poseA = parseLong("pose-a", poseA);
        this.poseB = parseLong("pose-b", poseB);
        this.dryRun = dryRun;
    }

    @Override
    public void run(String... args) {
        if (ligand == null || ligand.isBlank()) {
            throw new IllegalArgumentException(
                    "totah.differential-contacts.ligand is required"
            );
        }
        LOG.info("Differential contact report{}: ligand={}, receptor A={},"
                        + " receptor B={}, pose A={}, pose B={}",
                dryRun ? " (dry-run — read-only; no writes exist)" : "",
                ligand.trim(), receptorA, receptorB,
                poseA == null ? "default (best run top pose)" : poseA,
                poseB == null ? "default (best run top pose)" : poseB);

        System.out.println(reportService.report(
                ligand.trim(),
                receptorA,
                receptorB,
                poseA,
                poseB
        ));
    }

    private static long parseRequiredLong(String property, String value) {
        Long parsed = parseLong(property, value);
        if (parsed == null) {
            throw new IllegalArgumentException(
                    "totah.differential-contacts." + property
                            + " is required"
            );
        }
        return parsed;
    }

    private static Long parseLong(String property, String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(
                    "totah.differential-contacts." + property
                            + " is not a number: " + value
            );
        }
    }
}
