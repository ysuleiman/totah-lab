package totah.lab.daedalus.system;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import totah.lab.athena.system.*;
import totah.lab.athena.system.rules.*;
import totah.lab.mnemosyne.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static totah.lab.daedalus.system.AthenaScientificRulesAcceptanceTest.*;

/** Synthetic attributed evidence; never a real scientific receipt or source observation. */
final class EventCoverageFixture {
    static final ObjectMapper JSON=new ObjectMapper();
    final SystemStateView state=SourceSulfurConnectivityAcceptanceTest.state(false);
    final RuleManifest manifest;
    RuleManifest sourceManifest;
    java.time.Instant sourceTime=T;
    final List<EvidenceEnvelope> artifacts=new ArrayList<>();
    final ObjectNode plan;final ArrayNode sets;
    int sequence;
    EventCoverageFixture()throws Exception {
        manifest=qualified(RuleRegistry.decode(Files.readAllBytes(Path.of("software/modules/athena/src/main/resources/totah/lab/athena/system/rules/events-v1/ATHENA.EVENT.EXPLICIT_COVERAGE_ANALYSIS.rule.json"))));
        sourceManifest=qualified(SourceSulfurConnectivityAcceptanceTest.model());
        plan=JSON.createObjectNode();plan.put("schema","athena-event-analysis-plan/1");plan.set("stateBinding",tree(state.binding()));plan.put("operation","EVENT_COUNTS");
        plan.putArray("artifacts");sets=plan.putArray("states");plan.putArray("projections");plan.putNull("path");
    }
    static RuleManifest qualified(RuleManifest m)throws Exception {var n=(ObjectNode)JSON.readTree(SystemStateView.bytes(m));n.put("qualification","QUALIFIED");return RuleRegistry.decode(SystemStateView.bytes(n));}
    static JsonNode tree(Object o){try{return JSON.readTree(SystemStateView.bytes(o));}catch(Exception e){throw new IllegalArgumentException(e);}}
    static String canonical(Object o){return new String(SystemStateView.bytes(o instanceof JsonNode?JSON.convertValue(o,Object.class):o),StandardCharsets.UTF_8);}
    static String hash(Object o){return EvidenceExchange.sha256(canonical(o).getBytes(StandardCharsets.UTF_8));}
    JsonNode add(String id,String type,byte[] bytes){var e=SystemQualificationPipeline.envelope(ref(ScientificReference.Kind.ACTIVITY,"events"),id,type,bytes,ref(ScientificReference.Kind.METHOD,"fixture"),state.subject(),T,List.of("synthetic engineering only"));artifacts.add(e);return tree(Map.of("reference",e.reference(),"sha256",e.payloadSha256()));}
    JsonNode subject(SystemStateView.Binding b,String id){return tree(new EvidenceSubject(b.state(),"ATOM",id,List.of()));}
    ObjectNode key(SystemStateView.Binding b,String a,String z,String alternative){var k=JSON.createObjectNode();k.put("definitionSha256",RuleRegistry.digest(sourceManifest));k.put("direction","DIRECTED");k.put("correspondenceAlternative",alternative);var roles=k.putArray("roles");roles.addObject().put("role","from").set("entity",subject(b,a));roles.addObject().put("role","to").set("entity",subject(b,z));return k;}
    JsonNode source(SystemStateView.Binding b,String scope,EvidenceInterpretation.Status status,boolean coverage)throws Exception {
        var input=add("raw-"+sequence,"athena:event-source",("source-"+sequence).getBytes(StandardCharsets.UTF_8));
        var i=new EvidenceInterpretation(ref(ScientificReference.Kind.EVIDENCE_INTERPRETATION,"i-"+sequence++),List.of(new EvidenceInterpretation.Input(JSON.treeToValue(input.get("reference"),ScientificReference.class),input.get("sha256").asText())),
                new ScientificReference(ScientificReference.Kind.METHOD,sourceManifest.implementationId(),sourceManifest.key()+"/evaluate",RuleRegistry.digest(sourceManifest)),Map.of(),List.of(new EvidenceSubject(b.state(),"SYSTEM","fixture",List.of())),status,
                Map.of("stateBinding",canonical(b),"eventScope",scope,"proposition",coverage?"COMPLETE_EVENT_SCOPE":"EVENT"),List.of("synthetic"),List.of("no scientific assertion"),Optional.empty(),sourceTime);
        return add("interpretation-"+sequence,"athena:event-source",new EvidenceExchange().encodeRecord(i));
    }
    ObjectNode event(SystemStateView.Binding b,ObjectNode key,EvidenceInterpretation.Status status,boolean covered)throws Exception {
        var e=JSON.createObjectNode();e.set("key",key);var o=e.putArray("observations").addObject();o.set("source",source(b,hash(key),status,false));var c=o.putArray("coverage");if(covered)c.add(source(b,hash(key),EvidenceInterpretation.Status.SUPPORTED_PRESENT,true));
        o.set("qualification",tree(Map.of("receipt",JSON.nullNode(),"context",JSON.nullNode())));return e;
    }
    void state(SystemStateView.Binding b,List<ObjectNode> events)throws Exception {
        var n=JSON.createObjectNode();n.put("schema","athena-typed-event-set/1");n.set("stateBinding",tree(b));n.putArray("profiles").add(tree(Map.of("definitionSha256",RuleRegistry.digest(sourceManifest),"implementationId",sourceManifest.implementationId(),"implementationVersion",sourceManifest.implementationVersion(),"manifest",add("manifest","athena:event-source",SystemStateView.bytes(sourceManifest)))));
        n.set("events",tree(events));var pin=add("set-"+sets.size(),"athena:typed-event-set",SystemStateView.bytes(n));sets.addObject().setAll((ObjectNode)tree(Map.of("binding",b,"eventSet",pin)));
    }
    void projection(String id,SystemStateView.Binding b,List<ObjectNode> keys,boolean complete)throws Exception {
        var ps=(ArrayNode)plan.get("projections");ObjectNode p=null;for(var n:ps)if(n.get("id").asText().equals(id))p=(ObjectNode)n;
        if(p==null){p=ps.addObject().put("id",id);p.putArray("members");}
        var m=((ArrayNode)p.get("members")).addObject();m.set("state",tree(b));m.set("events",tree(keys));m.putNull("feature");var c=m.putArray("coverage");
        if(complete)c.add(source(b,hash(Map.of("projection",id,"events",new TreeSet<>(keys.stream().map(EventCoverageFixture::hash).toList()))),EvidenceInterpretation.Status.SUPPORTED_PRESENT,true));
    }
    RuleRequest request(){return new RuleRequest(state.binding(),manifest.key(),RuleRegistry.digest(manifest),List.of(),List.of(),List.of(),4.5,8,100,100);}
    List<EvidenceEnvelope> inputs(){((ArrayNode)plan.get("artifacts")).removeAll();for(var e:artifacts)((ArrayNode)plan.get("artifacts")).add(tree(Map.of("reference",e.reference(),"sha256",e.payloadSha256())));
        var out=new ArrayList<>(artifacts);out.add(SystemQualificationPipeline.envelope(ref(ScientificReference.Kind.ACTIVITY,"events"),"plan","athena:event-analysis-plan",SystemStateView.bytes(plan),ref(ScientificReference.Kind.METHOD,"fixture"),state.subject(),T,List.of()));return out;}
    SystemGraphAnalyzer.Finding finding()throws Exception{return RuleAnalyzers.evaluator(manifest,request()).analyze(state,inputs(),Map.of()).getFirst();}
    JsonNode result()throws Exception{return JSON.readTree(finding().measurements().get("payload"));}
}
