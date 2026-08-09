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
 * Gated one-shot batch runner producing a docking-ready mutant
 * receptor PDBQT (proteus fixed-backbone substitution + hermes PDBQT
 * writer) from the receptor artifact of a persisted docking run.
 * Launched with {@code --spring.main.web-application-type=none
 * --totah.mutation-prep.enabled=true} plus:
 * <ul>
 *   <li>{@code totah.mutation-prep.run-id} — the docking run whose
 *       receptor artifact is the wild type (required);</li>
 *   <li>{@code totah.mutation-prep.mutation} — compact spec such as
 *       {@code F43L}, or a comma-separated list applied sequentially
 *       to the same receptor, such as {@code F39L,L40M,V41A,R42V,F43L}
 *       (required);</li>
 *   <li>{@code totah.mutation-prep.output} — output PDBQT path
 *       (required; parent directories are created);</li>
 *   <li>{@code totah.mutation-prep.dry-run} — default true; validates
 *       the request and reports the intended output without writing
 *       it.</li>
 * </ul>
 */
@Component
@ConditionalOnProperty(
        name = "totah.mutation-prep.enabled",
        havingValue = "true"
)
public class MutationPreparationRunner implements CommandLineRunner {

    private static final Logger LOG =
            LoggerFactory.getLogger(MutationPreparationRunner.class);

    private final MutationPreparationOperation preparationService;
    private final String runId;
    private final String mutation;
    private final String output;
    private final boolean dryRun;

    public MutationPreparationRunner(
            MutationPreparationOperation preparationService,
            @Value("${totah.mutation-prep.run-id:}")
            String runId,
            @Value("${totah.mutation-prep.mutation:}")
            String mutation,
            @Value("${totah.mutation-prep.output:}")
            String output,
            @Value("${totah.mutation-prep.dry-run:true}")
            boolean dryRun
    ) {
        this.preparationService =
                Objects.requireNonNull(preparationService,
                        "preparationService");
        this.runId = runId;
        this.mutation = mutation;
        this.output = output;
        this.dryRun = dryRun;
    }

    @Override
    public void run(String... args) {
        long parsedRunId = parseRunId(runId);
        if (mutation == null || mutation.isBlank()) {
            throw new IllegalArgumentException(
                    "totah.mutation-prep.mutation is required"
            );
        }
        if (output == null || output.isBlank()) {
            throw new IllegalArgumentException(
                    "totah.mutation-prep.output is required"
            );
        }
        LOG.info("Mutation preparation{}: run={}, mutation={}, output={}",
                dryRun ? " (dry-run — no output written)" : "",
                parsedRunId, mutation.trim(), output.trim());

        if (dryRun) {
            return;
        }

        System.out.println(preparationService.render(
                preparationService.prepare(
                        parsedRunId,
                        mutation.trim(),
                        Path.of(output.trim())
                )
        ));
    }

    private static long parseRunId(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    "totah.mutation-prep.run-id is required"
            );
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(
                    "totah.mutation-prep.run-id is not a number: "
                            + value
            );
        }
    }
}
