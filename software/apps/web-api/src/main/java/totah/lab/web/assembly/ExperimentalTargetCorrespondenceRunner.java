package totah.lab.web.assembly;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name="totah.experimental-target-correspondence.enabled",
        havingValue="true")
public final class ExperimentalTargetCorrespondenceRunner
        implements CommandLineRunner {
    private static final Logger LOG = LoggerFactory.getLogger(
            ExperimentalTargetCorrespondenceRunner.class);
    private final ExperimentalTargetCorrespondenceService service;
    private final boolean dryRun;

    public ExperimentalTargetCorrespondenceRunner(
            ExperimentalTargetCorrespondenceService service,
            @Value("${totah.experimental-target-correspondence.dry-run:true}")
            boolean dryRun) {
        this.service = service;
        this.dryRun = dryRun;
    }

    @Override public void run(String... args) throws Exception {
        if (dryRun) {
            LOG.info("Experimental target correspondence dry run; no writes");
            return;
        }
        var result = service.build();
        LOG.info("Experimental target correspondence: targets={}, unavailable={}, "
                        + "accepted={}, lowConfidence={}, pairs={}", result.targets(),
                result.unavailableTargets(),
                result.acceptedAlignments(), result.lowConfidenceAlignments(),
                result.persistedPairs());
    }
}
