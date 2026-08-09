package totah.lab.web.assembly;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Read-only scientific validation runner; persistence is a separate gate. */
@Component
@ConditionalOnProperty(name="totah.experimental-site-analysis.enabled",
        havingValue="true")
public final class ExperimentalBindingSiteAnalysisRunner
        implements CommandLineRunner {
    private static final Logger LOG=LoggerFactory.getLogger(
            ExperimentalBindingSiteAnalysisRunner.class);
    private final ExperimentalBindingSiteAnalysisService service;
    private final String pdbIds;
    private final String componentIds;

    public ExperimentalBindingSiteAnalysisRunner(
            ExperimentalBindingSiteAnalysisService service,
            @Value("${totah.experimental-site-analysis.pdb-ids:}") String pdbIds,
            @Value("${totah.experimental-site-analysis.component-ids:}")
            String componentIds) {
        this.service=service;
        this.pdbIds=pdbIds;
        this.componentIds=componentIds;
    }

    @Override public void run(String... args) {
        List<String> selected=Arrays.stream(pdbIds.split(",")).map(String::trim)
                .filter(value->!value.isEmpty()).map(String::toUpperCase).toList();
        List<String> selectedComponents=Arrays.stream(componentIds.split(","))
                .map(String::trim).filter(value->!value.isEmpty())
                .map(String::toUpperCase).toList();
        var occurrences=service.meaningfulOccurrences(selected, selectedComponents);
        Map<String,Stats> stats=new LinkedHashMap<>();
        List<String> distinctExamples=new ArrayList<>();
        int processed=0;
        for(var occurrence:occurrences) {
            var analysis=service.analyze(occurrence);
            Stats value=stats.computeIfAbsent(occurrence.componentId(), ignored->new Stats());
            value.occurrences++;
            int candidates=analysis.candidates().size();
            int sites=analysis.grouping().sites().size();
            if(candidates==1)value.singleCandidate++;
            if(candidates>1)value.multipleCandidates++;
            if(candidates>1&&sites==1)value.collapsedToOne++;
            if(sites>1){value.distinctSites++; if(distinctExamples.size()<20)
                distinctExamples.add(occurrence.pdbId()+":"+occurrence.componentId()
                        +":"+occurrence.id()+" candidates="+candidates+" sites="+sites);}
            if(sites>1 && distinctExamples.size()<=5) {
                LOG.info("DISTINCT_DETAIL {}:{}:{} candidates={} sites={} pairs={}",
                        occurrence.pdbId(), occurrence.componentId(), occurrence.id(),
                        analysis.candidates(), analysis.grouping().sites(),
                        analysis.grouping().pairComparisons());
            }
            for(var site:analysis.grouping().sites()) {
                value.sites++;
                value.directResidues+=site.directContactResidues().size();
                value.nearResidues+=site.nearShellResidues().size();
                if(site.humanTargets().size()==1)value.singleTargetSites++;
                if(site.humanTargets().size()>1)value.multiTargetSites++;
            }
            processed++;
            if(processed%100==0)LOG.info("Analyzed {} / {} occurrences",processed,occurrences.size());
        }
        LOG.info("Grouping rule={}",service.rule());
        stats.forEach((component,value)->LOG.info("SITE_STATS {} {}",component,value));
        LOG.info("Distinct-site examples={}",distinctExamples);
    }

    private static final class Stats {
        int occurrences,singleCandidate,multipleCandidates,collapsedToOne,
                distinctSites,sites,directResidues,nearResidues,
                singleTargetSites,multiTargetSites;
        @Override public String toString(){return "occurrences="+occurrences
                +", singleCandidate="+singleCandidate+", multipleCandidates="+multipleCandidates
                +", collapsedToOne="+collapsedToOne+", distinctSites="+distinctSites
                +", sites="+sites+", directResidues="+directResidues
                +", nearResidues="+nearResidues+", singleTargetSites="+singleTargetSites
                +", multiTargetSites="+multiTargetSites;}
    }
}
