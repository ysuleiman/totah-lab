package totah.lab.athena.system.rules;

import com.fasterxml.jackson.databind.JsonNode;
import totah.lab.athena.design.backend.MolecularGraph;
import totah.lab.athena.system.*;
import totah.lab.gaia.structure.*;
import java.util.*;
import static totah.lab.athena.system.rules.EventPayload.*;
import static totah.lab.mnemosyne.EvidenceInterpretation.Status.*;

/** ATHENA.WATER.CANDIDATE_UNIVERSE/1; exclusions are pinned scientific contract, not inference. */
final class WaterIdentity {
    record Result(AtomReference oxygen,String identity,List<AtomReference> hydrogen,
                  HbondCandidateSources.Anchor anchor,boolean oriented,List<String> reasons) {
        boolean possible(){return !identity.equals("DEFINITELY_NOT_WATER");}
        Map<String,Object> payload(){return Map.of("oxygen",oxygen,"hydrogen",hydrogen,
                "sourceReportPins",anchor==null?List.of():List.of(pin(anchor.report().envelope())),
                "assessment",oriented?SUPPORTED_PRESENT.name():possible()?UNKNOWN_INCONCLUSIVE.name():UNSUPPORTED.name(),
                "reasons",reasons);}
    }
    private WaterIdentity() { }
    static Result assess(SystemStateView s,WaterBridgeInputs inputs,AtomReference oxygen)throws Exception {
        return assess(s,inputs.chemistry,oxygen);
    }
    static Result assess(SystemStateView s,HbondCandidateSources chemistry,AtomReference oxygen)throws Exception {
        var candidates=new ArrayList<SystemStateView.Component>();
        for(var c:s.components())if(c.correspondenceAlternatives().stream().anyMatch(m->m.containsValue(oxygen)))candidates.add(c);
        if(candidates.size()!=1||candidates.getFirst().correspondenceAlternatives().size()!=1)return unknown(oxygen,"unresolved component correspondence");
        var c=candidates.getFirst();var mapping=c.correspondenceAlternatives().getFirst();
        var ids=mapping.entrySet().stream().filter(e->e.getValue().equals(oxygen)).map(Map.Entry::getKey).toList();
        if(ids.size()!=1)return unknown(oxygen,"ambiguous oxygen mapping");String id=ids.getFirst();
        var report=chemistry.report(c,WaterBridgeInputs.WATER);
        if(report==null)return unknown(oxygen,"missing/conflicting independently qualified source chemistry");
        var graph=c.chemistry();var atom=graph.atom(id).orElseThrow();
        if(!atom.element().equals("O"))return unknown(oxygen,"source element disagreement");
        var coverage=report.payload().path("sourceCoverage");var a=coverage.path("atomState").path(id);
        var graphH=new TreeSet<String>();var bonds=new ArrayList<MolecularGraph.Bond>();
        for(var b:graph.bonds())if(b.firstAtomId().equals(id)||b.secondAtomId().equals(id)){
            bonds.add(b);if(graph.atom(other(b,id)).orElseThrow().element().equals("H"))graphH.add(other(b,id));}
        var stated=new TreeSet<String>();a.path("explicitHydrogenAtomIds").forEach(n->stated.add(n.asText()));
        boolean knownH=!a.path("hydrogenMode").asText().equals("UNKNOWN")&&a.path("implicitHydrogenCount").isIntegralNumber()
                &&a.path("implicitHydrogenCount").asInt()>=0;
        boolean contradiction=!graphH.equals(stated)
                ||knownCharge(a)&&a.path("formalCharge").asInt()!=atom.formalCharge()
                ||knownCharge(a)&&(!s.charges().charges().containsKey(oxygen)||s.charges().charge(oxygen)!=atom.formalCharge())
                ||knownH&&a.path("hydrogenMode").asText().equals("EXPLICIT_GRAPH")&&a.path("implicitHydrogenCount").asInt()!=0;
        if(contradiction)return unknown(oxygen,"contradictory source identity facts");
        boolean topology=sourceBondsAgree(s,c)&&s.graph().structure().getConnectivityMetadata().provenance()==ConnectivityProvenance.EXPLICIT;
        var exclusions=new TreeSet<String>();
        if(knownCharge(a)&&atom.formalCharge()!=0)exclusions.add("E1 known nonneutral oxygen");
        if(a.path("aromaticityStatus").asText().equals("SUPPORTED_PRESENT")&&a.path("aromaticityModel").asText().equals("OCL/2026.7.2")&&atom.aromatic())exclusions.add("E2 known aromatic oxygen");
        if(topology)for(var b:bonds){var n=graph.atom(other(b,id)).orElseThrow();
            if(!n.element().equals("H"))exclusions.add("E3 heavy covalent neighbor");
            else {
                if(b.order()!=MolecularGraph.BondOrder.SINGLE)exclusions.add("E5 non-single O-H");
                var h=coverage.path("atomState").path(n.id());
                if(knownCharge(h)&&(h.path("formalCharge").asInt()!=n.formalCharge()||!s.charges().charges().containsKey(mapping.get(n.id()))||s.charges().charge(mapping.get(n.id()))!=n.formalCharge()))return unknown(oxygen,"contradictory hydrogen charge");
                if(knownCharge(h)&&n.formalCharge()!=0)exclusions.add("E6 charged bound hydrogen");
                if(graph.bonds().stream().filter(x->x.firstAtomId().equals(n.id())||x.secondAtomId().equals(n.id())).count()>1)exclusions.add("E6 shared covalent hydrogen");
            }
        }
        if(topology&&(graphH.size()>2||knownH&&coverage.path("completeGraph").asText().equals("SUPPORTED_PRESENT")&&graphH.size()+a.path("implicitHydrogenCount").asInt()!=2))exclusions.add("E4 known non-water hydrogen count");
        if(!exclusions.isEmpty())return new Result(oxygen,"DEFINITELY_NOT_WATER",List.of(),null,false,List.copyOf(exclusions));
        var descriptor=JSON.readTree("[{\"id\":\""+WaterBridgeInputs.WATER+"\",\"role\":\"oxygen\"}]");
        var anchors=chemistry.anchors(c,descriptor).stream().filter(x->x.atom().equals(oxygen)).toList();
        if(!topology||!report.complete()||anchors.isEmpty())return unknown(oxygen,"water identity coverage incomplete");
        var anchor=anchors.getFirst();var hs=HbondCandidateEnumeration.hydrogens(s,anchor);
        boolean oriented=s.frameQualified()&&hs.size()==2&&knownH&&a.path("implicitHydrogenCount").asInt()==0;
        for(var h:hs){var ha=graph.atom(mapping.entrySet().stream().filter(e->e.getValue().equals(h)).findFirst().orElseThrow().getKey()).orElseThrow();
            var st=coverage.path("atomState").path(ha.id());oriented&=knownCharge(st)&&ha.formalCharge()==0&&finite(s,h)&&distancePositive(s,oxygen,h);}
        oriented&=finite(s,oxygen);
        return new Result(oxygen,"QUALIFIED_WATER",hs,anchor,oriented,List.of(oriented?"qualified source water and explicit H orientation":"qualified identity; complete explicit H orientation unavailable"));
    }
    private static Result unknown(AtomReference o,String reason){return new Result(o,"UNRESOLVED_WATER_ID",List.of(),null,false,List.of(reason));}
    private static boolean knownCharge(JsonNode n){return n.path("chargeStatus").asText().equals("SUPPORTED_PRESENT")&&n.path("formalCharge").isIntegralNumber();}
    private static String other(MolecularGraph.Bond b,String id){return b.firstAtomId().equals(id)?b.secondAtomId():b.firstAtomId();}
    static boolean finite(SystemStateView s,AtomReference a){var p=s.atoms().get(a).getPosition();return p!=null&&Double.isFinite(p.x())&&Double.isFinite(p.y())&&Double.isFinite(p.z());}
    private static boolean distancePositive(SystemStateView s,AtomReference a,AtomReference b){return finite(s,a)&&finite(s,b)&&s.atoms().get(a).getPosition().distance(s.atoms().get(b).getPosition())>0;}
    private static boolean sourceBondsAgree(SystemStateView s,SystemStateView.Component c){
        var m=c.correspondenceAlternatives().getFirst();if(m.size()!=c.chemistry().atoms().size()||new HashSet<>(m.values()).size()!=m.size())return false;
        var expected=new HashSet<Bond>();for(var b:c.chemistry().bonds()){if(!m.containsKey(b.firstAtomId())||!m.containsKey(b.secondAtomId()))return false;expected.add(new Bond(m.get(b.firstAtomId()),m.get(b.secondAtomId()),totah.lab.gaia.chemistry.BondOrder.valueOf(b.order().name())));}
        var actual=new HashSet<Bond>();s.graph().structure().bonds().stream().filter(b->m.containsValue(b.atom1())||m.containsValue(b.atom2())).forEach(actual::add);
        return expected.equals(actual);
    }
}
