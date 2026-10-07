package totah.lab.daedalus.system;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import totah.lab.athena.system.rules.research.*;
import totah.lab.mnemosyne.*;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static totah.lab.athena.system.rules.research.ResearchGateAcceptanceTest.AT;

class S1AuthorityBindingTest {
    @TempDir Path temp;
    S1NitrogenAcceptanceTest.Fixture qualified()throws Exception {var f=new S1NitrogenAcceptanceTest.Fixture("primary");var q=S1ResearchFixtures.qualify(f.manifest,f.state,f.selected,temp,"s1");f.manifest=q.manifest();f.inputs.addAll(q.artifacts());S1QualifiedProducerTest.qualifyScope(f,temp);return f;}
    @Test void genericReceiptCannotApproveChangedScopeAssertion()throws Exception {
        var f=qualified();assertEquals(EvidenceInterpretation.Status.SUPPORTED_PRESENT,f.result().getFirst().status());
        var envelope=f.inputs.stream().filter(e->e.evidenceType().equals("athena:event-source")).findFirst().orElseThrow();var old=(EvidenceInterpretation)new EvidenceExchange().decodeRecord(envelope.readPayload());
        var changed=new EvidenceInterpretation(old.reference(),old.inputs(),old.evaluator(),old.configuration(),old.subjects(),old.status(),old.measurements(),List.of("different source assertion, not reviewed"),old.limitations(),old.supersedes(),old.recordedAt());
        f.inputs.remove(envelope);f.add("athena:event-source",new EvidenceExchange().encodeRecord(changed),changed.evaluator());assertEquals(EvidenceInterpretation.Status.NOT_EVALUATED,f.result().getFirst().status());
    }
    @Test void conflictingPolicyContextsCannotSelectConvenientAuthority()throws Exception {
        var f=qualified();var e=f.inputs.stream().filter(x->x.evidenceType().equals("athena:rule-policy-context")).findFirst().orElseThrow();var c=ResearchDocuments.decode(e.readPayload(),RulePolicyContext.class);
        var invalid=new RulePolicyContext(c.schema(),c.policy(),List.of(new RulePolicyContext.Invalidation(f.manifest.research().domain().sha256(),AT,"synthetic revocation",c.contextSource())),c.asOf(),c.issuer(),c.contextSource());
        f.add("athena:rule-policy-context",ResearchDocuments.encode(invalid),e.method());assertEquals(EvidenceInterpretation.Status.NOT_EVALUATED,f.result().getFirst().status());
    }
    @Test void scopeAuthorityDoesNotInsertMissingElectronicFacts()throws Exception {var f=new S1NitrogenAcceptanceTest.Fixture("missing-none");var q=S1ResearchFixtures.qualify(f.manifest,f.state,f.selected,temp,"s1");f.manifest=q.manifest();f.inputs.addAll(q.artifacts());S1QualifiedProducerTest.qualifyScope(f,temp);var r=f.result().getFirst();assertEquals("UNKNOWN",r.measurements().get("hybridization"));assertEquals(EvidenceInterpretation.Status.UNKNOWN_INCONCLUSIVE,r.status());}
    @Test void legacyScopeRejectsUnapprovedConnectionToken()throws Exception {
        var f=new S1NitrogenAcceptanceTest.Fixture("primary");var e=f.inputs.stream().filter(x->x.evidenceType().equals("athena:event-source")).findFirst().orElseThrow();var old=(EvidenceInterpretation)new EvidenceExchange().decodeRecord(e.readPayload());var values=new TreeMap<>(old.measurements());values.put("connectionCoverage","KNOWN_NON_ORDINARY_CONNECTION");var changed=new EvidenceInterpretation(old.reference(),old.inputs(),old.evaluator(),old.configuration(),old.subjects(),old.status(),values,old.reasons(),old.limitations(),old.supersedes(),old.recordedAt());f.inputs.remove(e);f.add("athena:event-source",new EvidenceExchange().encodeRecord(changed),changed.evaluator());assertThrows(IllegalArgumentException.class,f::result);
    }
    @Test void originalSourceCannotBeDroppedFromScopeInputs()throws Exception {
        var f=new S1NitrogenAcceptanceTest.Fixture("primary");var e=f.inputs.stream().filter(x->x.evidenceType().equals("athena:event-source")).findFirst().orElseThrow();var old=(EvidenceInterpretation)new EvidenceExchange().decodeRecord(e.readPayload());var changed=new EvidenceInterpretation(old.reference(),old.inputs().subList(0,2),old.evaluator(),old.configuration(),old.subjects(),old.status(),old.measurements(),old.reasons(),old.limitations(),old.supersedes(),old.recordedAt());f.inputs.remove(e);f.add("athena:event-source",new EvidenceExchange().encodeRecord(changed),changed.evaluator());assertThrows(IllegalArgumentException.class,f::result);
    }
    @Test void foreignScopeReceiptCannotQualifyS1Itself()throws Exception {var f=qualified();f.inputs.removeIf(e->e.evidenceType().equals("athena:rule-qualification-receipt")&&e.reference().id().startsWith("s1/"));assertEquals(EvidenceInterpretation.Status.NOT_EVALUATED,f.result().getFirst().status());}
    @Test void oldReceiptCannotMovePastReviewExpiry()throws Exception {var f=qualified();var e=f.inputs.stream().filter(x->x.evidenceType().equals("athena:rule-qualification-receipt")&&x.reference().id().startsWith("s1/")).findFirst().orElseThrow();var r=ResearchDocuments.decode(e.readPayload(),RuleQualificationReceipt.class);var expired=new RuleQualificationReceipt(r.schema(),r.ruleKey(),r.manifestSha256(),r.eligibility(),r.implementationReport(),r.foundationCertificate(),r.stateBinding(),r.request(),r.qualification(),r.mode(),AT.plusSeconds(60),r.reasons());f.inputs.remove(e);f.add("athena:rule-qualification-receipt",ResearchDocuments.encode(expired),e.method());assertEquals(EvidenceInterpretation.Status.NOT_EVALUATED,f.result().getFirst().status());}

}
