package totah.lab.web.assembly;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "totah.experimental-site-grammar.enabled",
        havingValue = "true")
public final class ExperimentalSiteGrammarRunner implements CommandLineRunner {
    private static final Logger LOG = LoggerFactory.getLogger(
            ExperimentalSiteGrammarRunner.class);
    private final ExperimentalSiteGrammarService service;
    private final boolean dryRun;

    public ExperimentalSiteGrammarRunner(ExperimentalSiteGrammarService service,
            @Value("${totah.experimental-site-grammar.dry-run:true}")
            boolean dryRun) {
        this.service = service;
        this.dryRun = dryRun;
    }

    @Override
    public void run(String... args) throws Exception {
        if (dryRun) {
            LOG.info("Experimental site grammar dry run; no writes");
            return;
        }
        var result = service.derive();
        LOG.info("Experimental site grammar: pairs={}, rows={}, siteRows={}, "
                        + "lowConfidenceRows={}",
                result.acceptedPairsWithSiteGrammar(),
                result.residueGrammarRows(),
                result.experimentallySupportedRows(),
                result.lowConfidenceResidueRows());
    }
}
