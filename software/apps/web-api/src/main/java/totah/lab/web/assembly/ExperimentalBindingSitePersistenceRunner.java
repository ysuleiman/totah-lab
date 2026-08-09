package totah.lab.web.assembly;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

/** Gated, dry-run-by-default canonical experimental-site batch. */
@Component
@ConditionalOnProperty(name="totah.experimental-site-persistence.enabled",havingValue="true")
public final class ExperimentalBindingSitePersistenceRunner implements CommandLineRunner {
    private static final Logger LOG=LoggerFactory.getLogger(ExperimentalBindingSitePersistenceRunner.class);
    private final ExperimentalBindingSiteAnalysisService analysis;
    private final ExperimentalBindingSitePersistenceService persistence;
    private final boolean dryRun;
    public ExperimentalBindingSitePersistenceRunner(ExperimentalBindingSiteAnalysisService analysis,
            ExperimentalBindingSitePersistenceService persistence,
            @Value("${totah.experimental-site-persistence.dry-run:true}") boolean dryRun){
        this.analysis=analysis;this.persistence=persistence;this.dryRun=dryRun;
    }
    @Override public void run(String...args){
        var occurrences=analysis.meaningfulOccurrences(List.of(),List.of());
        LOG.info("Canonical experimental sites: occurrences={}, dryRun={}",occurrences.size(),dryRun);
        if(dryRun)return;
        int success=0,failures=0,sites=0;
        for(var occurrence:occurrences)try{
            var result=persistence.persist(occurrence);success++;sites+=result.sites();
            if(success%100==0)LOG.info("Persisted {} / {}; sites={}",success,occurrences.size(),sites);
        }catch(Exception exception){failures++;LOG.error("Site persistence failed for occurrence {}",occurrence.id(),exception);}
        LOG.info("Canonical site persistence finished: success={}, failures={}, sites={}",success,failures,sites);
        if(failures>0)throw new IllegalStateException("Canonical site failures="+failures);
    }
}
