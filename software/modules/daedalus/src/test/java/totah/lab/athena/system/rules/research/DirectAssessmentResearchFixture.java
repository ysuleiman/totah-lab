package totah.lab.athena.system.rules.research;

import totah.lab.athena.system.rules.*;
import java.nio.file.*;
import java.util.*;

/** Artificial research review for orchestration acceptance; never a production scientific receipt. */
public final class DirectAssessmentResearchFixture {
    private DirectAssessmentResearchFixture() { }
    public static ResearchGateAcceptanceTest.Fixture create(RuleManifest old,String variant)throws Exception {
        var f=ResearchGateAcceptanceTest.fixture(variant);var bytes=new HashMap<>(f.bytes());
        var domain=new RuleDomain("athena-rule-domain/1","synthetic-source-pair","1",List.of("atom"),List.of("supplied S-S connectivity only"),List.of(),List.of(),List.of(f.raw()),List.of(),old.negativeCoverage().version());
        var dp=ResearchGateAcceptanceTest.save(domain,bytes);var r=f.manifest().research();
        var draft=bind(old,new ResearchBinding(r.schema(),r.dossier(),r.reviewPolicy(),dp,r.definitionSha256(),r.projectionVersion()));
        String definition=ResearchCodec.definition(draft);var d=f.dossier();var review=d.review();
        var next=new RuleResearchDossier(d.schema(),d.id(),d.version(),draft.key(),variant.equals("wrong-definition")?"0".repeat(64):definition,d.projectionVersion(),dp,d.sources(),d.implementationAudits(),d.literatureAudits(),d.datasetAudits(),
                List.of(new RuleResearchDossier.Decision("fixture","source connectivity",dp.sha256(),RuleResearchDossier.Disposition.ADOPT,"synthetic acceptance only",List.of(f.raw()),"no chemical interpretation")),d.unresolvedRequirements(),
                new RuleResearchDossier.Review(review.reviewer(),review.decisionSource(),review.reviewedAt(),review.validUntil(),review.policy(),definition,dp.sha256()));
        var dossier=ResearchGateAcceptanceTest.save(next,bytes);
        var m=bind(old,new ResearchBinding(r.schema(),dossier,r.reviewPolicy(),dp,definition,r.projectionVersion()));
        for(var source:m.scientificSources())bytes.put(source.sha256(),Files.readAllBytes(Path.of(source.locator())));
        if(variant.equals("missing-artifact"))bytes.remove(dossier.sha256());
        var context=variant.equals("invalidated")?new RulePolicyContext(f.context().schema(),f.context().policy(),List.of(new RulePolicyContext.Invalidation(dp.sha256(),ResearchGateAcceptanceTest.AT,"synthetic invalidation",f.raw())),f.context().asOf(),f.context().issuer(),f.context().contextSource()):f.context();
        return new ResearchGateAcceptanceTest.Fixture(m,f.policy(),context,bytes,next,f.raw());
    }
    static RuleManifest bind(RuleManifest m,ResearchBinding r) {
        return new RuleManifest("athena-rule/3",m.ruleId(),m.version(),m.profile(),m.family(),m.tier(),m.implementationId(),m.implementationVersion(),m.qualification(),m.retired(),m.requiredCapabilities(),m.requiredChemistry(),m.requiredGeometry(),m.measurementsProduced(),m.classificationStates(),m.parameters(),m.scientificSources(),m.referenceArtifacts(),m.limitations(),m.negativeCoverage(),r);
    }
}
