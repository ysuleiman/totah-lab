package totah.lab.athena.system.rules;

import com.fasterxml.jackson.databind.JsonNode;
import totah.lab.athena.system.*;
import totah.lab.mnemosyne.*;
import java.util.*;
import static totah.lab.athena.system.rules.EventPayload.*;
import static totah.lab.mnemosyne.EvidenceInterpretation.Status.*;

/** Read-only descriptive event analysis, exposed through existing generic rule interfaces. */
final class EventAnalysisRules {
    private EventAnalysisRules() { }
    static void validate(RuleManifest m) {
        require(Set.of("athena-rule/2","athena-rule/3").contains(m.schema())
                && m.ruleId().equals("ATHENA.EVENT.EXPLICIT_COVERAGE_ANALYSIS")
                && m.profile().equals("ATHENA_EVENT_ANALYSIS_V1") && m.implementationVersion().equals("1")
                && m.family()==RuleManifest.Family.ENVIRONMENT && m.parameters().isEmpty()
                && m.requiredCapabilities().isEmpty()
                && m.negativeCoverage().requirements().equals(List.of("EXPLICIT_EVENT_UNIVERSE","COMPLETE_APPLICABLE_COVERAGE","RESOLVED_SOURCE_ASSERTIONS")),"event rule contract");
    }
    static SystemGraphAnalyzer analyzer(RuleManifest m,RuleRequest request) {
        validate(m);
        return new SystemGraphAnalyzer() {
            public ScientificReference method(){return new ScientificReference(ScientificReference.Kind.METHOD,"athena.events",m.key()+"/evaluate",RuleRegistry.digest(m));}
            public Set<SystemGraphCertificate.Capability> requires(){return Set.of();}
            public Set<SystemGraphCertificate.Capability> qualifies(){return Set.of();}
            public Set<String> evidenceTypes(){return Set.of(PLAN,SET,"athena:event-source");}
            public List<Finding> analyze(SystemStateView state,List<EvidenceEnvelope> evidence,Map<String,String> configuration)throws Exception {
                require(request.manifestKey().equals(m.key()) && request.manifestSha256().equals(RuleRegistry.digest(m))
                        && request.atoms().isEmpty() && request.first().isEmpty() && request.second().isEmpty(),"event request manifest/scope mismatch");
                var selection=EventInputs.resolve(state,request,evidence);var plan=selection.plan();String op=text(plan,"operation");
                var out=new TreeMap<String,Object>();out.put("schema","athena-event-analysis/1");out.put("operation",op);
                out.put("plan",pin(selection.planEnvelope()));out.put("implementation","athena.events/1");out.put("manifestSha256",RuleRegistry.digest(m));
                out.put("inputs",selection.inputPins());out.put("states",selection.states().values().stream().map(EventInputs.State::binding).toList());
                var distinctCounts=new TreeMap<String,Object>();
                selection.states().forEach((id,s)->{
                    var cells=new TreeMap<String,EventCounts.Cell>();s.events().forEach((k,e)->cells.put(k,e.cell()));
                    distinctCounts.put(id,EventCounts.summary(cells));
                });
                out.put("distinctEventCountsByState",distinctCounts);
                EvidenceInterpretation.Status assessment=SUPPORTED_PRESENT;
                if(op.equals("SIMPLE_PATHS")) {
                    require(array(plan,"projections").isEmpty(),"paths do not use count projections");
                    var paths=EventPaths.analyze(selection,request);out.put("result",paths);
                    assessment=EvidenceInterpretation.Status.valueOf((String)paths.get("assessment"));
                } else {
                    require(plan.get("path").isNull(),"non-path plan must not carry a path");
                    var projections=project(selection,op.equals("UNPAIRED"));
                    require(!projections.isEmpty(),"explicit projections required");
                    var result=new TreeMap<String,Object>();projections.forEach((k,v)->result.put(k,EventCounts.summary(v)));
                    out.put("projections",result);out.put("correspondence",plan.get("projections"));
                    if(op.equals("COOCCURRENCE_COUNTS"))out.put("cooccurrence",EventCounts.cooccurrence(projections,new TreeSet<>(selection.states().keySet())));
                    if(op.equals("UNPAIRED")) {
                        require(projections.size()==1 && selection.states().size()==1,"unpaired is one explicit feature/state");
                        var cell=projections.get(projections.firstKey()).values().iterator().next();
                        assessment=cell.status().equals(ABSENT_FALSE.name())?SUPPORTED_PRESENT:cell.present()?ABSENT_FALSE:UNKNOWN_INCONCLUSIVE;
                        out.put("proposition","UNPAIRED_UNDER_EXPLICIT_PROFILE_AND_PARTNER_SCOPE");
                    }
                }
                if(m.retired()||(!m.schema().equals("athena-rule/3")&&m.qualification()!=SystemGraphCertificate.Status.QUALIFIED))assessment=NOT_EVALUATED;
                out.put("assessment",assessment.name());
                return List.of(new Finding("event-analysis",List.of(state.subject()),assessment,Map.of("payload",canonical(out)),
                        List.of("explicit event evidence only; counts and paths are descriptive"),m.limitations()));
            }
        };
    }
    private static SortedMap<String,SortedMap<String,EventCounts.Cell>> project(EventInputs.Selection selected,boolean unpaired)throws Exception {
        var out=new TreeMap<String,SortedMap<String,EventCounts.Cell>>();
        for(var projection:array(selected.plan(),"projections")) {
            fields(projection,"id","members");String id=text(projection,"id");require(!out.containsKey(id),"duplicate projection identity");
            var cells=new TreeMap<String,EventCounts.Cell>();selected.states().keySet().forEach(k->cells.put(k,EventCounts.missing()));
            var mappings=new TreeMap<String,String>();
            for(var member:array(projection,"members")) {
                fields(member,"state","events","coverage","feature");var binding=binding(member.get("state"));String stateId=canonical(binding);
                var state=selected.states().get(stateId);require(state!=null,"projection state not selected");
                String old=mappings.putIfAbsent(stateId,canonical(member));require(old==null||old.equals(canonical(member)),"conflicting projection correspondence");
                if(unpaired){var feature=JSON.treeToValue(member.get("feature"),EvidenceSubject.class);require(feature.state().equals(binding.state()),"feature state mismatch");}
                else require(member.get("feature").isNull(),"feature applies to unpaired only");
                var keys=new TreeSet<String>();var values=new ArrayList<EventCounts.Cell>();
                for(var key:array(member,"events")) {String k=eventKey(key);if(!keys.add(k))continue;
                    var event=state.events().get(k);values.add(event==null?EventCounts.missing():event.cell());
                    if(unpaired)require(array(key,"roles").stream().anyMatch(r->r.get("entity").equals(member.get("feature"))),"unpaired event does not involve feature");
                }
                String scope;
                if(unpaired) {
                    var definitions=new TreeSet<String>();for(var key:array(member,"events"))definitions.add(digest(key,"definitionSha256"));
                    if(definitions.isEmpty())for(var profile:array(state.payload(),"profiles"))definitions.add(digest(profile,"definitionSha256"));
                    require(definitions.size()==1,"unpaired requires one explicitly attributable profile");
                    scope=hash(Map.of("projection",id,"events",keys,"feature",member.get("feature"),"profileDefinitionSha256",definitions.first()));
                } else scope=hash(Map.of("projection",id,"events",keys));
                boolean complete=EventInputs.coverage(array(member,"coverage"),selected.artifacts(),binding,scope);
                // Even an empty universe needs independent exhaustive coverage for a negative.
                cells.put(stateId,EventCounts.project(values,complete));
            }
            out.put(id,Collections.unmodifiableSortedMap(cells));
        }
        return Collections.unmodifiableSortedMap(out);
    }
}
