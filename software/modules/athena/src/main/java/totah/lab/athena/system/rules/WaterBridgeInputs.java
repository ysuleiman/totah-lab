package totah.lab.athena.system.rules;

import com.fasterxml.jackson.databind.JsonNode;
import totah.lab.athena.system.*;
import totah.lab.gaia.structure.AtomReference;
import totah.lab.mnemosyne.*;
import java.util.*;
import static totah.lab.athena.system.rules.EventPayload.*;

/** The approved water-plan selection only; inventory assertions never supply chemistry. */
final class WaterBridgeInputs {
    static final String WATER = "ATHENA.GROUP.WATER.NEUTRAL_H2";
    final JsonNode plan;
    final EvidenceEnvelope envelope;
    final Map<String,EvidenceEnvelope> artifacts;
    final List<AtomReference> first, second, waters;
    final HbondCandidateSources chemistry;
    final boolean inventoryComplete;
    final List<String> conflicts;

    WaterBridgeInputs(SystemStateView state, RuleManifest manifest, RuleRequest request,
                      List<EvidenceEnvelope> inputs) throws Exception {
        var supplied=index(inputs);
        var plans=supplied.values().stream().filter(e->e.evidenceType().equals("athena:water-bridge-plan")).toList();
        require(plans.size()==1,"one explicit water bridge plan required");
        envelope=plans.getFirst();plan=read(envelope);
        fields(plan,"schema","stateBinding","first","second","waters","inventoryCoverage","artifacts","minimumWaters","maximumWaters");
        require(text(plan,"schema").equals("athena-water-bridge-plan/1") && binding(plan.get("stateBinding")).equals(state.binding()),"water plan state/schema mismatch");
        first=atoms(plan.get("first"),state);second=atoms(plan.get("second"),state);waters=atoms(plan.get("waters"),state);
        require(!first.isEmpty()&&!second.isEmpty()&&Collections.disjoint(first,second)
                &&Collections.disjoint(first,waters)&&Collections.disjoint(second,waters),"disjoint water endpoint scopes required");
        for(var w:waters)require(state.atoms().get(w).getElement()==totah.lab.gaia.chemistry.Element.O,"water selection must name source oxygen");
        require(plan.get("minimumWaters").isIntegralNumber()&&plan.get("minimumWaters").canConvertToInt()
                &&plan.get("maximumWaters").isIntegralNumber()&&plan.get("maximumWaters").canConvertToInt(),"integer water bounds required");
        int min=plan.get("minimumWaters").intValue(),max=plan.get("maximumWaters").intValue();
        require(min>=1&&max>=min&&max<Integer.MAX_VALUE,"invalid water bounds");
        require(manifest.ruleId().endsWith("SINGLE")?min==1&&max==1:min>=2,"water count outside selected profile");
        var selected=new TreeMap<String,EvidenceEnvelope>();
        for(var p:array(plan,"artifacts")){var e=pinned(p,supplied);require(!e.evidenceType().equals("athena:water-bridge-plan"),"water plan cannot select another plan");selected.put(canonical(e.reference()),e);}
        artifacts=Collections.unmodifiableMap(selected);
        var reports=new TreeMap<String,EvidenceEnvelope>();var bad=new TreeSet<String>();
        for(var e:selected.values())if(e.evidenceType().equals("athena:group-identities")) {
            // Validate every report, including contradictory alternatives, before selecting any.
            new HbondCandidateSources(state,manifest,request,List.of(e));
            var n=read(e);String key=canonical(n.get("componentReference"))+":"+n.path("definition").path("groupId").asText();
            var prior=reports.putIfAbsent(key,e);
            if(prior!=null&&!read(prior).equals(n))bad.add(key);
        }
        bad.forEach(reports::remove);conflicts=List.copyOf(bad);
        chemistry=new HbondCandidateSources(state,manifest,request,List.copyOf(reports.values()));
        String scope=hash(Map.of("definitionSha256",RuleRegistry.digest(manifest),"stateBinding",state.binding(),
                "first",first,"second",second,"waters",waters,"minimumWaters",min,"maximumWaters",max));
        for(var p:array(plan,"inventoryCoverage")) {
            var i=EventInputs.interpretation(pinned(p,artifacts),state.binding(),scope);
            require(!i.inputs().isEmpty(),"inventory coverage must pin its source inventory/selection protocol");
        }
        inventoryComplete=EventInputs.coverage(array(plan,"inventoryCoverage"),artifacts,state.binding(),scope);
    }
    private static List<AtomReference> atoms(JsonNode array,SystemStateView state)throws Exception {
        require(array!=null&&array.isArray(),"explicit atom array required");var found=new TreeSet<AtomReference>();
        for(var n:array){fields(n,"chainId","residueNumber","insertionCode","atomName");var a=JSON.treeToValue(n,AtomReference.class);
            require(found.add(a)&&state.atoms().containsKey(a),"duplicate or absent source atom");}
        return found.stream().sorted(Comparator.comparing(EventPayload::canonical)).toList();
    }
}
