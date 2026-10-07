package totah.lab.athena.system.rules;

import totah.lab.athena.system.*;
import totah.lab.athena.system.rules.research.*;
import totah.lab.mnemosyne.*;
import java.time.Instant;
import java.util.*;
import static totah.lab.athena.system.rules.EventPayload.*;

/** S1-specific receipt checks using the existing gate; no issuer, clock or receipt is created here. */
final class S1Qualification {
    private S1Qualification() { }
    static Optional<Instant> current(RuleManifest m,SystemStateView s,RuleRequest request,Map<String,EvidenceEnvelope> inputs)throws Exception {
        if(!m.schema().equals("athena-rule/3")||m.retired())return Optional.empty();
        var receipts=inputs.values().stream().filter(e->e.evidenceType().equals("athena:rule-qualification-receipt")).toList();
        var byHash=new HashMap<String,byte[]>();for(var e:inputs.values())byHash.put(e.payloadSha256(),e.readPayload());
        ResearchArtifactReader reader=p->{var b=byHash.get(p.sha256());if(b==null)throw new java.io.IOException("missing selected S1 qualification dependency");return b.clone();};
        var contexts=new HashSet<RulePolicyContext>();
        for(var e:inputs.values())if(e.evidenceType().equals("athena:rule-policy-context")){var context=ResearchDocuments.decode(e.readPayload(),RulePolicyContext.class);if(context.policy().equals(m.research().reviewPolicy()))contexts.add(context);}
        if(contexts.size()!=1)return Optional.empty(); // Never select a convenient non-revoking context.
        var times=new TreeSet<Instant>();
        for(var e:receipts) {
            var receipt=ResearchDocuments.decode(e.readPayload(),RuleQualificationReceipt.class);
            if(!receipt.ruleKey().equals(m.key())||!receipt.manifestSha256().equals(RuleRegistry.digest(m)))continue;
            require(JSON.readTree(reader.read(receipt.stateBinding())).equals(JSON.valueToTree(s.binding())),"S1 receipt state mismatch");
            if(request!=null)require(JSON.readTree(reader.read(receipt.request())).equals(JSON.valueToTree(request)),"S1 receipt selection mismatch");
            if(receipt.mode()!=QualificationMode.CURRENT||receipt.qualification()!=SystemGraphCertificate.Status.QUALIFIED)continue;
            for(var context:contexts) {
                // Unrelated contexts cannot replace the reviewed policy context. The gate checks their exact pins.
                try {RuleQualification.verify(m,receipt,context,reader,receipt.evaluatedAt());times.add(receipt.evaluatedAt());}
                catch(java.io.IOException ineligible) { /* Retain all supplied evidence; no qualification from this context. */ }
            }
        }
        require(times.size()<=1,"conflicting S1 invocation times");return times.stream().findFirst();
    }
    static boolean scope(EvidenceEnvelope envelope,SystemStateView s,Map<String,EvidenceEnvelope> artifacts,Instant at)throws Exception {
        var i=(EvidenceInterpretation)new EvidenceExchange().decodeRecord(envelope.readPayload());
        for(var p:i.inputs())pinned(JSON.valueToTree(p),artifacts);
        var protocol=pinned(JSON.readTree(i.measurements().get("sourceProtocol")),artifacts);
        boolean qualified=false;
        for(var e:artifacts.values())if(e.evidenceType().equals("athena:rule-manifest")) {
            // Source scope is reviewed as an exact attributed artifact. A generic reader receipt
            // cannot bless another assertion or omitted connection record without a new review.
            var m=ResearchDocuments.decode(e.readPayload(),RuleManifest.class);
            if(!m.ruleId().equals("ATHENA.I03.SP3_SOURCE_SCOPE")||!m.schema().equals("athena-rule/3"))continue;
            if(m.scientificSources().stream().noneMatch(p->p.sha256().equals(protocol.payloadSha256()))
                    ||m.scientificSources().stream().noneMatch(p->p.sha256().equals(envelope.payloadSha256())))continue;
            var time=current(m,s,null,artifacts);
            qualified |=time.isPresent()&&time.get().equals(at)&&i.recordedAt().equals(at);
        }
        return qualified;
    }
}
