package totah.lab.athena.system.rules.research;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import totah.lab.athena.system.rules.*;
import totah.lab.athena.system.SystemGraphCertificate;
import totah.lab.athena.system.SystemStateView;
import totah.lab.mnemosyne.*;
import java.io.IOException;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static totah.lab.athena.system.rules.research.RuleResearchDossier.*;

/** Synthetic administration fixtures only. No scientific dossier or reviewer is certified here. */
public class ResearchGateAcceptanceTest {
    public static final Instant AT=Instant.parse("2026-10-05T00:00:00Z");
    public static final ScientificReference REVIEWER=new ScientificReference(ScientificReference.Kind.METHOD,"fixture","reviewer","1");
    static final String ZERO="0".repeat(64);
    static final List<String> SYSTEMS=List.of("Athena/OCL","MolProbity/Probe/wwPDB","OpenFF/SMIRNOFF","PLIP","ProLIF","RDKit/FDef","RING");
    static final List<String> CHANGES=List.of("classification/thresholds","domain","implementation version","measurement","negative coverage","perception","references/distributions");
    public record Fixture(RuleManifest manifest,RuleReviewPolicy policy,RulePolicyContext context,Map<String,byte[]> bytes,
                   RuleResearchDossier dossier,RuleManifest.Source raw) {
        ResearchArtifactReader reader(){return source->{var b=bytes.get(source.sha256());if(b==null)throw new IOException("missing supplied pin");return b.clone();};}
        ResearchEligibility run()throws IOException{return new ScientificRuleResearchGate().evaluate(manifest,policy,context,reader(),AT,QualificationMode.CURRENT);}
    }
    static RuleManifest.Source save(Object value,Map<String,byte[]> bytes){var p=ResearchCodec.pin(value);bytes.put(p.sha256(),ResearchCodec.bytes(value));return p;}
    static RuleManifest manifest(ResearchBinding r)throws Exception {
        var old=RuleRegistry.decode(Files.readAllBytes(Path.of("software/modules/athena/src/main/resources/totah/lab/athena/system/rules/groups-b01/ATHENA.GROUP.CARBONYL.rule.json")));
        return new RuleManifest("athena-rule/3",old.ruleId(),old.version(),old.profile(),old.family(),old.tier(),old.implementationId(),old.implementationVersion(),
                SystemGraphCertificate.Status.NOT_EVALUATED,false,old.requiredCapabilities(),old.requiredChemistry(),old.requiredGeometry(),old.measurementsProduced(),
                old.classificationStates(),old.parameters(),old.scientificSources(),old.referenceArtifacts(),old.limitations(),old.negativeCoverage(),r);
    }
    public static Fixture fixture(String variant)throws Exception {
        var bytes=new HashMap<String,byte[]>();var raw=save(Map.of("fixture","not a real research review"),bytes);
        var policy=new RuleReviewPolicy("athena-rule-review-policy/1","fixture-policy","1",List.of("ATHENA."),SYSTEMS,List.of("DATASET","IMPLEMENTATION","LITERATURE"),
                List.of(REVIEWER),List.of(REVIEWER),"EXPLICIT_EXPIRY",true,CHANGES,List.of("synthetic-positive"),"athena-research-json/1");
        var policyPin=save(policy,bytes);
        var domain=new RuleDomain("athena-rule-domain/1","fixture-domain","1",List.of("atom"),List.of("literal neutral carbonyl"),List.of(),List.of(),List.of(raw),List.of(),"ATHENA.GROUP.CARBONYL/negative/1");
        var domainPin=save(domain,bytes);
        var placeholder=new RuleManifest.Source("placeholder",ZERO,"cycle-free placeholder");
        var draft=manifest(new ResearchBinding("athena-rule-research-binding/1",placeholder,policyPin,domainPin,ZERO,"athena-rule-definition-projection/1"));
        var definition=ResearchCodec.definition(draft);
        var review=new RuleResearchDossier.Review(variant.equals("unauthorized")?new ScientificReference(ScientificReference.Kind.METHOD,"fixture","unauthorized","1"):REVIEWER,raw,
                variant.equals("future")?AT.plusSeconds(1):AT.minusSeconds(60),variant.equals("expired")?AT:AT.plusSeconds(60),policyPin,definition,domainPin.sha256());
        var audits=SYSTEMS.stream().filter(s->!variant.equals("missing-system")||!s.equals("PLIP")).map(s->new Audit(s,"fixture-1",variant.equals("uninspected")?Applicability.UNINSPECTED:Applicability.NOT_APPLICABLE,
                List.of(raw),List.of(),"fixture perception","fixture measurement","fixture classification",List.of(),List.of("synthetic only"))).toList();
        var datasets=List.of(new DatasetAudit("exact-graph",variant.equals("missing-data")?Applicability.APPLICABLE:Applicability.NOT_APPLICABLE,List.of(raw),"synthetic graph",
                "fixture","no empirical population","not applicable","source graph",AnalysisStatus.NOT_ACQUIRED,List.of("fixture only")));
        var dossier=new RuleResearchDossier("athena-rule-research-dossier/1","fixture-dossier","1",draft.key(),variant.equals("wrong-definition")?ZERO:definition,
                "athena-rule-definition-projection/1",domainPin,List.of(raw),audits,List.of(audits.getFirst()),datasets,
                List.of(new Decision("retain-alternative","fixture criterion",domainPin.sha256(),Disposition.REJECT,"synthetic alternative not adopted",List.of(raw),"retain source graph")),
                variant.equals("unresolved")?List.of("missing domain review"):List.of(),review);
        var dossierPin=save(dossier,bytes);
        var m=manifest(new ResearchBinding("athena-rule-research-binding/1",dossierPin,policyPin,domainPin,definition,"athena-rule-definition-projection/1"));
        for(var source:m.scientificSources())bytes.put(source.sha256(),Files.readAllBytes(Path.of(source.locator())));
        var context=new RulePolicyContext("athena-rule-policy-context/1",policyPin,
                variant.equals("invalidated")?List.of(new RulePolicyContext.Invalidation(domainPin.sha256(),AT,"fixture invalidation",raw)):List.of(),
                variant.equals("stale-context")?AT.minusSeconds(1):AT,REVIEWER,raw);
        if(variant.equals("tampered"))bytes.put(dossierPin.sha256(),new byte[]{1});
        if(variant.equals("missing-artifact"))bytes.remove(dossierPin.sha256());
        return new Fixture(m,policy,context,bytes,dossier,raw);
    }
    @Test void completeSyntheticDossierIsEligibleButNeverAQualification()throws Exception {
        var f=fixture("valid");var result=f.run();assertTrue(result.eligible(),result.reasons().toString());
        assertEquals(SystemGraphCertificate.Status.NOT_EVALUATED,f.manifest.qualification());
        assertEquals(result,f.run());assertEquals(result,ResearchCodec.decode(ResearchCodec.bytes(result),ResearchEligibility.class));
    }
    @ParameterizedTest @ValueSource(strings={"unauthorized","future","expired","missing-system","uninspected","missing-data","wrong-definition","unresolved","invalidated","stale-context"})
    void incompleteResearchIsNonEligible(String variant)throws Exception {
        var f=fixture(variant);var before=ResearchCodec.bytes(f.dossier);var result=f.run();assertFalse(result.eligible(),variant);assertFalse(result.reasons().isEmpty());
        assertArrayEquals(before,ResearchCodec.bytes(f.dossier));
    }
    @ParameterizedTest @ValueSource(strings={"tampered","missing-artifact"})
    void badArtifactTransportIsCheckedFailure(String variant)throws Exception {assertThrows(IOException.class,()->fixture(variant).run());}
    @Test void historicalModeCannotBecomeCurrentByRelabeling()throws Exception {
        var f=fixture("valid");var result=new ScientificRuleResearchGate().evaluate(f.manifest,f.policy,f.context,f.reader(),AT,QualificationMode.HISTORICAL_REPLAY);
        assertTrue(result.eligible());assertEquals(QualificationMode.HISTORICAL_REPLAY,result.mode());assertNotEquals(result,f.run());
    }
    @Test void legacyManifestsKeepExactBytesAndDigests()throws Exception {
        for(String directory:List.of("scientific-b00-v2","groups-b01"))try(var paths=Files.list(Path.of("software/modules/athena/src/main/resources/totah/lab/athena/system/rules/"+directory))){
            for(var p:paths.filter(x->x.toString().endsWith(".rule.json")).toList()){
                var m=RuleRegistry.decode(Files.readAllBytes(p));var node=ResearchCodec.JSON.readTree(SystemStateView.bytes(m));assertFalse(node.has("research"));
                assertEquals(m,RuleRegistry.decode(SystemStateView.bytes(m)));assertEquals(RuleRegistry.digest(m),EvidenceExchange.sha256(SystemStateView.bytes(m)));
            }
        }
    }
    @Test void v3CannotSelfCertifyAndHistoricalCannotTakeResearchField()throws Exception {
        var f=fixture("valid");var node=ResearchCodec.JSON.valueToTree(f.manifest);((com.fasterxml.jackson.databind.node.ObjectNode)node).put("qualification","QUALIFIED");
        assertThrows(IOException.class,()->RuleRegistry.decode(ResearchCodec.JSON.writeValueAsBytes(node)));
        ((com.fasterxml.jackson.databind.node.ObjectNode)node).put("schema","athena-rule/2");
        assertThrows(IOException.class,()->RuleRegistry.decode(ResearchCodec.JSON.writeValueAsBytes(node)));
        assertEquals(f.manifest,RuleRegistry.decode(ResearchCodec.bytes(f.manifest)));
    }
    @Test void canonicalizationUsesScalarOrderAndPreservesArrayOrder()throws Exception {
        assertEquals("{\"a\":[\"b\",\"a\"],\"\ue000\":\"x\",\"\ud800\udc00\":\"y\"}",new String(ResearchCodec.bytes(Map.of("\ud800\udc00","y","\ue000","x","a",List.of("b","a"))),java.nio.charset.StandardCharsets.UTF_8));
        assertThrows(IllegalArgumentException.class,()->ResearchCodec.bytes(Map.of("bad","\ud800")));
        assertThrows(IOException.class,()->ResearchCodec.decode("{\"x\":true,\"x\":false}".getBytes(),RuleDomain.class));
        assertThrows(IOException.class,()->ResearchCodec.decode("{} {}".getBytes(),RuleDomain.class));
        assertThrows(IOException.class,()->ResearchCodec.decode("{\"x\":null}".getBytes(),RuleDomain.class));
    }
    @Test void unknownFieldsAndCoercionRejected()throws Exception {
        var f=fixture("valid");var node=(com.fasterxml.jackson.databind.node.ObjectNode)ResearchCodec.JSON.valueToTree(f.policy);
        node.put("requireExpiryAfterReview","true");assertThrows(IOException.class,()->ResearchCodec.decode(ResearchCodec.JSON.writeValueAsBytes(node),RuleReviewPolicy.class));
        node.put("requireExpiryAfterReview",true);node.put("unknown","value");assertThrows(IOException.class,()->ResearchCodec.decode(ResearchCodec.JSON.writeValueAsBytes(node),RuleReviewPolicy.class));
    }
    @Test void reviewExpiryIsExclusiveAndWrongSourceDomainCannotBorrowDossier()throws Exception {
        var f=fixture("valid");var at=AT.plusSeconds(60);
        var context=new RulePolicyContext(f.context.schema(),f.context.policy(),List.of(),at,REVIEWER,f.raw);
        var expired=new ScientificRuleResearchGate().evaluate(f.manifest,f.policy,context,f.reader(),at,QualificationMode.HISTORICAL_REPLAY);
        assertFalse(expired.eligible());assertTrue(expired.reasons().contains("REVIEW_EXPIRED"));
        var other=new RuleDomain("athena-rule-domain/1","different","1",List.of("atom"),List.of("wider chemical state"),List.of(),List.of(),List.of(f.raw),List.of(),"ATHENA.GROUP.CARBONYL/negative/1");
        var otherPin=save(other,f.bytes);var r=f.manifest.research();
        var altered=manifest(new ResearchBinding(r.schema(),r.dossier(),r.reviewPolicy(),otherPin,r.definitionSha256(),r.projectionVersion()));
        var result=new ScientificRuleResearchGate().evaluate(altered,f.policy,f.context,f.reader(),AT,QualificationMode.CURRENT);
        assertFalse(result.eligible());assertTrue(result.reasons().contains("DOSSIER_DEFINITION_DOMAIN_MISMATCH"));
    }
    @Test void rejectedAlternativesAndOriginalArtifactsRemainImmutable()throws Exception {
        var f=fixture("valid");assertEquals(Disposition.REJECT,ResearchDocuments.decode(ResearchDocuments.encode(f.dossier),RuleResearchDossier.class).decisions().getFirst().disposition());
        var b=f.reader().read(f.raw);b[0]=0;assertNotEquals(0,f.reader().read(f.raw)[0]);
        assertThrows(UnsupportedOperationException.class,()->f.dossier.decisions().clear());
    }
    @Test void historicalRegistryCannotBeUsedAsCurrentQualification()throws Exception {
        var f=fixture("valid");var old=RuleRegistry.decode(Files.readAllBytes(Path.of("software/modules/athena/src/main/resources/totah/lab/athena/system/rules/groups-b01/ATHENA.GROUP.CARBONYL.rule.json")));
        var fake=new RuleQualificationReceipt("athena-rule-qualification-receipt/1",old.key(),RuleRegistry.digest(old),f.raw,f.raw,f.raw,f.raw,f.raw,SystemGraphCertificate.Status.QUALIFIED,QualificationMode.CURRENT,AT,List.of("forged"));
        assertThrows(IOException.class,()->new RuleRegistry().register(old).requireCurrent(old.key(),RuleRegistry.digest(old),fake,f.context,f.reader(),AT));
    }
    public static void main(String[] args)throws Exception {Files.write(Path.of(args[0]),ResearchCodec.bytes(fixture("valid").run()));}
}
