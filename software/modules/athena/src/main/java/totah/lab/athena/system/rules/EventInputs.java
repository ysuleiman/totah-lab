package totah.lab.athena.system.rules;

import com.fasterxml.jackson.databind.JsonNode;
import totah.lab.athena.system.*;
import totah.lab.mnemosyne.*;
import java.util.*;
import static totah.lab.athena.system.rules.EventPayload.*;
import static totah.lab.mnemosyne.EvidenceInterpretation.Status.*;

/** Verified explicitly selected evidence only. Source assertions are never rewritten. */
final class EventInputs {
    record Event(JsonNode key, EventCounts.Cell cell, boolean qualified, List<Object> sources) { }
    record State(SystemStateView.Binding binding, SortedMap<String,Event> events, JsonNode payload) { }
    record Selection(JsonNode plan, EvidenceEnvelope planEnvelope, Map<String,EvidenceEnvelope> artifacts,
                     SortedMap<String,State> states, List<Object> inputPins) { }
    private EventInputs() { }
    static Selection resolve(SystemStateView state, RuleRequest request,List<EvidenceEnvelope> supplied) throws Exception {
        var all=index(supplied);var plans=all.values().stream().filter(e->e.evidenceType().equals(PLAN)).toList();
        require(plans.size()==1,"one explicit event analysis plan required");var pe=plans.getFirst();var plan=read(pe);
        fields(plan,"schema","stateBinding","operation","artifacts","states","projections","path");
        require(text(plan,"schema").equals("athena-event-analysis-plan/1"),"plan schema");
        require(binding(plan.get("stateBinding")).equals(state.binding()) && state.binding().equals(request.state()),"plan anchor binding");
        require(Set.of("EVENT_COUNTS","COOCCURRENCE_COUNTS","SIMPLE_PATHS","UNPAIRED").contains(text(plan,"operation")),"operation unsupported");
        var selected=new TreeMap<String,EvidenceEnvelope>();
        for(var pin:array(plan,"artifacts")){var e=pinned(pin,all);require(!e.evidenceType().equals(PLAN),"plan cannot select another plan");selected.put(canonical(e.reference()),e);}
        var states=new TreeMap<String,State>();var identities=new HashMap<ScientificReference,String>();
        for(var selection:array(plan,"states")) {
            fields(selection,"binding","eventSet");var b=binding(selection.get("binding"));var envelope=pinned(selection.get("eventSet"),selected);
            require(envelope.evidenceType().equals(SET),"event set type required");var node=read(envelope);
            fields(node,"schema","stateBinding","profiles","events");
            require(text(node,"schema").equals("athena-typed-event-set/1") && binding(node.get("stateBinding")).equals(b),"set schema/state mismatch");
            String id=canonical(b);String oldId=identities.putIfAbsent(b.state(),id);require(oldId==null||oldId.equals(id),"conflicting source state identity");
            var prior=states.get(id);if(prior!=null){require(prior.payload.equals(node),"contradictory set for duplicate state");continue;}
            var manifests=new TreeMap<String,RuleManifest>();
            for(var profile:array(node,"profiles")){fields(profile,"definitionSha256","implementationId","implementationVersion","manifest");
            var manifest=RuleRegistry.decode(pinned(profile.get("manifest"),selected).readPayload());
            require(RuleRegistry.digest(manifest).equals(digest(profile,"definitionSha256"))
                    && manifest.implementationId().equals(text(profile,"implementationId"))
                    && manifest.implementationVersion().equals(text(profile,"implementationVersion")),"profile manifest mismatch");
                var previous=manifests.putIfAbsent(RuleRegistry.digest(manifest),manifest);require(previous==null||previous.equals(manifest),"conflicting profile identity");
            }
            require(!manifests.isEmpty(),"explicit event profiles required");
            var events=new TreeMap<String,Event>();
            var merged=new TreeMap<String,JsonNode>();
            for(var entry:array(node,"events")) {
                fields(entry,"key","observations");String idKey=eventKey(entry.get("key"));
                var priorEntry=merged.get(idKey);
                if(priorEntry==null)merged.put(idKey,entry.deepCopy());
                else for(var assertion:array(entry,"observations"))((com.fasterxml.jackson.databind.node.ArrayNode)priorEntry.get("observations")).add(assertion.deepCopy());
            }
            for(var e:merged.values()) {
                fields(e,"key","observations");var key=e.get("key");String eventId=eventKey(key);
                var manifest=manifests.get(digest(key,"definitionSha256"));require(manifest!=null,"event definition differs from selected profiles");
                for(var role:array(key,"roles"))require(JSON.treeToValue(role.get("entity"),EvidenceSubject.class).state().equals(b.state()),"event role from another state");
                var assertions=new TreeMap<String,EvidenceInterpretation>();var sources=new ArrayList<Object>();boolean qualified=false;
                var statuses=new TreeSet<String>();boolean present=false,absent=false,negativeAssertion=false;
                var uniqueObservations=new TreeMap<String,JsonNode>();
                for(var o:array(e,"observations"))uniqueObservations.putIfAbsent(canonical(o),o);
                for(var o:uniqueObservations.values()) {
                    fields(o,"source","coverage","qualification");var source=pinned(o.get("source"),selected);
                    var observation=interpretation(source,b,eventId);
                    for(var dependency:observation.inputs())pinned(JSON.valueToTree(dependency),selected);
                    String oid=canonical(observation.reference());
                    var old=assertions.putIfAbsent(oid,observation);require(old==null||old.equals(observation),"conflicting observation identity");
                    require("EVENT".equals(observation.measurements().get("proposition")),"event proposition mismatch");
                    require(observation.evaluator().kind()==ScientificReference.Kind.METHOD
                            && observation.evaluator().id().equals(manifest.key()+"/evaluate")
                            && observation.evaluator().namespace().equals(manifest.implementationId())
                            && observation.evaluator().version().equals(RuleRegistry.digest(manifest)),"observation method mismatch");
                    var st=observation.status();statuses.add(st.name());sources.add(Map.of("source",pin(source),"status",st.name(),"coverage",o.get("coverage"),"qualification",o.get("qualification")));
                    boolean covered=coverage(array(o,"coverage"),selected,b,eventId);
                    if(st==SUPPORTED_PRESENT){present=true;qualified|=EventSourceQualification.verify(o.get("qualification"),manifest,observation,b,selected);}
                    if(st==ABSENT_FALSE){negativeAssertion=true;if(covered){absent=true;qualified|=EventSourceQualification.verify(o.get("qualification"),manifest,observation,b,selected);}}
                }
                String status=present&&negativeAssertion?"CONFLICTING":present?SUPPORTED_PRESENT.name():absent?ABSENT_FALSE.name():UNKNOWN_INCONCLUSIVE.name();
                sources.sort(Comparator.comparing(EventPayload::canonical));
                var value=new Event(key,new EventCounts.Cell(status,List.copyOf(statuses),sources),qualified,List.copyOf(sources));
                var old=events.putIfAbsent(eventId,value);require(old==null||old.equals(value),"duplicate event key with different assertions; combine observations explicitly");
            }
            states.put(id,new State(b,Collections.unmodifiableSortedMap(events),node));
        }
        require(!states.isEmpty(),"explicit ensemble cannot be empty");
        var pins=new ArrayList<Object>();selected.values().forEach(e->pins.add(pin(e)));
        return new Selection(plan,pe,Collections.unmodifiableMap(selected),Collections.unmodifiableSortedMap(states),List.copyOf(pins));
    }
    static EvidenceInterpretation interpretation(EvidenceEnvelope e,SystemStateView.Binding state,String scope) throws Exception {
        var record=new EvidenceExchange().decodeRecord(e.readPayload());require(record instanceof EvidenceInterpretation,"source must be an immutable interpretation record");
        var i=(EvidenceInterpretation)record;
        require(i.subjects().stream().allMatch(s->s.state().equals(state.state()))
                && canonical(state).equals(i.measurements().get("stateBinding"))
                && scope.equals(i.measurements().get("eventScope")),"source state/scope mismatch");
        return i;
    }
    static boolean coverage(List<JsonNode> pins,Map<String,EvidenceEnvelope> inputs,SystemStateView.Binding state,String scope)throws Exception {
        boolean positive=false,conflict=false;
        for(var pin:pins){var c=interpretation(pinned(pin,inputs),state,scope);
            for(var dependency:c.inputs())pinned(JSON.valueToTree(dependency),inputs);
            require("COMPLETE_EVENT_SCOPE".equals(c.measurements().get("proposition")),"coverage proposition mismatch");
            positive|=c.status()==SUPPORTED_PRESENT;conflict|=c.status()!=SUPPORTED_PRESENT;
        }
        return positive&&!conflict;
    }
}
