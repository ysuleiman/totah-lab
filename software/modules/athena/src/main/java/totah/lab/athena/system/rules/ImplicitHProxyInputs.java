package totah.lab.athena.system.rules;

import com.fasterxml.jackson.databind.JsonNode;
import totah.lab.athena.system.*;
import totah.lab.gaia.structure.AtomReference;
import totah.lab.mnemosyne.*;
import java.util.*;
import static totah.lab.athena.system.rules.EventPayload.*;

/** I03-A selection and attribution only. No assignment producer is qualified here. */
final class ImplicitHProxyInputs {
    final JsonNode plan;
    final EvidenceEnvelope envelope;
    final Map<String,EvidenceEnvelope> artifacts;
    final List<AtomReference> donors, acceptors;
    final HbondCandidateSources chemistry;
    final Map<AtomReference,List<EvidenceEnvelope>> assignments;
    final SortedSet<String> reasons = new TreeSet<>();
    final boolean inventoryComplete;

    ImplicitHProxyInputs(SystemStateView state, RuleManifest manifest, RuleRequest request,
                         List<EvidenceEnvelope> inputs) throws Exception {
        var supplied=index(inputs);
        var plans=supplied.values().stream().filter(e->e.evidenceType().equals("athena:implicit-h-proxy-plan")).toList();
        require(plans.size()==1,"one explicit implicit-H proxy plan required");
        envelope=plans.getFirst();plan=read(envelope);
        fields(plan,"schema","stateBinding","donorAtoms","acceptorAtoms","artifacts","hybridizationEvidence","inventoryCoverage");
        require(text(plan,"schema").equals("athena-implicit-h-proxy-plan/1")&&binding(plan.get("stateBinding")).equals(state.binding()),"proxy plan schema/state mismatch");
        donors=atoms(plan.get("donorAtoms"),state);acceptors=atoms(plan.get("acceptorAtoms"),state);
        require(!donors.isEmpty()&&!acceptors.isEmpty()&&Collections.disjoint(donors,acceptors),"nonempty disjoint proxy scopes required");
        var selected=new TreeMap<String,EvidenceEnvelope>();
        for(var p:array(plan,"artifacts")){var e=pinned(p,supplied);require(!e.evidenceType().equals("athena:implicit-h-proxy-plan"),"proxy plan cannot select a plan");selected.put(canonical(e.reference()),e);}
        artifacts=Collections.unmodifiableMap(selected);
        var reports=new TreeMap<String,EvidenceEnvelope>();var conflicting=new TreeSet<String>();
        for(var e:selected.values())if(e.evidenceType().equals("athena:group-identities")){
            new HbondCandidateSources(state,manifest,request,List.of(e));
            var n=read(e);String key=canonical(n.get("componentReference"))+":"+n.path("definition").path("groupId").asText();
            var prior=reports.putIfAbsent(key,e);if(prior!=null&&!read(prior).equals(n))conflicting.add(key);
        }
        conflicting.forEach(reports::remove);
        if(!conflicting.isEmpty())reasons.add("conflicting source chemistry reports: "+conflicting);
        chemistry=new HbondCandidateSources(state,manifest,request,List.copyOf(reports.values()));
        var assigned=new TreeMap<AtomReference,List<EvidenceEnvelope>>();var identities=new TreeMap<String,EvidenceInterpretation>();
        var values=new TreeMap<AtomReference,Set<String>>();var protocols=new TreeMap<String,JsonNode>();
        for(var p:array(plan,"hybridizationEvidence")){
            var e=pinned(p,artifacts);var record=new EvidenceExchange().decodeRecord(e.readPayload());
            require(record instanceof EvidenceInterpretation,"assignment interpretation required");var i=(EvidenceInterpretation)record;
            var v=i.measurements();
            require("SOURCE_ATOM_HYBRIDIZATION".equals(v.get("proposition"))&&canonical(state.binding()).equals(v.get("stateBinding"))
                    &&!i.subjects().isEmpty()&&i.subjects().stream().allMatch(s->s.state().equals(state.identity()))
                    &&e.method().equals(i.evaluator()),"assignment proposition/state/method mismatch");
            var a=atom(JSON.readTree(v.get("atom")),state);
            require(donors.contains(a)||acceptors.contains(a),"assignment outside declared atom scopes");
            require(v.get("hybridization")!=null&&!v.get("hybridization").isBlank(),"assignment value missing");
            var protocol=JSON.readTree(v.get("assignmentProtocol"));pinned(protocol,artifacts);
            require(i.inputs().size()>=2,"assignment must pin source report and protocol");
            var dependencyPins=new HashSet<String>();
            for(var dependency:i.inputs()){var d=JSON.valueToTree(dependency);pinned(d,artifacts);dependencyPins.add(canonical(d));}
            require(dependencyPins.size()>=2&&dependencyPins.contains(canonical(protocol)),"assignment protocol not independently pinned among inputs");
            var previous=identities.putIfAbsent(canonical(i.reference()),i);
            require(previous==null||previous.equals(i),"conflicting assignment interpretation identity");
            var list=assigned.computeIfAbsent(a,k->new ArrayList<>());if(!list.contains(e))list.add(e);
            values.computeIfAbsent(a,k->new TreeSet<>()).add(i.status()+":"+v.get("hybridization"));
            protocols.put(canonical(protocol),protocol);
        }
        assigned.replaceAll((a,es)->es.stream().sorted(Comparator.comparing(e->canonical(e.reference()))).toList());
        assignments=Collections.unmodifiableMap(assigned);
        values.forEach((a,vs)->{if(vs.size()>1)reasons.add("conflicting unqualified assignment assertions: "+a);});
        for(var a:java.util.stream.Stream.concat(donors.stream(),acceptors.stream()).toList())
            reasons.add((assigned.containsKey(a)?"unqualified assignment producer/protocol: ":"missing source SP3 assignment: ")+a);
        String scope=hash(Map.of("definitionSha256",RuleRegistry.digest(manifest),"stateBinding",state.binding(),"donorAtoms",donors,"acceptorAtoms",acceptors,
                "classPairs",HbondCandidateRules.parameter(manifest,"classPairs"),"assignmentProtocolPins",protocols.values()));
        for(var p:array(plan,"inventoryCoverage"))require(!EventInputs.interpretation(pinned(p,artifacts),state.binding(),scope).inputs().isEmpty(),"inventory must pin source inventory/selection protocol");
        inventoryComplete=EventInputs.coverage(array(plan,"inventoryCoverage"),artifacts,state.binding(),scope);
        if(!inventoryComplete)reasons.add("independent inventory exhaustiveness not established");
        reasons.add("SP3 assignment producer/protocol not selected, reviewed or independently qualified; activation blocked");
    }
    private static AtomReference atom(JsonNode n,SystemStateView state)throws Exception{
        fields(n,"chainId","residueNumber","insertionCode","atomName");var a=JSON.treeToValue(n,AtomReference.class);
        require(state.atoms().containsKey(a),"absent source atom");return a;
    }
    private static List<AtomReference> atoms(JsonNode n,SystemStateView state)throws Exception{
        require(n!=null&&n.isArray(),"explicit atom array required");var found=new TreeSet<AtomReference>();
        for(var a:n)require(found.add(atom(a,state)),"duplicate source atom");
        return found.stream().sorted(Comparator.comparing(EventPayload::canonical)).toList();
    }
}
