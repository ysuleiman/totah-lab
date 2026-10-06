package totah.lab.daedalus.system;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import totah.lab.athena.system.*;
import totah.lab.athena.system.rules.*;
import totah.lab.mnemosyne.*;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static totah.lab.daedalus.system.B01FunctionalGroupAcceptanceTest.*;
import static totah.lab.daedalus.system.AthenaScientificRulesAcceptanceTest.*;

/** Engineering diagnostics only; never fabricates a qualified SP3 producer or receipt. */
class ImplicitHProxyAcceptanceTest {
    static final Path MANIFEST=Path.of("software/modules/athena/src/main/resources/totah/lab/athena/system/rules/implicit-h-proxy-v1/ATHENA.I03.HEAVY_ATOM_DIRECTIONAL_PROXY_SP3_AMINES.rule.json");
    static final class Fixture {
        final SystemStateView state;RuleManifest manifest;final ObjectNode plan;final List<EvidenceEnvelope> artifacts;int seq;
        Fixture(String d,String a,boolean explicit,String mode)throws Exception{
            this(HbondCandidateFixtures.sample("DONOR."+d,a,3,109.5,explicit,mode));
        }
        Fixture(HbondCandidateFixtures.Sample base)throws Exception{
            state=base.state();manifest=RuleRegistry.decode(Files.readAllBytes(MANIFEST));artifacts=new ArrayList<>(base.inputs());
            plan=JSON.createObjectNode();plan.put("schema","athena-implicit-h-proxy-plan/1");plan.set("stateBinding",node(state.binding()));
            plan.set("donorAtoms",node(List.of(ref(1,"a1"))));plan.set("acceptorAtoms",node(List.of(ref(2,"a1"))));plan.putArray("artifacts");plan.putArray("hybridizationEvidence");plan.putArray("inventoryCoverage");
        }
        RuleRequest request(){return new RuleRequest(state.binding(),manifest.key(),RuleRegistry.digest(manifest),List.of(),List.of(),List.of(),0.1,0,1000,10000);}
        EvidenceEnvelope envelope(String type,byte[] bytes,ScientificReference method){return SystemQualificationPipeline.envelope(ref(ScientificReference.Kind.ACTIVITY,"i03-fixture"),"e"+seq++,type,bytes,method,state.subject(),T,List.of("synthetic unqualified source assertion; not a scientific receipt"));}
        JsonNode add(byte[] bytes,ScientificReference method){var e=envelope("athena:event-source",bytes,method);artifacts.add(e);return WaterBridgeFixtures.pin(e);}
        void assignment(String value,boolean foreign)throws Exception{
            var method=ref(ScientificReference.Kind.METHOD,"unqualified-sp3-annotation-fixture");
            var protocol=add(SystemStateView.bytes(Map.of("unqualifiedProtocol",seq)),method);var raw=add(SystemStateView.bytes(Map.of("unqualifiedReport",seq)),method);
            var i=new EvidenceInterpretation(ref(ScientificReference.Kind.EVIDENCE_INTERPRETATION,"annotation"+seq),List.of(input(protocol),input(raw)),method,Map.of(),List.of(state.subject()),EvidenceInterpretation.Status.SUPPORTED_PRESENT,
                    Map.of("proposition","SOURCE_ATOM_HYBRIDIZATION","stateBinding",foreign?"foreign":EventCoverageFixture.canonical(state.binding()),"atom",EventCoverageFixture.canonical(ref(1,"a1")),"hybridization",value,"assignmentProtocol",EventCoverageFixture.canonical(protocol)),List.of("unqualified"),List.of(),Optional.empty(),T);
            ((ArrayNode)plan.get("hybridizationEvidence")).add(add(new EvidenceExchange().encodeRecord(i),method));
        }
        static EvidenceInterpretation.Input input(JsonNode pin)throws Exception{return new EvidenceInterpretation.Input(JSON.treeToValue(pin.get("reference"),ScientificReference.class),pin.get("sha256").asText());}
        void coverage(EvidenceInterpretation.Status status)throws Exception{
            var protocols=new TreeMap<String,JsonNode>();for(var pin:plan.get("hybridizationEvidence"))for(var e:artifacts)if(WaterBridgeFixtures.pin(e).equals(pin)){
                var i=(EvidenceInterpretation)new EvidenceExchange().decodeRecord(e.readPayload());var p=JSON.readTree(i.measurements().get("assignmentProtocol"));protocols.put(EventCoverageFixture.canonical(p),p);}
            String scope=EventCoverageFixture.hash(Map.of("definitionSha256",RuleRegistry.digest(manifest),"stateBinding",state.binding(),"donorAtoms",plan.get("donorAtoms"),"acceptorAtoms",plan.get("acceptorAtoms"),"classPairs",JSON.readTree(manifest.parameters().get("classPairs").value()),"assignmentProtocolPins",protocols.values()));
            var method=ref(ScientificReference.Kind.METHOD,"inventory-fixture");var raw=add(SystemStateView.bytes(Map.of("inventory",seq)),method);
            var i=new EvidenceInterpretation(ref(ScientificReference.Kind.EVIDENCE_INTERPRETATION,"inventory"+seq),List.of(input(raw)),method,Map.of(),List.of(state.subject()),status,Map.of("proposition","COMPLETE_EVENT_SCOPE","stateBinding",EventCoverageFixture.canonical(state.binding()),"eventScope",scope),List.of("inventory is not chemistry"),List.of(),Optional.empty(),T);
            ((ArrayNode)plan.get("inventoryCoverage")).add(add(new EvidenceExchange().encodeRecord(i),method));
        }
        List<EvidenceEnvelope> inputs(){((ArrayNode)plan.get("artifacts")).removeAll();artifacts.forEach(e->((ArrayNode)plan.get("artifacts")).add(WaterBridgeFixtures.pin(e)));
            var out=new ArrayList<>(artifacts);out.add(SystemQualificationPipeline.envelope(ref(ScientificReference.Kind.ACTIVITY,"i03-fixture"),"plan","athena:implicit-h-proxy-plan",SystemStateView.bytes(plan),ref(ScientificReference.Kind.METHOD,"selection-fixture"),state.subject(),T,List.of()));return out;}
        JsonNode result()throws Exception{return result(inputs());}
        JsonNode result(List<EvidenceEnvelope> inputs)throws Exception{
            byte[] before=SystemStateView.bytes(state.snapshot());var collector=RuleAnalyzers.collector(manifest,request());var finding=collector.analyze(state,inputs,Map.of("trustSP3","true")).getFirst();
            var report=JSON.readTree(finding.measurements().get("payload"));var raw=envelope("athena:rule-measurements",SystemStateView.bytes(report),collector.method());
            var exchange=new EvidenceExchange();var supplied=new ArrayList<>(inputs);supplied.add((EvidenceEnvelope)exchange.decodeRecord(exchange.encodeRecord(raw)));
            var evaluated=RuleAnalyzers.evaluator(manifest,request()).analyze(state,supplied,Map.of("qualifiedProducer","true")).getFirst();
            assertEquals(EvidenceInterpretation.Status.NOT_EVALUATED,evaluated.status());assertEquals(report,JSON.readTree(evaluated.measurements().get("payload")));assertArrayEquals(before,SystemStateView.bytes(state.snapshot()));
            assertEquals("NOT_EVALUATED",report.get("assessment").asText());assertFalse(report.get("complete").asBoolean());return report;
        }
    }
    static Fixture fixture()throws Exception{return new Fixture("AMINE_PRIMARY","AMINE_PRIMARY",false,"complete");}
    @ParameterizedTest @ValueSource(strings={"PRIMARY:PRIMARY","PRIMARY:SECONDARY","PRIMARY:TERTIARY","SECONDARY:PRIMARY","SECONDARY:SECONDARY","SECONDARY:TERTIARY"})
    void sixPairsRemainUnqualified(String pair)throws Exception{
        var p=pair.split(":");var f=new Fixture("AMINE_"+p[0],"AMINE_"+p[1],false,"complete");var report=f.result();assertEquals(1,report.get("candidates").size(),report.toPrettyString());
        var c=report.get("candidates").get(0);assertEquals("HEAVY_ATOM_DIRECTIONAL_PROXY",c.get("geometryKind").asText());assertEquals("NOT_EVALUATED",c.get("assessment").asText());
        assertFalse(c.get("geometry").isNull(),c.toPrettyString());assertTrue(c.get("donorMinimumDeviationDegrees").isNumber());
        assertEquals(p[0].equals("PRIMARY")?1:2,c.get("donorNeighbors").size());assertEquals(p[1].equals("PRIMARY")?1:p[1].equals("SECONDARY")?2:3,c.get("acceptorNeighbors").size());
        for(var op:c.get("geometry").get("plan").get("operations"))assertTrue(Set.of("DISTANCE","ANGLE").contains(op.get("kind").asText()));
        assertEquals(0,c.get("hybridizationPins").size());assertFalse(c.has("hydrogen"));assertFalse(c.has("DHA"));
    }
    @ParameterizedTest @ValueSource(strings={"SP3","SP2","SP","UNKNOWN"}) void callerAssignmentsAreNotAuthority(String value)throws Exception{var f=fixture();f.assignment(value,false);var r=f.result();assertTrue(r.get("reasons").toString().contains("unqualified assignment"));assertEquals(1,r.get("candidates").get(0).get("hybridizationPins").size());}
    @Test void conflictingAssignmentsPreserved()throws Exception{var f=fixture();f.assignment("SP3",false);f.assignment("SP2",false);var r=f.result();assertTrue(r.get("reasons").toString().contains("conflicting unqualified"));assertEquals(2,r.get("candidates").get(0).get("hybridizationPins").size());}
    @Test void completeInventoryCannotActivateChemistry()throws Exception{var f=fixture();f.coverage(EvidenceInterpretation.Status.SUPPORTED_PRESENT);f.result();}
    @Test void conflictingCoverageCannotActivateChemistry()throws Exception{var f=fixture();f.coverage(EvidenceInterpretation.Status.SUPPORTED_PRESENT);f.coverage(EvidenceInterpretation.Status.ABSENT_FALSE);assertTrue(f.result().get("reasons").toString().contains("exhaustiveness not established"));}
    @Test void explicitDonorHNeverDeleted()throws Exception{var f=new Fixture("AMINE_PRIMARY","AMINE_PRIMARY",true,"complete");var r=f.result();assertTrue(r.get("candidates").get(0).get("geometry").isNull());}
    @ParameterizedTest @ValueSource(strings={"incomplete","unknownH"}) void missingChemistryIsNotAbsence(String mode)throws Exception{new Fixture("AMINE_PRIMARY","AMINE_PRIMARY",false,mode).result();}
    @Test void emptyReportsAreNotAbsence()throws Exception{var f=fixture();f.artifacts.clear();assertEquals(0,f.result().get("candidates").size());}
    @Test void foreignAssignmentRejected()throws Exception{var f=fixture();f.assignment("SP3",true);assertThrows(IllegalArgumentException.class,f::result);}
    @ParameterizedTest @ValueSource(strings={"unknown-field","overlap","duplicate","empty","foreign-state","missing-pin"}) void malformedPlansRejected(String kind)throws Exception{
        var f=fixture();switch(kind){case "unknown-field"->f.plan.put("trustedSP3",true);case "overlap"->f.plan.set("acceptorAtoms",f.plan.get("donorAtoms"));case "duplicate"->((ArrayNode)f.plan.get("donorAtoms")).add(f.plan.get("donorAtoms").get(0));case "empty"->((ArrayNode)f.plan.get("acceptorAtoms")).removeAll();case "foreign-state"->((ObjectNode)f.plan.get("stateBinding")).put("stateSha256","0".repeat(64));case "missing-pin"->((ArrayNode)f.plan.get("hybridizationEvidence")).add(node(Map.of("reference",ref(ScientificReference.Kind.EVIDENCE_ENVELOPE,"missing"),"sha256","0".repeat(64))));}
        assertThrows(IllegalArgumentException.class,f::result);
    }
    @Test void duplicateEnvelopeIdempotent()throws Exception{var f=fixture();var inputs=f.inputs();var expected=f.result(inputs);inputs.add(inputs.getFirst());assertEquals(expected,f.result(inputs));}
    @Test void inputOrderingDoesNotChangeDiagnostics()throws Exception{var f=fixture();var inputs=f.inputs();var expected=f.result(inputs);Collections.reverse(inputs);assertEquals(expected,f.result(inputs));}
    @Test void unselectedHistoryNotConsumed()throws Exception{var f=fixture();var inputs=f.inputs();var expected=f.result(inputs);inputs.add(f.envelope("athena:event-source",SystemStateView.bytes(Map.of("unselectedHistoricalSP3",true)),ref(ScientificReference.Kind.METHOD,"history")));assertEquals(expected,f.result(inputs));}
    @Test void payloadRelabellingRejectedOnReplay()throws Exception{var f=fixture();var inputs=f.inputs();var collector=RuleAnalyzers.collector(f.manifest,f.request());var report=(ObjectNode)JSON.readTree(collector.analyze(f.state,inputs,Map.of()).getFirst().measurements().get("payload"));report.put("geometryKind","OBSERVED_DHA");inputs.add(f.envelope("athena:rule-measurements",SystemStateView.bytes(report),collector.method()));assertThrows(IllegalArgumentException.class,()->RuleAnalyzers.evaluator(f.manifest,f.request()).analyze(f.state,inputs,Map.of()));}
    @Test void changedBytesSameIdentityRejected()throws Exception{
        var f=fixture();var inputs=f.inputs();var e=inputs.getFirst();byte[] changed=SystemStateView.bytes(Map.of("changed",true));
        inputs.add(new EvidenceEnvelope(e.reference(),e.evidenceType(),e.payloadFormat(),e.payloadVersion(),Optional.of(Base64.getEncoder().encodeToString(changed)),Optional.empty(),EvidenceExchange.sha256(changed),e.provenance(),e.method(),e.context(),e.subjects(),e.qualifications(),e.limitations(),e.recordedAt()));
        assertThrows(IllegalArgumentException.class,()->f.result(inputs));
    }
    @Test void pinnedThresholdCannotChange()throws Exception{
        var root=(ObjectNode)JSON.readTree(Files.readAllBytes(MANIFEST));((ObjectNode)root.get("parameters").get("predicate")).put("value","{}");
        assertThrows(java.io.IOException.class,()->RuleRegistry.decode(SystemStateView.bytes(root)));
    }
    @Test void inventoryScopeCannotBeReusedAfterSelectionChange()throws Exception{
        var f=fixture();f.coverage(EvidenceInterpretation.Status.SUPPORTED_PRESENT);f.plan.set("donorAtoms",node(List.of(ref(1,"a0"))));assertThrows(IllegalArgumentException.class,f::result);
    }
    @Test void annotationProtocolMustBeSelected()throws Exception{
        var f=fixture();f.assignment("SP3",false);f.artifacts.remove(2);assertThrows(IllegalArgumentException.class,f::result);
    }
    public static void main(String[] args)throws Exception{Files.write(Path.of(args[0]),SystemStateView.bytes(fixture().result()));}
}
