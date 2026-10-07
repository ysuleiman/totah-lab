package totah.lab.athena.system.rules;

import totah.lab.athena.system.*;
import totah.lab.mnemosyne.*;
import java.util.*;
import static totah.lab.athena.system.rules.EventPayload.*;

/** Checks the approved evidence proposition; coverage never creates chemical facts or authority. */
final class S1SourceScope {
    static final String PROPOSITION="ATHENA.I03.SP3_SOURCE_SCOPE/1";
    record Checked(boolean complete,boolean conflicting,boolean knownOutside,List<EvidenceEnvelope> witnesses,List<String> reasons) { }
    private S1SourceScope() { }
    static Checked check(SystemStateView s,SystemStateView.Component c,Map<String,EvidenceEnvelope> artifacts)throws Exception {
        var witnesses=new ArrayList<EvidenceEnvelope>();var values=new TreeSet<String>();var reasons=new TreeSet<String>();
        var identities=new HashMap<String,EvidenceInterpretation>();boolean complete=true,knownOutside=false;
        for(var e:artifacts.values()) {
            if(!e.evidenceType().equals("athena:event-source"))continue;
            var record=new EvidenceExchange().decodeRecord(e.readPayload());
            require(record instanceof EvidenceInterpretation,"source interpretation required");var i=(EvidenceInterpretation)record;
            var v=i.measurements();boolean v2="ATHENA.I03.SP3_SOURCE_SCOPE/2".equals(v.get("proposition"));if(!v2&&!PROPOSITION.equals(v.get("proposition")))continue;
            require(canonical(s.binding()).equals(v.get("stateBinding")),"source scope state mismatch");
            if(!canonical(c.identity()).equals(v.get("componentReference"))) {
                require(s.components().stream().anyMatch(x->canonical(x.identity()).equals(v.get("componentReference"))),"source scope component absent");continue;
            }
            require(v.keySet().equals(Set.of("proposition","stateBinding","componentReference","atoms","connectionCoverage","electronicStateCoverage","sourceProtocol")),"source scope fields");
            require(e.method().equals(i.evaluator())&&!i.subjects().isEmpty()&&i.subjects().stream().allMatch(x->x.state().equals(s.identity())),"source scope method/subjects mismatch");
            require((v2?Set.of("COMPLETE_ORDINARY_COVALENT_NO_COORDINATION","UNKNOWN","KNOWN_NON_ORDINARY_CONNECTION"):Set.of("COMPLETE_ORDINARY_COVALENT_NO_COORDINATION","UNKNOWN")).contains(v.get("connectionCoverage"))&&Set.of("COMPLETE_EXPLICIT_NONE","UNKNOWN").contains(v.get("electronicStateCoverage")),"source scope coverage tokens");
            var previous=identities.putIfAbsent(canonical(i.reference()),i);require(previous==null||previous.equals(i),"source scope identity collision");
            if(c.correspondenceAlternatives().size()!=1){complete=false;reasons.add("source scope mapping unresolved");}
            else {
                var mapping=c.correspondenceAlternatives().getFirst();
                var atoms=mapping.values().stream().sorted(Comparator.comparing(EventPayload::canonical)).toList();
                require(canonical(atoms).equals(v.get("atoms")),"source scope atom selection mismatch");
                if(mapping.size()!=c.chemistry().atoms().size()||new HashSet<>(mapping.values()).size()!=mapping.size()){complete=false;reasons.add("source scope mapping incomplete/noninjective");}
            }
            var protocol=JSON.readTree(v.get("sourceProtocol"));var protocolEnvelope=pinned(protocol,artifacts);
            var pins=new HashSet<String>();boolean boundSource=false,originalSource=false;var contentPins=new HashSet<String>();
            for(var dependency:i.inputs()) {
                var p=JSON.valueToTree(dependency);var input=pinned(p,artifacts);pins.add(canonical(p));contentPins.add(input.payloadSha256());
                originalSource |=input.evidenceType().equals("athena:source-artifact")&&!input.payloadSha256().equals(protocolEnvelope.payloadSha256());
                if(input.evidenceType().equals("athena:group-source-coverage")||input.evidenceType().equals("athena:group-identities")) {
                    var n=read(input);if(n.path("stateBinding").equals(JSON.valueToTree(s.binding()))&&n.path("componentReference").equals(JSON.valueToTree(c.identity())))boundSource=true;
                }
            }
            require(pins.size()>=3&&contentPins.size()>=3&&pins.contains(canonical(protocol))&&boundSource&&originalSource,"source scope must independently pin protocol, original source and bound source report");
            values.add(i.status()+":"+v.get("connectionCoverage")+":"+v.get("electronicStateCoverage"));
            complete &=i.status()==EvidenceInterpretation.Status.SUPPORTED_PRESENT&&v.get("connectionCoverage").equals("COMPLETE_ORDINARY_COVALENT_NO_COORDINATION")&&v.get("electronicStateCoverage").equals("COMPLETE_EXPLICIT_NONE");
            if(v2&&v.get("connectionCoverage").equals("KNOWN_NON_ORDINARY_CONNECTION")){
                require(i.status()==EvidenceInterpretation.Status.UNSUPPORTED&&!i.reasons().isEmpty()&&i.reasons().stream().allMatch(reason->reason!=null&&!reason.isBlank()),"known nonordinary scope requires explicit attributed exclusion witness reasons");
                knownOutside=true;reasons.addAll(i.reasons());
            }
            witnesses.add(e);
        }
        boolean conflict=values.size()>1;
        if(witnesses.isEmpty())reasons.add("source-scope interpretation missing");
        if(conflict)reasons.add("conflicting source-scope assertions");
        if(!complete&&!knownOutside)reasons.add("source-scope completeness unresolved");
        return new Checked(complete&&!conflict&&!witnesses.isEmpty(),conflict,knownOutside&&!conflict,List.copyOf(witnesses),List.copyOf(reasons));
    }
}
