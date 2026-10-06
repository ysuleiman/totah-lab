package totah.lab.athena.system.rules;

import com.fasterxml.jackson.databind.JsonNode;
import totah.lab.athena.system.*;
import totah.lab.gaia.structure.AtomReference;
import totah.lab.mnemosyne.*;
import java.util.*;
import static totah.lab.athena.system.rules.EventPayload.*;
import static totah.lab.mnemosyne.EvidenceInterpretation.Status.*;

/** I12/I13 only. Source chemistry, geometry and the qualified EventPaths engine are reused. */
final class WaterBridgeRules {
    private WaterBridgeRules() { }
    static void validate(RuleManifest m) {
        require(Set.of("athena-rule/2","athena-rule/3").contains(m.schema())&&Set.of("ATHENA.WATER_BRIDGE.EXPLICIT_H_SINGLE","ATHENA.WATER_BRIDGE.EXPLICIT_H_MULTI").contains(m.ruleId())
                &&m.version().equals("1.0.0")&&m.implementationVersion().equals("1")&&m.profile().equals("ATHENA_WATER_BRIDGE_V1")&&m.family()==RuleManifest.Family.ENVIRONMENT&&m.requiredCapabilities().isEmpty(),"water rule contract");
        try(var in=WaterBridgeRules.class.getResourceAsStream("water-bridge-v1/"+m.ruleId()+".rule.json")) {
            require(in!=null,"missing pinned water definition");var pinned=JSON.readTree(in);
            require(JSON.valueToTree(m.parameters()).equals(pinned.get("parameters"))&&JSON.valueToTree(m.negativeCoverage()).equals(pinned.get("negativeCoverage"))&&JSON.valueToTree(m.scientificSources()).equals(pinned.get("scientificSources")),"water scientific definition changed without review");
        }catch(java.io.IOException e){throw new IllegalArgumentException("water definition unreadable",e);}
    }
    static ScientificReference method(RuleManifest m,boolean evaluate){return new ScientificReference(ScientificReference.Kind.METHOD,"athena.water-bridge",m.key()+(evaluate?"/evaluate":"/collect"),RuleRegistry.digest(m));}
    static SystemGraphAnalyzer analyzer(RuleManifest manifest,RuleRequest request,boolean evaluate){
        validate(manifest);
        return new SystemGraphAnalyzer(){
            public ScientificReference method(){return WaterBridgeRules.method(manifest,evaluate);}
            public Set<SystemGraphCertificate.Capability> requires(){return Set.of();}
            public Set<SystemGraphCertificate.Capability> qualifies(){return Set.of();}
            public Set<String> evidenceTypes(){return Set.of("athena:water-bridge-plan","athena:group-identities","athena:event-source","athena:rule-measurements","athena:system-state");}
            public List<Finding> analyze(SystemStateView state,List<EvidenceEnvelope> inputs,Map<String,String> config)throws Exception {
                require(state.binding().equals(request.state())&&manifest.key().equals(request.manifestKey())&&RuleRegistry.digest(manifest).equals(request.manifestSha256())
                        &&request.atoms().isEmpty()&&request.first().isEmpty()&&request.second().isEmpty(),"water request binding/scope mismatch");
                var selected=new WaterBridgeInputs(state,manifest,request,inputs);var report=build(state,manifest,request,selected);
                if(evaluate){var raw=inputs.stream().filter(e->e.evidenceType().equals("athena:rule-measurements")).toList();
                    require(raw.size()==1&&raw.getFirst().method().equals(WaterBridgeRules.method(manifest,false))&&read(raw.getFirst()).equals(report),"water measurements/source replay mismatch");}
                var status=EvidenceInterpretation.Status.valueOf(report.get("assessment").asText());
                if(!evaluate)status=SUPPORTED_PRESENT;
                else if(manifest.retired()||(!manifest.schema().equals("athena-rule/3")&&manifest.qualification()!=SystemGraphCertificate.Status.QUALIFIED))status=NOT_EVALUATED;
                return List.of(new Finding(evaluate?"evaluate":"collect",List.of(state.subject()),status,Map.of("payload",canonical(report)),List.of("Explicit-source-H water-mediated structural candidates only"),manifest.limitations()));
            }
        };
    }
    private static JsonNode build(SystemStateView s,RuleManifest m,RuleRequest request,WaterBridgeInputs input)throws Exception {
        var water=new TreeMap<AtomReference,WaterIdentity.Result>();var reasons=new TreeSet<String>();
        boolean complete=input.inventoryComplete&&input.conflicts.isEmpty();
        if(!input.inventoryComplete)reasons.add("independent inventory exhaustiveness not established");
        if(!input.conflicts.isEmpty())reasons.add("conflicting source chemistry reports: "+input.conflicts);
        for(var e:s.atoms().entrySet())if(e.getValue().getElement()==totah.lab.gaia.chemistry.Element.O){
            var w=WaterIdentity.assess(s,input,e.getKey());water.put(e.getKey(),w);
            if(w.possible()&&(!input.waters.contains(e.getKey())||!w.oriented())){complete=false;reasons.add("possibly-water oxygen omitted or unresolved: "+e.getKey());}
        }
        var legs=WaterBridgeLegs.collect(s,m,request,input,water);complete&=legs.complete();reasons.addAll(legs.reasons());
        var paths=new ArrayList<Map<String,Object>>();var analyses=new ArrayList<Map<String,Object>>();int pairs=0;
        for(var start:input.first)for(var end:input.second){
            if(pairs++>=request.maximumCandidates()){complete=false;reasons.add("endpoint-pair budget truncated");break;}
            var allowed=new HashSet<String>();allowed.add(canonical(WaterBridgeLegs.subject(s,start)));allowed.add(canonical(WaterBridgeLegs.subject(s,end)));
            input.waters.stream().filter(w->water.get(w).oriented()).forEach(w->allowed.add(canonical(WaterBridgeLegs.subject(s,w))));
            var events=new TreeMap<String,EventInputs.Event>();
            for(var e:legs.events().entrySet())if(array(e.getValue().key(),"roles").stream().filter(r->Set.of("from","to").contains(text(r,"role"))).allMatch(r->allowed.contains(canonical(r.get("entity")))))events.put(e.getKey(),e.getValue());
            var result=traverse(s,m,request,input,events,start,end,complete);analyses.add(result);
            if(!(Boolean)result.get("complete")){complete=false;reasons.add("bounded event path coverage incomplete");}
            for(var path:JSON.valueToTree(result.get("paths"))){
                var ws=new ArrayList<AtomReference>();String current=canonical(WaterBridgeLegs.subject(s,start));var keys=new ArrayList<String>();
                for(var edge:path){String id=edge.asText();keys.add(id);var key=events.get(id).key();String from=null,to=null;
                    for(var r:array(key,"roles")){if(text(r,"role").equals("from"))from=canonical(r.get("entity"));if(text(r,"role").equals("to"))to=canonical(r.get("entity"));}
                    current=current.equals(from)?to:from;
                    for(var w:input.waters)if(current.equals(canonical(WaterBridgeLegs.subject(s,w))))ws.add(w);
                }
                if(ws.size()>=input.plan.get("minimumWaters").asInt()&&ws.size()<=input.plan.get("maximumWaters").asInt())paths.add(Map.of("eventKeys",keys,"waters",ws,"minimumWaters",input.plan.get("minimumWaters").asInt(),"maximumWaters",input.plan.get("maximumWaters").asInt(),"assessment",SUPPORTED_PRESENT.name()));
            }
        }
        paths.sort(Comparator.comparing(EventPayload::canonical));
        var out=new TreeMap<String,Object>();out.put("schema","athena-water-bridge-measurements/1");out.put("definitionSha256",RuleRegistry.digest(m));out.put("stateBinding",s.binding());out.put("request",request);out.put("plan",pin(input.envelope));
        out.put("inputPins",input.artifacts.values().stream().map(EventPayload::pin).toList());out.put("waterIdentities",input.waters.stream().map(w->water.get(w).payload()).toList());out.put("legs",legs.legs());out.put("eventAnalysis",analyses);out.put("paths",paths);out.put("complete",complete);
        out.put("assessment",!paths.isEmpty()?SUPPORTED_PRESENT.name():complete?ABSENT_FALSE.name():UNKNOWN_INCONCLUSIVE.name());out.put("reasons",reasons);out.put("limitations",m.limitations());return JSON.valueToTree(out);
    }
    private static Map<String,Object> traverse(SystemStateView s,RuleManifest m,RuleRequest request,WaterBridgeInputs input,
                                              SortedMap<String,EventInputs.Event> events,AtomReference start,AtomReference end,boolean complete)throws Exception {
        var plan=JSON.createObjectNode();var p=plan.putObject("path");p.set("start",JSON.valueToTree(WaterBridgeLegs.subject(s,start)));p.set("end",JSON.valueToTree(WaterBridgeLegs.subject(s,end)));
        p.put("fromRole","from");p.put("toRole","to");p.put("maximumLength",input.plan.get("maximumWaters").asInt()+1);
        String scope=hash(Map.of("events",events.keySet(),"fromRole","from","toRole","to"));
        var dependencies=new ArrayList<EvidenceInterpretation.Input>();dependencies.add(new EvidenceInterpretation.Input(input.envelope.reference(),input.envelope.payloadSha256()));
        input.artifacts.values().forEach(e->dependencies.add(new EvidenceInterpretation.Input(e.reference(),e.payloadSha256())));
        var assertion=new EvidenceInterpretation(new ScientificReference(ScientificReference.Kind.EVIDENCE_INTERPRETATION,"athena.water-bridge.coverage",hash(Map.of("scope",scope,"plan",pin(input.envelope),"complete",complete)),"1"),dependencies,
                method(m,false),Map.of(),List.of(s.subject()),complete?SUPPORTED_PRESENT:UNKNOWN_INCONCLUSIVE,
                Map.of("proposition","COMPLETE_EVENT_SCOPE","stateBinding",canonical(s.binding()),"eventScope",scope),List.of("Derived conjunction of independently verified inventory, chemistry, H and leg coverage"),m.limitations(),Optional.empty(),input.envelope.recordedAt());
        byte[] bytes=new EvidenceExchange().encodeRecord(assertion);String digest=EvidenceExchange.sha256(bytes);
        var e=new EvidenceEnvelope(new ScientificReference(ScientificReference.Kind.EVIDENCE_ENVELOPE,"athena.water-bridge.coverage",digest,"1"),"athena:event-source","application/json","1",Optional.of(Base64.getEncoder().encodeToString(bytes)),Optional.empty(),digest,input.envelope.provenance(),method(m,false),input.envelope.context(),List.of(s.subject()),List.of(),List.of("Derived coverage, not an independent source or scientific receipt"),input.envelope.recordedAt());
        p.putArray("coverage").add(JSON.valueToTree(pin(e)));var artifacts=new TreeMap<>(input.artifacts);artifacts.put(canonical(e.reference()),e);artifacts.put(canonical(input.envelope.reference()),input.envelope);
        var states=new TreeMap<String,EventInputs.State>();states.put(canonical(s.binding()),new EventInputs.State(s.binding(),events,JSON.nullNode()));
        return EventPaths.analyze(new EventInputs.Selection(plan,input.envelope,artifacts,states,List.of()),request);
    }
}
