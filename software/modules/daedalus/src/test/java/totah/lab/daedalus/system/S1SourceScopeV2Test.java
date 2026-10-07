package totah.lab.daedalus.system;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import totah.lab.athena.system.*;
import totah.lab.mnemosyne.*;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Approved additive /2. Source connection identity is retained in the exact reviewed record and original bytes. */
class S1SourceScopeV2Test {
    @TempDir Path temp;
    S1NitrogenAcceptanceTest.Fixture qualify(String variant)throws Exception {var f=new S1NitrogenAcceptanceTest.Fixture(variant);var q=S1ResearchFixtures.qualify(f.manifest,f.state,f.selected,temp,"s1");f.manifest=q.manifest();f.inputs.addAll(q.artifacts());S1QualifiedProducerTest.qualifyScope(f,temp);return f;}
    @ParameterizedTest @ValueSource(strings={"primary","unknown-scope","known-connection"})
    void completeUnknownAndKnownAreDistinct(String variant)throws Exception {var f=qualify(variant);var r=f.result().getFirst();assertEquals(switch(variant){case "primary"->"SP3";case "known-connection"->"UNSUPPORTED";default->"UNKNOWN";},r.measurements().get("hybridization"));assertEquals(switch(variant){case "primary"->EvidenceInterpretation.Status.SUPPORTED_PRESENT;case "known-connection"->EvidenceInterpretation.Status.UNSUPPORTED;default->EvidenceInterpretation.Status.UNKNOWN_INCONCLUSIVE;},r.status());if(variant.equals("known-connection")){assertTrue(r.reasons().toString().contains("source-dative-17"));assertTrue(r.reasons().toString().contains("source-Zn:external"));}}
    @Test void knownWithoutIndependentWitnessReviewIsNotEvaluated()throws Exception {var f=new S1NitrogenAcceptanceTest.Fixture("known-connection");var q=S1ResearchFixtures.qualify(f.manifest,f.state,f.selected,temp,"s1");f.manifest=q.manifest();f.inputs.addAll(q.artifacts());assertEquals(EvidenceInterpretation.Status.NOT_EVALUATED,f.result().getFirst().status());}
    @Test void contraryCompleteAndKnownPreservedAsUnknown()throws Exception {var f=new S1NitrogenAcceptanceTest.Fixture("known-connection");f.scope(f.coverages.getFirst(),"COMPLETE_ORDINARY_COVALENT_NO_COORDINATION",false,false);var q=S1ResearchFixtures.qualify(f.manifest,f.state,f.selected,temp,"s1");f.manifest=q.manifest();f.inputs.addAll(q.artifacts());S1QualifiedProducerTest.qualifyScope(f,temp);var r=f.result().getFirst();assertEquals("UNKNOWN",r.measurements().get("hybridization"));assertEquals(EvidenceInterpretation.Status.UNKNOWN_INCONCLUSIVE,r.status());assertTrue(r.reasons().toString().contains("conflicting source-scope"));}
    @Test void missingSourceWitnessCannotBeQualified()throws Exception {var f=qualify("known-connection");var scope=f.inputs.stream().filter(e->e.evidenceType().equals("athena:event-source")).findFirst().orElseThrow();var i=(EvidenceInterpretation)new EvidenceExchange().decodeRecord(scope.readPayload());var source=i.inputs().get(2);f.inputs.removeIf(e->e.reference().equals(source.reference()));assertThrows(IllegalArgumentException.class,f::result);}
    @Test void nearbyMetalWithoutConnectionWitnessCannotCreateExclusion()throws Exception {var f=qualify("primary");f.add("athena:source-artifact","Unconnected metal elsewhere in source; no incident connection asserted".getBytes(),f.inputs.getFirst().method());assertEquals("SP3",f.result().getFirst().measurements().get("hybridization"));}
    @ParameterizedTest @ValueSource(strings={"DATIVE","COORDINATE","ZERO_ORDER","METAL_CONNECTION"})
    void exactExplicitSourceConnectionKindsAreRetained(String kind)throws Exception {
        var f=new S1NitrogenAcceptanceTest.Fixture("known-connection");var scope=f.inputs.stream().filter(e->e.evidenceType().equals("athena:event-source")).findFirst().orElseThrow();var i=(EvidenceInterpretation)new EvidenceExchange().decodeRecord(scope.readPayload());var oldSource=f.inputs.stream().filter(e->e.reference().equals(i.inputs().get(2).reference())).findFirst().orElseThrow();
        var original=(com.fasterxml.jackson.databind.node.ObjectNode)B01FunctionalGroupAcceptanceTest.JSON.readTree(oldSource.readPayload());((com.fasterxml.jackson.databind.node.ObjectNode)original.path("explicitOriginalConnection")).put("kind",kind).put("identity","source-connection-"+kind);
        f.inputs.remove(oldSource);var fresh=f.add("athena:source-artifact",SystemStateView.bytes(original),oldSource.method());var deps=new ArrayList<>(i.inputs());deps.set(2,new EvidenceInterpretation.Input(fresh.reference(),fresh.payloadSha256()));
        var next=new EvidenceInterpretation(i.reference(),deps,i.evaluator(),i.configuration(),i.subjects(),i.status(),i.measurements(),List.of("Exact source-connection-"+kind+": component-source-N:a1 -> source-Zn:external; explicitly "+kind),i.limitations(),i.supersedes(),i.recordedAt());f.inputs.remove(scope);f.add("athena:event-source",new EvidenceExchange().encodeRecord(next),next.evaluator());
        var q=S1ResearchFixtures.qualify(f.manifest,f.state,f.selected,temp,"s1");f.manifest=q.manifest();f.inputs.addAll(q.artifacts());S1QualifiedProducerTest.qualifyScope(f,temp);var r=f.result().getFirst();assertEquals(EvidenceInterpretation.Status.UNSUPPORTED,r.status());assertTrue(r.reasons().toString().contains("source-connection-"+kind));assertEquals(1,f.state.components().getFirst().chemistry().bonds().size(),"no nonordinary source edge converted into a covalent edge");
    }
    public static void main(String[] args)throws Exception {var t=new S1SourceScopeV2Test();t.temp=Files.createDirectories(Path.of(args[0]));Files.write(Path.of(args[1]),SystemStateView.bytes(t.qualify("known-connection").result()));}
}
