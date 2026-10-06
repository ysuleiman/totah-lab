package totah.lab.daedalus.system;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import totah.lab.athena.system.*;
import totah.lab.athena.system.rules.*;
import totah.lab.mnemosyne.*;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static totah.lab.daedalus.system.HbondCandidateFixtures.*;
import static totah.lab.daedalus.system.B01FunctionalGroupAcceptanceTest.JSON;
import static totah.lab.daedalus.system.AthenaScientificRulesAcceptanceTest.*;

class HbondCandidateIntegrityTest {
    @TempDir Path temp;
    @Test void inputOrderingAndIndependentSourceStateRemainDeterministic() throws Exception {
        var s=sample("DONOR.AMIDE_NH2","AMIDE_CARBONYL_O",3,180,true,"complete");
        var reversed=new ArrayList<>(s.inputs());Collections.reverse(reversed);
        assertEquals(collect(s),collect(new Sample(s.state(),s.manifest(),s.request(),reversed)));
    }
    @Test void noSelectedInputsDoesNotBecomeAbsence() throws Exception {
        var s=sample("DONOR.ALCOHOL","ETHER_O",3,180,true,"complete");
        var r=collect(new Sample(s.state(),s.manifest(),s.request(),List.of()));
        assertEquals("UNKNOWN_INCONCLUSIVE",r.get("assessment").asText());
    }
    @Test void frameAndBudgetCannotManufactureNegative() throws Exception {
        var s=sample("DONOR.AMMONIUM_PRIMARY","ETHER_O",5,180,true,"complete");
        var state=s.state();
        // Same authoritative coordinates; frame assertion is deliberately absent.
        var unqualified=new SystemStateView(state.identity(),state.graph(),state.components(),state.sources(),state.cofactors(),state.charges(),false,true,state.limitations());
        // State bindings differ: using old evidence is rejected rather than transplanted.
        assertThrows(Exception.class,()->collect(new Sample(unqualified,s.manifest(),s.request(),s.inputs())));
        var r=s.request();var bounded=new RuleRequest(r.state(),r.manifestKey(),r.manifestSha256(),r.atoms(),r.first(),r.second(),r.radiusAngstrom(),r.maximumHops(),r.maximumNodes(),1);
        var collector=RuleAnalyzers.collector(s.manifest(),bounded,B01FunctionalGroupAcceptanceTest.BACKEND);
        var result=JSON.readTree(collector.analyze(state,s.inputs(),Map.of()).getFirst().measurements().get("payload"));
        assertEquals("UNKNOWN_INCONCLUSIVE",pair(result,"DONOR.AMMONIUM_PRIMARY","ETHER_O").get("assessment").asText());
    }
    @Test void sourceReportTamperingAndDuplicatesAreRejected() throws Exception {
        var s=sample("DONOR.ALCOHOL","ETHER_O",3,180,true,"complete");
        var collector=RuleAnalyzers.collector(s.manifest(),s.request(),B01FunctionalGroupAcceptanceTest.BACKEND);
        var duplicate=new ArrayList<>(s.inputs());duplicate.add(s.inputs().getFirst());
        assertThrows(Exception.class,()->collector.analyze(s.state(),duplicate,Map.of()));
        var e=s.inputs().getFirst();var n=(ObjectNode)JSON.readTree(e.readPayload());n.put("definitionDigest","0".repeat(64));
        var changed=SystemQualificationPipeline.envelope(ref(ScientificReference.Kind.ACTIVITY,"tamper"),"source",e.evidenceType(),SystemStateView.bytes(n),e.method(),s.state().subject(),T,List.of());
        var inputs=new ArrayList<>(s.inputs());inputs.set(0,changed);
        assertThrows(Exception.class,()->collector.analyze(s.state(),inputs,Map.of()));
    }
    @Test void parametersCannotSilentlyWidenApprovedDefinition() throws Exception {
        var n=(ObjectNode)JSON.readTree(Files.readAllBytes(MANIFEST));
        ((ObjectNode)n.get("parameters").get("angle")).put("value","120");
        assertThrows(java.io.IOException.class,()->RuleRegistry.decode(SystemStateView.bytes(n)));
    }
    @Test void pipelinePreservesInputsCollectionAndEvaluationInJournal() throws Exception {
        var s=sample("DONOR.AMINE_PRIMARY","ETHER_O",3,180,true,"complete");
        var catalog=new EvidenceSnapshotCatalog(temp);var p=pipeline();var collector=RuleAnalyzers.collector(s.manifest(),s.request(),B01FunctionalGroupAcceptanceTest.BACKEND);
        var collected=p.run(catalog,Optional.empty(),s.state(),s.inputs(),Map.of(),List.of(collector),ref(ScientificReference.Kind.ACTIVITY,"i02-collect"),T);
        var history=catalog.read(collected.catalogSnapshot()).orElseThrow().history();
        for(var input:s.inputs())assertEquals(input,history.envelopes().get(input.reference()));
        var finding=history.interpretations().values().stream().filter(i->i.evaluator().equals(collector.method())).findFirst().orElseThrow();
        assertEquals(EvidenceInterpretation.Status.SUPPORTED_PRESENT,finding.status(),finding.toString());
        var raw=SystemQualificationPipeline.envelope(ref(ScientificReference.Kind.ACTIVITY,"i02"),"raw","athena:rule-measurements",finding.measurements().get("payload").getBytes(java.nio.charset.StandardCharsets.UTF_8),collector.method(),s.state().subject(),T,List.of());
        var evaluator=RuleAnalyzers.evaluator(s.manifest(),s.request());var inputs=new ArrayList<>(s.inputs());inputs.add(raw);
        var evaluated=p.run(catalog,Optional.of(collected.catalogSnapshot()),s.state(),inputs,Map.of(),List.of(evaluator),ref(ScientificReference.Kind.ACTIVITY,"i02-evaluate"),T);
        var after=new EvidenceSnapshotCatalog(temp).read(evaluated.catalogSnapshot()).orElseThrow().history();
        assertEquals(raw,after.envelopes().get(raw.reference()));
        assertTrue(after.interpretations().values().stream().anyMatch(i->i.evaluator().equals(evaluator.method())&&i.status()==EvidenceInterpretation.Status.NOT_EVALUATED));
        for(var old:history.envelopes().entrySet())assertEquals(old.getValue(),after.envelopes().get(old.getKey()));
    }
}
