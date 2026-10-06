package totah.lab.athena.system.rules.research;

import totah.lab.athena.system.rules.*;
import java.nio.file.*;
import java.util.*;

/** Artificial, explicitly short-lived review for orchestration testing only. */
public final class HbondCandidateResearchFixture {
    private HbondCandidateResearchFixture() { }
    public static ResearchV2Fixtures.Fixture create(RuleManifest source,String variant) throws Exception {
        var f=ResearchGateAcceptanceTest.fixture(variant);var bytes=new HashMap<>(f.bytes());
        var domain=new RuleDomain("athena-rule-domain/1","synthetic-i02","1",List.of("atom","component"),
                List.of("explicit qualified D/H/A source tuples; supported class pairs only"),List.of(),List.of(),List.of(f.raw()),List.of(),source.negativeCoverage().version());
        var dp=ResearchGateAcceptanceTest.save(domain,bytes);var r=f.manifest().research();
        var draft=DirectAssessmentResearchFixture.bind(source,new ResearchBinding(r.schema(),r.dossier(),r.reviewPolicy(),dp,r.definitionSha256(),r.projectionVersion()));
        var definition=ResearchCodec.definition(draft);var d=f.dossier();var review=d.review();
        var dossier=new RuleResearchDossier(d.schema(),"synthetic-i02",d.version(),draft.key(),definition,d.projectionVersion(),dp,d.sources(),d.implementationAudits(),d.literatureAudits(),d.datasetAudits(),
                List.of(new RuleResearchDossier.Decision("fixture","directional candidate",dp.sha256(),RuleResearchDossier.Disposition.ADOPT,"engineering fixture, not scientific approval",List.of(f.raw()),"no physical favorability")),d.unresolvedRequirements(),
                new RuleResearchDossier.Review(review.reviewer(),review.decisionSource(),review.reviewedAt(),review.validUntil(),review.policy(),definition,dp.sha256()));
        var pin=ResearchGateAcceptanceTest.save(dossier,bytes);
        var m=DirectAssessmentResearchFixture.bind(source,new ResearchBinding(r.schema(),pin,r.reviewPolicy(),dp,definition,r.projectionVersion()));
        for(var s:m.scientificSources())bytes.put(s.sha256(),Files.readAllBytes(Path.of(s.locator())));
        return ResearchV2Fixtures.upgrade(new ResearchGateAcceptanceTest.Fixture(m,f.policy(),f.context(),bytes,dossier,f.raw()),variant);
    }
}
