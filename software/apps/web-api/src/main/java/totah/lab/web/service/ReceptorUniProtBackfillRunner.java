package totah.lab.web.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Command-line entry point for {@link ReceptorUniProtBackfillService}.
 * Gated by {@code totah.backfill.enabled=true} so it never runs during
 * normal startup or tests. Launch with
 * {@code --spring.main.web-application-type=none}:
 *
 * <pre>
 * --totah.backfill.enabled=true
 * --totah.backfill.uniprot-tsv=/path/to/uniprot_UP000005640.tsv
 * </pre>
 */
@Component
@ConditionalOnProperty(
        name = "totah.backfill.enabled",
        havingValue = "true"
)
public class ReceptorUniProtBackfillRunner implements CommandLineRunner {

    private static final Logger LOG =
            LoggerFactory.getLogger(ReceptorUniProtBackfillRunner.class);

    private final ReceptorUniProtBackfillService backfillService;
    private final Path uniprotTsv;

    public ReceptorUniProtBackfillRunner(
            ReceptorUniProtBackfillService backfillService,
            @Value("${totah.backfill.uniprot-tsv:"
                    + "/Users/yazan/artifacts/uniprot_UP000005640.tsv}")
            Path uniprotTsv
    ) {
        this.backfillService = Objects.requireNonNull(backfillService);
        this.uniprotTsv = Objects.requireNonNull(uniprotTsv);
    }

    @Override
    public void run(String... args) throws Exception {
        if (!Files.isRegularFile(uniprotTsv)) {
            throw new IllegalStateException(
                    "UniProt TSV does not exist: " + uniprotTsv
            );
        }

        LOG.info("Receptor UniProt backfill starting from {}", uniprotTsv);

        ReceptorUniProtBackfillService.BackfillResult result =
                backfillService.backfill(uniprotTsv);

        LOG.info("Receptor UniProt backfill summary: uniProtEntries={}, "
                        + "updated={}, alreadyComplete={}",
                result.uniProtEntries(),
                result.updated(),
                result.alreadyComplete());
    }
}
