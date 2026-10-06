package totah.lab.daedalus.system;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.*;
import totah.lab.athena.design.backend.MolecularGraph;
import totah.lab.athena.system.*;
import totah.lab.athena.system.rules.*;
import totah.lab.gaia.structure.AtomReference;
import totah.lab.mnemosyne.*;
import java.nio.file.*;
import java.util.*;
import static totah.lab.daedalus.system.B01FunctionalGroupAcceptanceTest.*;
import static totah.lab.daedalus.system.AthenaScientificRulesAcceptanceTest.*;

/** Fully attributed synthetic coordinates; no experimental observation or production authority. */
final class WaterBridgeFixtures {
    static final Path ROOT=Path.of("software/modules/athena/src/main/resources/totah/lab/athena/system/rules/water-bridge-v1");
    final SystemStateView state;
    RuleManifest manifest;
    final ObjectNode plan;
    final List<EvidenceEnvelope> artifacts=new ArrayList<>();
    final List<Fixture> molecules;
    int sequence;
    int hops=12,nodes=1000,budget=10000;
    WaterBridgeFixtures(int waters,boolean multi)throws Exception {this(chain(waters),multi,Map.of());}
    WaterBridgeFixtures(List<Fixture> fixtures,boolean multi,Map<Integer,String> missing)throws Exception {
        molecules=List.copyOf(fixtures);var initial=system(fixtures.stream().map(Fixture::graph).toList(),true,false);
        var components=new ArrayList<>(initial.components());
        if(missing.getOrDefault(-1,"").equals("ambiguous")){var c=components.get(1);var alternative=new TreeMap<>(c.correspondenceAlternatives().getFirst());var h=alternative.get("h1");alternative.put("h1",alternative.get("h2"));alternative.put("h2",h);components.set(1,new SystemStateView.Component(c.identity(),c.chemistry(),List.of(c.correspondenceAlternatives().getFirst(),alternative),c.limitations()));}
        state=missing.containsKey(-1)?new SystemStateView(initial.identity(),initial.graph(),components,initial.sources(),Set.of(),initial.charges(),!missing.get(-1).equals("frame"),true,List.of("synthetic altered source state")):initial;
        manifest=RuleRegistry.decode(Files.readAllBytes(ROOT.resolve("ATHENA.WATER_BRIDGE.EXPLICIT_H_"+(multi?"MULTI":"SINGLE")+".rule.json")));
        plan=JSON.createObjectNode();plan.put("schema","athena-water-bridge-plan/1");plan.set("stateBinding",node(state.binding()));
        plan.set("first",node(List.of(ref(1,"o"))));plan.set("second",node(List.of(ref(fixtures.size(),"o"))));
        var ws=plan.putArray("waters");for(int i=1;i<fixtures.size()-1;i++)ws.add(node(ref(i+1,"o")));
        plan.putArray("inventoryCoverage");plan.putArray("artifacts");plan.put("minimumWaters",multi?2:1);plan.put("maximumWaters",multi?Math.max(2,fixtures.size()-2):1);
        var sources=JSON.readTree(manifest.parameters().get("sources").value());
        for(int i=0;i<fixtures.size();i++)for(var it=sources.fields();it.hasNext();) {
            var entry=it.next();var source=RuleRegistry.decode(SystemStateView.bytes(entry.getValue()));var c=B01FunctionalGroupAcceptanceTest.coverage(state,fixtures.get(i));c.set("componentReference",node(state.components().get(i).identity()));
            // Mixed explicit/implicit source H is declared per atom, never molecule-wide.
            c.path("atomState").forEach(a->((ObjectNode)a).put("hydrogenMode",a.path("implicitHydrogenCount").asInt()>0?"AUTHORITATIVE_IMPLICIT":"EXPLICIT_GRAPH"));
            String mode=missing.getOrDefault(i,"");
            if(mode.equals("graph"))c.put("completeGraph","UNKNOWN_INCONCLUSIVE");
            if(mode.equals("H")||mode.equals("charge"))for(var a:c.path("atomState"))if(mode.equals("H")){((ObjectNode)a).put("hydrogenMode","UNKNOWN");((ObjectNode)a).putNull("implicitHydrogenCount");}else ((ObjectNode)a).put("chargeStatus","UNKNOWN_INCONCLUSIVE");
            var collector=RuleAnalyzers.collector(source,B01FunctionalGroupAcceptanceTest.request(state,source),BACKEND);
            var finding=collector.analyze(state,List.of(envelope(state,"athena:group-source-coverage",c),envelope(state,"athena:group-definition",JSON.readTree(source.parameters().get("definition").value()))),Map.of()).getFirst();
            add("group-"+i+":"+entry.getKey(),"athena:group-identities",finding.measurements().get("payload").getBytes(java.nio.charset.StandardCharsets.UTF_8),collector.method());
        }
    }
    static List<Fixture> chain(int count){var out=new ArrayList<Fixture>();out.add(endpoint(0,true));for(int i=0;i<count;i++)out.add(water(3.0*(i+1),true));out.add(endpoint(3.0*(count+1),false));return out;}
    static Fixture water(double x,boolean explicit){
        var aa=new ArrayList<MolecularGraph.Atom>();aa.add(atom("o","O",0,false,x,0,0));var bb=new ArrayList<MolecularGraph.Bond>();var h=new TreeMap<String,Integer>();h.put("o",explicit?0:2);
        if(explicit){aa.add(atom("h1","H",0,false,x+1,0,0));aa.add(atom("h2","H",0,false,x,1,0));bb.add(bond("o","h1",MolecularGraph.BondOrder.SINGLE));bb.add(bond("o","h2",MolecularGraph.BondOrder.SINGLE));h.put("h1",0);h.put("h2",0);}
        return new Fixture(new MolecularGraph(aa,bb,Map.of()),h);
    }
    static Fixture endpoint(double x,boolean donor){
        var aa=new ArrayList<MolecularGraph.Atom>();aa.add(atom("o","O",0,false,x,0,0));aa.add(atom("c","C",0,false,x+(donor?-1:1.2),1,0));var bb=new ArrayList<MolecularGraph.Bond>();bb.add(bond("o","c",donor?MolecularGraph.BondOrder.SINGLE:MolecularGraph.BondOrder.DOUBLE));
        var hs=new TreeMap<String,Integer>();hs.put("o",0);hs.put("c",donor?3:2);if(donor){aa.add(atom("h","H",0,false,x+1,0,0));bb.add(bond("o","h",MolecularGraph.BondOrder.SINGLE));hs.put("h",0);}return new Fixture(new MolecularGraph(aa,bb,Map.of()),hs);
    }
    static Fixture position(Fixture f,String id,double x,double y,double z){var aa=f.graph().atoms().stream().map(a->a.id().equals(id)?new MolecularGraph.Atom(a.id(),a.element(),a.isotope(),a.formalCharge(),a.explicitHydrogens(),a.aromatic(),a.stereochemistry(),new MolecularGraph.Coordinates(x,y,z),a.properties()):a).toList();return new Fixture(new MolecularGraph(aa,f.graph().bonds(),f.graph().properties()),f.hydrogens());}
    static Fixture charge(Fixture f,String id,int charge){var aa=f.graph().atoms().stream().map(a->a.id().equals(id)?new MolecularGraph.Atom(a.id(),a.element(),a.isotope(),charge,a.explicitHydrogens(),a.aromatic(),a.stereochemistry(),a.coordinates(),a.properties()):a).toList();return new Fixture(new MolecularGraph(aa,f.graph().bonds(),f.graph().properties()),f.hydrogens());}
    JsonNode add(String id,String type,byte[] bytes,ScientificReference method){var e=SystemQualificationPipeline.envelope(ref(ScientificReference.Kind.ACTIVITY,"water-fixture"),id,type,bytes,method,state.subject(),T,List.of("synthetic engineering evidence; not a production receipt"));artifacts.add(e);return pin(e);}
    static JsonNode pin(EvidenceEnvelope e){return EventCoverageFixture.tree(Map.of("reference",e.reference(),"sha256",e.payloadSha256()));}
    void coverage(EvidenceInterpretation.Status status)throws Exception {
        var raw=add("inventory-protocol-"+sequence,"athena:event-source",SystemStateView.bytes(Map.of("sourceState",state.binding(),"protocol","synthetic complete represented-state inventory, not physical solvent")),ref(ScientificReference.Kind.METHOD,"water-inventory-fixture"));
        String scope=EventCoverageFixture.hash(Map.of("definitionSha256",RuleRegistry.digest(manifest),"stateBinding",state.binding(),"first",sorted("first"),"second",sorted("second"),"waters",sorted("waters"),"minimumWaters",plan.get("minimumWaters").asInt(),"maximumWaters",plan.get("maximumWaters").asInt()));
        var i=new EvidenceInterpretation(ref(ScientificReference.Kind.EVIDENCE_INTERPRETATION,"water-inventory-"+sequence++),List.of(new EvidenceInterpretation.Input(JSON.treeToValue(raw.get("reference"),ScientificReference.class),raw.get("sha256").asText())),ref(ScientificReference.Kind.METHOD,"water-inventory-fixture"),Map.of(),List.of(state.subject()),status,
                Map.of("proposition","COMPLETE_EVENT_SCOPE","stateBinding",EventCoverageFixture.canonical(state.binding()),"eventScope",scope),List.of("exhaustiveness only; no chemical eligibility"),List.of("synthetic"),Optional.empty(),T);
        ((ArrayNode)plan.get("inventoryCoverage")).add(add("inventory-assertion-"+sequence,"athena:event-source",new EvidenceExchange().encodeRecord(i),i.evaluator()));
    }
    List<JsonNode> sorted(String field){var values=new ArrayList<JsonNode>();plan.get(field).forEach(values::add);values.sort(Comparator.comparing(EventCoverageFixture::canonical));return values;}
    RuleRequest request(){return new RuleRequest(state.binding(),manifest.key(),RuleRegistry.digest(manifest),List.of(),List.of(),List.of(),4.5,hops,nodes,budget);}
    List<EvidenceEnvelope> inputs(){((ArrayNode)plan.get("artifacts")).removeAll();artifacts.forEach(e->((ArrayNode)plan.get("artifacts")).add(pin(e)));var out=new ArrayList<>(artifacts);
        out.add(SystemQualificationPipeline.envelope(ref(ScientificReference.Kind.ACTIVITY,"water-fixture"),"plan","athena:water-bridge-plan",SystemStateView.bytes(plan),ref(ScientificReference.Kind.METHOD,"water-selection-fixture"),state.subject(),T,List.of()));return out;}
    JsonNode result()throws Exception {
        var before=SystemStateView.bytes(state.snapshot());var inputs=inputs();var collector=RuleAnalyzers.collector(manifest,request(),BACKEND);var finding=collector.analyze(state,inputs,Map.of()).getFirst();
        var report=JSON.readTree(finding.measurements().get("payload"));var raw=SystemQualificationPipeline.envelope(ref(ScientificReference.Kind.ACTIVITY,"water-fixture"),"result","athena:rule-measurements",SystemStateView.bytes(report),collector.method(),state.subject(),T,List.of());
        var exchange=new EvidenceExchange();var restored=(EvidenceEnvelope)exchange.decodeRecord(exchange.encodeRecord(raw));inputs.add(restored);
        var evaluated=RuleAnalyzers.evaluator(manifest,request()).analyze(state,inputs,Map.of()).getFirst();org.junit.jupiter.api.Assertions.assertEquals(EvidenceInterpretation.Status.NOT_EVALUATED,evaluated.status());org.junit.jupiter.api.Assertions.assertEquals(report,JSON.readTree(evaluated.measurements().get("payload")));org.junit.jupiter.api.Assertions.assertArrayEquals(before,SystemStateView.bytes(state.snapshot()));return report;
    }
}
