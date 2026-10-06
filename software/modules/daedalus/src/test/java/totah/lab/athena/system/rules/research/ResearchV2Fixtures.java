package totah.lab.athena.system.rules.research;
import java.util.*;
import totah.lab.athena.system.rules.*;
import totah.lab.mnemosyne.ScientificReference;

/** Synthetic authorities only; no real review or policy activation. */
public final class ResearchV2Fixtures {
    public static final ScientificReference SCIENCE=new ScientificReference(ScientificReference.Kind.AGENT,"fixture-v2","scientist","1");
    public static final ScientificReference EXECUTOR=new ScientificReference(ScientificReference.Kind.AGENT,"fixture-v2","executor","1");
    public record Fixture(RuleManifest manifest,RuleReviewPolicyV2 policy,RulePolicyContext context,Map<String,byte[]> bytes,RuleResearchDossier dossier,RuleManifest.Source raw) {
        public ResearchEligibility run()throws Exception{return new ScientificRuleResearchGate().evaluate(manifest,policy,context,s->{var b=bytes.get(s.sha256());if(b==null)throw new java.io.IOException("missing");return b;},ResearchGateAcceptanceTest.AT,QualificationMode.CURRENT);}
    }
    public static Fixture upgrade(ResearchGateAcceptanceTest.Fixture f,String variant)throws Exception {
        var bytes=new HashMap<>(f.bytes());var old=f.policy();
        var policy=new RuleReviewPolicyV2("athena-rule-review-policy/2","fixture-separated","1",old.applicableRulePrefixes(),old.requiredSystems(),old.requiredAuditCategories(),List.of(SCIENCE),List.of(EXECUTOR),List.of(EXECUTOR),"EXPLICIT_EXPIRY",true,List.of("definition","domain","perception","measurement","references/distributions","classification/thresholds","negative coverage"),List.of("implementation identity/version","source/artifact pins","fixtures/tests","execution/context/receipt binding"),old.requiredQualificationChecks(),old.canonicalizationVersion());
        var pp=ResearchGateAcceptanceTest.save(policy,bytes);var r=f.manifest().research();
        var draft=DirectAssessmentResearchFixture.bind(f.manifest(),new ResearchBinding("athena-rule-research-binding/2",r.dossier(),pp,r.domain(),r.definitionSha256(),"athena-rule-definition-projection/2"));
        var definition=ResearchCodec.definition(draft);var d=f.dossier();var review=d.review();
        var next=new RuleResearchDossier("athena-rule-research-dossier/2",d.id(),d.version(),d.ruleKey(),variant.equals("wrong-definition")?"0".repeat(64):definition,"athena-rule-definition-projection/2",d.domain(),d.sources(),d.implementationAudits(),d.literatureAudits(),d.datasetAudits(),d.decisions(),d.unresolvedRequirements(),new RuleResearchDossier.Review(variant.equals("swapped-science")?EXECUTOR:variant.equals("unauthorized")?ResearchGateAcceptanceTest.REVIEWER:SCIENCE,review.decisionSource(),review.reviewedAt(),variant.equals("long-validity")?review.validUntil().plusSeconds(86400L*800):review.validUntil(),pp,definition,d.domain().sha256()));
        var dp=ResearchGateAcceptanceTest.save(next,bytes);
        var m=DirectAssessmentResearchFixture.bind(draft,new ResearchBinding("athena-rule-research-binding/2",dp,pp,r.domain(),definition,"athena-rule-definition-projection/2"));
        if(Set.of("implementation-revision","stale-implementation").contains(variant)) {
            var executable=ResearchGateAcceptanceTest.save(Map.of("synthetic executable revision","2"),bytes);
            var refs=new ArrayList<>(m.referenceArtifacts());refs.add(executable);
            m=new RuleManifest(m.schema(),m.ruleId(),m.version(),m.profile(),m.family(),m.tier(),m.implementationId(),m.implementationVersion(),m.qualification(),m.retired(),m.requiredCapabilities(),m.requiredChemistry(),m.requiredGeometry(),m.measurementsProduced(),m.classificationStates(),m.parameters(),m.scientificSources(),refs,m.limitations(),m.negativeCoverage(),m.research());
        }
        var ctx=f.context();var context=new RulePolicyContext(ctx.schema(),pp,ctx.invalidations(),ctx.asOf(),variant.equals("swapped-context")?SCIENCE:EXECUTOR,ctx.contextSource());
        if(variant.equals("missing-artifact"))bytes.remove(dp.sha256());
        if(variant.equals("tampered"))bytes.put(dp.sha256(),new byte[]{1});
        return new Fixture(m,policy,context,bytes,next,f.raw());
    }
}
