package totah.lab.web.assembly;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.nio.file.Path;

/** Gated, dry-run-by-default SAM/SAH/SFG residue correspondence batch. */
@Component
@ConditionalOnProperty(name = "totah.experimental-residue-mapping.enabled",
        havingValue = "true")
public final class ExperimentalSiteResidueMappingRunner
        implements CommandLineRunner {
    private static final Logger LOG = LoggerFactory.getLogger(
            ExperimentalSiteResidueMappingRunner.class);
    private final ExperimentalSiteResidueMappingService service;
    private final Path cohortRoot;
    private final boolean dryRun;

    public ExperimentalSiteResidueMappingRunner(
            ExperimentalSiteResidueMappingService service,
            @Value("${totah.experimental-residue-mapping.cohort-root}")
            Path cohortRoot,
            @Value("${totah.experimental-residue-mapping.dry-run:true}")
            boolean dryRun) {
        this.service = service;
        this.cohortRoot = cohortRoot;
        this.dryRun = dryRun;
    }

    @Override
    public void run(String... args) {
        var candidates = service.candidates();
        LOG.info("Experimental residue mappings: candidates={}, dryRun={}",
                candidates.size(), dryRun);
        if (dryRun) return;
        int success = 0;
        int failures = 0;
        int requested = 0;
        int mapped = 0;
        for (var candidate : candidates) {
            Path entry = cohortRoot.resolve(candidate.pdbId() + ".cif");
            try {
                var result = service.map(candidate, entry);
                success++;
                requested += result.requestedResidues();
                mapped += result.mappedResidues();
            } catch (Exception exception) {
                failures++;
                service.recordFailure(candidate, entry, exception);
                LOG.error("Residue mapping failed for {} assembly {} target {}",
                        candidate.pdbId(), candidate.assemblyId(),
                        candidate.uniProtAccession(), exception);
            }
        }
        LOG.info("Experimental residue mappings finished: success={}, "
                        + "failures={}, requestedResidues={}, mappedResidues={}",
                success, failures, requested, mapped);
        if (failures > 0) {
            throw new IllegalStateException("Residue mapping failures="
                    + failures);
        }
    }
}
