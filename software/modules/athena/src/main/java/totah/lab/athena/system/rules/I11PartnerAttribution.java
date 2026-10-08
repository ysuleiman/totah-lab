package totah.lab.athena.system.rules;

import totah.lab.athena.system.*;
import totah.lab.mnemosyne.*;
import java.time.Instant;
import java.util.*;
import static totah.lab.athena.system.rules.EventPayload.*;
import static totah.lab.mnemosyne.EvidenceInterpretation.Status.*;

/** Literal I11 source-partner proposition; never infers roles from names or source scope. */
final class I11PartnerAttribution {
    static final String ID="ATHENA.I11.SOURCE_PARTNER_ATTRIBUTION";
    private I11PartnerAttribution() { }
    static EvidenceInterpretation.Status check(SystemStateView s,SystemStateView.Component ligand,SystemStateView.Component protein,Map<String,EvidenceEnvelope> inputs,Instant at)throws Exception {
        var statuses=new HashSet<EvidenceInterpretation.Status>();boolean authority=true;var identities=new HashMap<String,EvidenceInterpretation>();
        for(var e:inputs.values()) {
            if(!e.evidenceType().equals("athena:event-source"))continue;
            var record=new EvidenceExchange().decodeRecord(e.readPayload());if(!(record instanceof EvidenceInterpretation i)||!ID.concat("/1").equals(i.measurements().get("proposition")))continue;
            for(var dependency:i.inputs())if(!inputs.containsKey(canonical(dependency.reference())))return UNKNOWN_INCONCLUSIVE;
            var v=i.measurements();require(v.keySet().equals(Set.of("proposition","stateBinding","ligandComponentReference","proteinComponentReference","ligandAtoms","proteinAtoms","stateSnapshotPin","sourceProtocol","originalSourcePins")),"I11 attribution fields");
            require(v.get("stateBinding").equals(canonical(s.binding()))&&v.get("ligandComponentReference").equals(canonical(ligand.identity()))&&v.get("proteinComponentReference").equals(canonical(protein.identity())),"I11 attribution exact selected partners/state required");
            require(!ligand.identity().equals(protein.identity())&&e.method().equals(i.evaluator())&&!i.subjects().isEmpty()&&i.subjects().stream().allMatch(x->x.state().equals(s.identity())),"I11 attribution producer/subjects mismatch");
            var prior=identities.putIfAbsent(canonical(i.reference()),i);require(prior==null||prior.equals(i),"I11 attribution identity collision");
            var la=ligand.correspondenceAlternatives().getFirst().values().stream().sorted(Comparator.comparing(EventPayload::canonical)).toList();var pa=protein.correspondenceAlternatives().getFirst().values().stream().sorted(Comparator.comparing(EventPayload::canonical)).toList();
            require(new HashSet<>(la).size()==la.size()&&new HashSet<>(pa).size()==pa.size()&&Collections.disjoint(la,pa)&&canonical(la).equals(v.get("ligandAtoms"))&&canonical(pa).equals(v.get("proteinAtoms")),"I11 attribution exhaustive disjoint atom sets required");
            var snapshot=JSON.readTree(v.get("stateSnapshotPin"));var se=pinned(snapshot,inputs);require(se.evidenceType().equals("athena:system-state")&&read(se).equals(JSON.readTree(canonical(s.snapshot()))),"I11 original bound snapshot mismatch");
            var protocol=JSON.readTree(v.get("sourceProtocol"));var pe=pinned(protocol,inputs);require(pe.evidenceType().equals("athena:source-artifact"),"I11 source protocol artifact required");
            var originals=JSON.readTree(v.get("originalSourcePins"));require(originals.isArray()&&!originals.isEmpty(),"I11 original source witnesses required");var expected=new TreeSet<String>();expected.add(canonical(snapshot));require(expected.add(canonical(protocol)),"I11 independent protocol pin required");var ordered=new ArrayList<String>();
            for(var p:originals){var source=pinned(p,inputs);require(source.evidenceType().equals("athena:source-artifact")&&!source.payloadSha256().equals(pe.payloadSha256())&&!source.payloadSha256().equals(se.payloadSha256())&&expected.add(canonical(p)),"I11 distinct original source pin required");ordered.add(canonical(p));}
            require(ordered.equals(ordered.stream().sorted().toList()),"I11 original source pins must be canonical ordered");
            var actual=new TreeSet<String>();for(var p:i.inputs()){pinned(JSON.valueToTree(p),inputs);require(actual.add(canonical(p)),"I11 duplicate input pin");}require(actual.equals(expected),"I11 exact source input union required");
            require(Set.of(SUPPORTED_PRESENT,UNKNOWN_INCONCLUSIVE,UNSUPPORTED).contains(i.status()),"I11 attribution status is not chemistry absence");statuses.add(i.status());
            boolean qualified=false;
            for(var me:inputs.values())if(me.evidenceType().equals("athena:rule-manifest")){
                var m=totah.lab.athena.system.rules.research.ResearchDocuments.decode(me.readPayload(),RuleManifest.class);
                if(!m.ruleId().equals(ID)||!m.schema().equals("athena-rule/3")||m.scientificSources().stream().noneMatch(p->p.sha256().equals(e.payloadSha256()))||m.scientificSources().stream().noneMatch(p->p.sha256().equals(pe.payloadSha256())))continue;
                var time=S1Qualification.current(m,s,null,inputs);qualified|=time.isPresent()&&time.get().equals(at)&&i.recordedAt().equals(at);
            }authority &=qualified;
        }
        if(statuses.isEmpty()||statuses.size()>1)return UNKNOWN_INCONCLUSIVE;
        return authority?statuses.iterator().next():NOT_EVALUATED;
    }
}
