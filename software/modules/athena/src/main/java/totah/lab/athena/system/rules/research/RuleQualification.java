package totah.lab.athena.system.rules.research;

import totah.lab.athena.system.*;
import totah.lab.athena.system.rules.*;
import totah.lab.mnemosyne.EvidenceExchange;
import java.io.IOException;
import java.time.Instant;
import java.util.*;

/** Rechecks pinned research, implementation and concrete-state bindings; no boolean-only issuance path. */
public final class RuleQualification {
    private RuleQualification() { }
    public static RuleQualificationReceipt qualify(RuleManifest manifest,ResearchEligibility eligibility,
            RuleImplementationQualification implementation,SystemGraphCertificate foundation,SystemStateView state,
            RuleRequest request,RulePolicyContext context,ResearchArtifactReader artifacts,Instant evaluatedAt)throws IOException {
        Objects.requireNonNull(state);
        if(!state.binding().equals(foundation.binding())||!state.binding().equals(request.state()))throw new IOException("state binding mismatch");
        var status=checked(manifest,eligibility,implementation,foundation,request,context,artifacts,evaluatedAt);
        return new RuleQualificationReceipt("athena-rule-qualification-receipt/1",manifest.key(),RuleRegistry.digest(manifest),
                ResearchCodec.pin(eligibility),ResearchCodec.pin(implementation),statePin(foundation),statePin(state.binding()),statePin(request),
                status,eligibility.mode(),evaluatedAt,List.of(status==SystemGraphCertificate.Status.QUALIFIED?"RESEARCH_AND_IMPLEMENTATION_VERIFIED":"HISTORICAL_REPLAY_ONLY"));
    }
    public static void verify(RuleManifest manifest,RuleQualificationReceipt receipt,RulePolicyContext context,
                              ResearchArtifactReader artifacts,Instant at)throws IOException {
        if(!receipt.ruleKey().equals(manifest.key())||!receipt.manifestSha256().equals(RuleRegistry.digest(manifest))
                ||!receipt.evaluatedAt().equals(at))throw new IOException("receipt rule/time binding mismatch");
        var pins=new TreeSet<String>();
        var eligibility=ResearchCodec.decode(ResearchCodec.read(receipt.eligibility(),artifacts,pins),ResearchEligibility.class);
        var implementation=ResearchCodec.decode(ResearchCodec.read(receipt.implementationReport(),artifacts,pins),RuleImplementationQualification.class);
        var foundation=ResearchCodec.JSON.readValue(ResearchCodec.read(receipt.foundationCertificate(),artifacts,pins),SystemGraphCertificate.class);
        var binding=ResearchCodec.JSON.readValue(ResearchCodec.read(receipt.stateBinding(),artifacts,pins),SystemStateView.Binding.class);
        var request=ResearchCodec.JSON.readValue(ResearchCodec.read(receipt.request(),artifacts,pins),RuleRequest.class);
        if(!binding.equals(foundation.binding())||!binding.equals(request.state())||receipt.mode()!=eligibility.mode())throw new IOException("receipt state/mode mismatch");
        var result=checked(manifest,eligibility,implementation,foundation,request,context,artifacts,at);
        if(result!=receipt.qualification())throw new IOException("forged qualification status");
        var expectedReason=result==SystemGraphCertificate.Status.QUALIFIED?"RESEARCH_AND_IMPLEMENTATION_VERIFIED":"HISTORICAL_REPLAY_ONLY";
        if(!receipt.reasons().equals(List.of(expectedReason)))throw new IOException("receipt reasons mismatch");
    }
    private static SystemGraphCertificate.Status checked(RuleManifest manifest,ResearchEligibility eligibility,
            RuleImplementationQualification implementation,SystemGraphCertificate foundation,RuleRequest request,
            RulePolicyContext context,ResearchArtifactReader artifacts,Instant at)throws IOException {
        if(!manifest.schema().equals("athena-rule/3"))throw new IOException("historical manifest cannot issue current qualification");
        if(manifest.retired())throw new IOException("retired rule");
        var pins=new TreeSet<String>();
        var policy=ResearchCodec.decode(ResearchCodec.read(manifest.research().reviewPolicy(),artifacts,pins),RuleReviewPolicy.class);
        var fresh=new ScientificRuleResearchGate().evaluate(manifest,policy,context,artifacts,at,eligibility.mode());
        if(!fresh.equals(eligibility)||!fresh.eligible())throw new IOException("research ineligible or receipt changed: "+fresh.reasons());
        if(!request.manifestKey().equals(manifest.key())||!request.manifestSha256().equals(RuleRegistry.digest(manifest)))throw new IOException("request rule pin mismatch");
        if(!implementation.ruleKey().equals(manifest.key())||!implementation.definitionSha256().equals(manifest.research().definitionSha256())
                ||!implementation.domainSha256().equals(manifest.research().domain().sha256()))throw new IOException("implementation definition/domain mismatch");
        if(!policy.authorizedReviewers().contains(implementation.reviewer())||implementation.completedAt().isAfter(at))throw new IOException("implementation authority/time mismatch");
        if(implementation.implementationPins().isEmpty()||implementation.fixturePins().isEmpty())throw new IOException("implementation/fixture provenance missing");
        var checks=new HashMap<String,RuleImplementationQualification.Check>();implementation.checkResults().forEach(c->checks.put(c.id(),c));
        if(implementation.checkResults().stream().anyMatch(c->!c.passed())||policy.requiredQualificationChecks().stream().anyMatch(id->!checks.containsKey(id)))throw new IOException("implementation checks missing/failed");
        ResearchCodec.verifySources(implementation,artifacts,pins);
        // These input documents must already be admitted; constructing a public record is not sufficient.
        ResearchCodec.read(ResearchCodec.pin(eligibility),artifacts,pins);
        ResearchCodec.read(ResearchCodec.pin(implementation),artifacts,pins);
        ResearchCodec.read(statePin(foundation),artifacts,pins);
        ResearchCodec.read(statePin(foundation.binding()),artifacts,pins);
        ResearchCodec.read(statePin(request),artifacts,pins);
        if(!foundation.binding().equals(request.state())||foundation.recordedAt().isAfter(at))throw new IOException("foundation state/time mismatch");
        for(var capability:manifest.requiredCapabilities())if(foundation.capabilities().get(capability).status()!=SystemGraphCertificate.Status.QUALIFIED)
            throw new IOException("foundation prerequisite unavailable: "+capability);
        for(var invalidation:context.invalidations())if(!invalidation.effectiveAt().isAfter(at)&&pins.contains(invalidation.targetSha256()))throw new IOException("qualification input invalidated");
        return eligibility.mode()==QualificationMode.CURRENT?SystemGraphCertificate.Status.QUALIFIED:SystemGraphCertificate.Status.NOT_EVALUATED;
    }
    private static RuleManifest.Source statePin(Object value){String h=EvidenceExchange.sha256(SystemStateView.bytes(value));return new RuleManifest.Source("sha256:"+h,h,"Immutable historical-codec state/request/certificate");}
}
