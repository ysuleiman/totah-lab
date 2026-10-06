package totah.lab.athena.system.rules;

import com.fasterxml.jackson.databind.JsonNode;
import totah.lab.athena.system.*;
import totah.lab.gaia.structure.AtomReference;
import totah.lab.mnemosyne.*;
import java.util.*;
import static totah.lab.athena.system.rules.EventPayload.*;
import static totah.lab.mnemosyne.EvidenceInterpretation.Status.*;

/** Only the approved 21 directional class families, composed from existing source roles/geometry. */
final class WaterBridgeLegs {
    record Role(String name,HbondCandidateSources.Anchor anchor,List<AtomReference> hydrogens) { }
    record Result(List<Map<String,Object>> legs,SortedMap<String,EventInputs.Event> events,boolean complete,List<String> reasons) { }
    private WaterBridgeLegs() { }
    static Result collect(SystemStateView state,RuleManifest manifest,RuleRequest request,WaterBridgeInputs input,
                          Map<AtomReference,WaterIdentity.Result> water)throws Exception {
        var donors=new ArrayList<Role>();var acceptors=new ArrayList<Role>();var reasons=new TreeSet<String>();
        boolean complete=true;var roles=HbondCandidateRules.parameter(manifest,"roles");
        for(var a:java.util.stream.Stream.concat(input.first.stream(),input.second.stream()).toList()) {
            if(water.containsKey(a)&&water.get(a).possible()){complete=false;reasons.add("endpoint may be water: "+a);continue;}
            var cs=state.components().stream().filter(c->c.correspondenceAlternatives().size()==1&&c.correspondenceAlternatives().getFirst().containsValue(a)).toList();
            if(cs.size()!=1){complete=false;reasons.add("unresolved endpoint correspondence");continue;}
            var c=cs.getFirst();int before=donors.size()+acceptors.size();
            for(String side:List.of("donors","acceptors")) {
                var names=new TreeSet<String>();roles.get(side).fieldNames().forEachRemaining(names::add);
                for(var name:names){var descriptors=roles.get(side).get(name);var contexts=side.equals("acceptors")?roles.path("contexts").get(name):null;
                    complete&=input.chemistry.complete(c,descriptors)&&(contexts==null||input.chemistry.complete(c,contexts));
                    for(var anchor:input.chemistry.anchors(c,descriptors))if(anchor.atom().equals(a)) {
                        if(contexts!=null&&input.chemistry.anchors(c,contexts).stream().noneMatch(x->x.atom().equals(a)))continue;
                        var hs=side.equals("donors")?HbondCandidateEnumeration.hydrogens(state,anchor):List.<AtomReference>of();
                        if(side.equals("donors")&&hs.isEmpty()){complete=false;reasons.add("endpoint explicit donor H unavailable");continue;}
                        (side.equals("donors")?donors:acceptors).add(new Role(name,anchor,hs));
                    }
                }
            }
            if(before==donors.size()+acceptors.size()){complete=false;reasons.add("endpoint has no eligible reviewed role: "+a);}
        }
        for(var o:input.waters){var w=water.get(o);if(w!=null&&!w.possible())continue;
            if(w==null||!w.oriented()){complete=false;continue;}
            donors.add(new Role("WATER.NEUTRAL.EXPLICIT_H2",w.anchor(),w.hydrogen()));
            acceptors.add(new Role("WATER.NEUTRAL.EXPLICIT_H2",w.anchor(),List.of()));}
        var legs=new ArrayList<Map<String,Object>>();var events=new TreeMap<String,EventInputs.Event>();int used=0;
        outer:for(var d:donors)for(var a:acceptors){
            boolean dw=d.name().equals("WATER.NEUTRAL.EXPLICIT_H2"),aw=a.name().equals("WATER.NEUTRAL.EXPLICIT_H2");
            if(!dw&&!aw||d.anchor().atom().equals(a.anchor().atom()))continue;
            for(var h:d.hydrogens()) {
                if(used++>=request.maximumCandidates()){complete=false;reasons.add("leg candidate budget truncated");break outer;}
                var geo=HbondCandidateGeometry.measure(state,manifest,request,d.anchor(),h,a.anchor());
                Double da=HbondCandidateGeometry.value(geo,0,"distanceAngstrom"),ha=HbondCandidateGeometry.value(geo,1,"distanceAngstrom"),dha=HbondCandidateGeometry.value(geo,2,"angleDegrees");
                boolean eligible=da!=null&&ha!=null&&dha!=null&&da>0&&ha>0&&WaterIdentity.finite(state,h)
                        &&state.atoms().get(h).getPosition().distance(state.atoms().get(d.anchor().atom()).getPosition())>0;
                if(!eligible){complete=false;reasons.add("degenerate or unavailable leg geometry");}
                var status=!eligible?UNKNOWN_INCONCLUSIVE:da<=Double.parseDouble(manifest.parameters().get("distance").value())&&dha>=Double.parseDouble(manifest.parameters().get("angle").value())?SUPPORTED_PRESENT:ABSENT_FALSE;
                var key=JSON.createObjectNode();key.put("definitionSha256",RuleRegistry.digest(manifest));key.put("direction","UNDIRECTED");
                key.put("correspondenceAlternative",hash(Map.of("donorClass",d.name(),"acceptorClass",a.name(),"donorOccurrence",d.anchor().occurrence(),"acceptorOccurrence",a.anchor().occurrence())));
                var rr=key.putArray("roles");role(rr,"from",state,d.anchor().atom());role(rr,"to",state,a.anchor().atom());
                role(rr,"donor",state,d.anchor().atom());role(rr,"hydrogen",state,h);role(rr,"acceptor",state,a.anchor().atom());
                String id=eventKey(key);var pins=List.of(pin(d.anchor().report().envelope()),pin(a.anchor().report().envelope()));
                var leg=new TreeMap<String,Object>();leg.put("donorClass",d.name());leg.put("acceptorClass",a.name());leg.put("donor",d.anchor().atom());leg.put("hydrogen",h);leg.put("acceptor",a.anchor().atom());
                leg.put("sourceReportPins",pins);leg.put("geometry",geo);leg.put("eventKey",key);leg.put("assessment",status.name());leg.put("reasons",List.of("Recomputed explicit-source-H leg under enclosing water-rule qualification; not independent corroboration"));
                legs.add(leg);events.put(id,new EventInputs.Event(key,new EventCounts.Cell(status.name(),List.of(status.name()),List.of(leg)),eligible,List.of(leg)));
            }
        }
        legs.sort(Comparator.comparing(EventPayload::canonical));return new Result(List.copyOf(legs),events,complete,List.copyOf(reasons));
    }
    static EvidenceSubject subject(SystemStateView state,AtomReference a){return new EvidenceSubject(state.identity(),"atom",a.toString(),List.of());}
    private static void role(com.fasterxml.jackson.databind.node.ArrayNode array,String role,SystemStateView state,AtomReference a){array.addObject().put("role",role).set("entity",JSON.valueToTree(subject(state,a)));}
}
