package totah.lab.web.poseanalysis;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Gated one-shot batch runner printing the mutation-pose report for
 * the DiffDock mutant directories (per mutant: pose movement vs the
 * WT 7A reference, aligned comparison vs the WT 7B reference,
 * 7A/7B-likeness classification, pocket before/after, confidence
 * delta). Launched with
 * {@code --spring.main.web-application-type=none
 * --totah.mutation-pose-report.enabled=true} plus:
 * <ul>
 *   <li>{@code totah.mutation-pose-report.mutant-dirs} —
 *       comma-separated DiffDock result directories (required); each
 *       entry may carry an explicit label after {@code =}, e.g.
 *       {@code /path/diffdock_wall=F39L+L40M+V41A+R42V+F43L} —
 *       entries without {@code =} parse the label from the directory
 *       name;</li>
 *   <li>{@code totah.mutation-pose-report.pose-a} — WT 7A reference
 *       pose id (default 1157340);</li>
 *   <li>{@code totah.mutation-pose-report.pose-b} — WT 7B reference
 *       pose id (default 1157320);</li>
 *   <li>{@code totah.mutation-pose-report.rank} — DiffDock rank to
 *       analyze (default 1);</li>
 *   <li>{@code totah.mutation-pose-report.expected-mutations} —
 *       comma-separated labels the availability section reports on
 *       (default F39L,L40M,F43L); labels without a directory are
 *       listed as missing, never fabricated;</li>
 *   <li>{@code totah.mutation-pose-report.dry-run} — default true;
 *       honored as a log annotation (the runner is read-only).</li>
 * </ul>
 */
@Component
@ConditionalOnProperty(
        name = "totah.mutation-pose-report.enabled",
        havingValue = "true"
)
public class MutationPoseReportRunner implements CommandLineRunner {

    private static final Logger LOG =
            LoggerFactory.getLogger(MutationPoseReportRunner.class);

    private final MutationPoseReportService reportService;
    private final String mutantDirs;
    private final long poseA;
    private final long poseB;
    private final int rank;
    private final String expectedMutations;
    private final boolean dryRun;

    public MutationPoseReportRunner(
            MutationPoseReportService reportService,
            @Value("${totah.mutation-pose-report.mutant-dirs:}")
            String mutantDirs,
            @Value("${totah.mutation-pose-report.pose-a:1157340}")
            long poseA,
            @Value("${totah.mutation-pose-report.pose-b:1157320}")
            long poseB,
            @Value("${totah.mutation-pose-report.rank:1}")
            int rank,
            @Value("${totah.mutation-pose-report.expected-mutations:"
                    + "F39L,L40M,F43L}")
            String expectedMutations,
            @Value("${totah.mutation-pose-report.dry-run:true}")
            boolean dryRun
    ) {
        this.reportService =
                Objects.requireNonNull(reportService, "reportService");
        this.mutantDirs = mutantDirs;
        this.poseA = poseA;
        this.poseB = poseB;
        this.rank = rank;
        this.expectedMutations = expectedMutations;
        this.dryRun = dryRun;
    }

    @Override
    public void run(String... args) {
        List<MutationPoseReportService.MutantDirEntry> directories =
                entries(mutantDirs);
        LOG.info("Mutation pose report{}: dirs={}, pose A={}, pose B={},"
                        + " rank={}",
                dryRun ? " (dry-run — read-only)" : "",
                directories, poseA, poseB, rank);

        System.out.println(reportService.report(
                directories,
                poseA,
                poseB,
                rank,
                labels(expectedMutations)
        ));
    }

    private static List<MutationPoseReportService.MutantDirEntry>
            entries(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    "totah.mutation-pose-report.mutant-dirs is required"
            );
        }
        List<MutationPoseReportService.MutantDirEntry> entries =
                new ArrayList<>();
        for (String entry : value.split(",")) {
            if (!entry.isBlank()) {
                entries.add(MutationPoseReportService.MutantDirEntry
                        .parse(entry));
            }
        }
        if (entries.isEmpty()) {
            throw new IllegalArgumentException(
                    "totah.mutation-pose-report.mutant-dirs is required"
            );
        }
        return List.copyOf(entries);
    }

    private static List<String> labels(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        List<String> labels = new ArrayList<>();
        for (String entry : value.split(",")) {
            if (!entry.isBlank()) {
                labels.add(entry.trim());
            }
        }
        return List.copyOf(labels);
    }
}
