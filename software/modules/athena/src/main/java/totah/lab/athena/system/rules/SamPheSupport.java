package totah.lab.athena.system.rules;
import com.fasterxml.jackson.databind.JsonNode;
import totah.lab.athena.system.*;
import totah.lab.gaia.structure.AtomReference;
import totah.lab.mnemosyne.*;
import java.time.Instant;
import java.util.*;
import static totah.lab.athena.system.rules.EventPayload.*;
import static totah.lab.mnemosyne.EvidenceInterpretation.Status.*;
/** Reuses the qualified finite G03 Phe source predicates verbatim; no new aromatic chemistry. */
final class SamPheSupport {
    private SamPheSupport() { }
    static EvidenceInterpretation.Status check(SystemStateView s,RuleManifest m,RuleRequest request,
            Map<String,EvidenceEnvelope> inputs,List<AtomReference> selected,Optional<Instant> at)throws Exception {
        if(selected.size()!=6)return UNKNOWN_INCONCLUSIVE;
        var c=HalogenCarbonylRules.component(s,selected.getFirst(),selected.getLast());
        if(c==null||!c.correspondenceAlternatives().getFirst().values().containsAll(selected))return UNKNOWN_INCONCLUSIVE;
        if(c.chemistry().atoms().size()>request.maximumNodes())return UNKNOWN_INCONCLUSIVE;
        try{c.chemistry().validateTopology(true);}catch(IllegalArgumentException ex){return UNKNOWN_INCONCLUSIVE;}
        var scope=S1SourceScope.check(s,c,inputs);
        if(scope.conflicting()||scope.witnesses().isEmpty())return UNKNOWN_INCONCLUSIVE;
        if(at.isPresent())for(var w:scope.witnesses())if(!S1Qualification.scope(w,s,inputs,at.get()))return NOT_EVALUATED;
        if(scope.knownOutside())return UNSUPPORTED;
        if(!scope.complete())return UNKNOWN_INCONCLUSIVE;
        JsonNode coverage=null;
        for(var e:inputs.values())if(e.evidenceType().equals("athena:group-source-coverage")){
            var n=read(e);if(n.path("componentReference").equals(JSON.valueToTree(c.identity()))){if(coverage!=null&&!coverage.equals(n))return UNKNOWN_INCONCLUSIVE;coverage=n;}}
        if(coverage==null)return UNKNOWN_INCONCLUSIVE;
        ZincCarbonylRules.verifyCoverage(s,c,coverage);var status=HalogenCarbonylRules.sourceFacts(s,c,coverage,inputs.values());
        if(status!=SUPPORTED_PRESENT)return status;
        if(!coverage.path("completeGraph").asText().equals("SUPPORTED_PRESENT"))return UNKNOWN_INCONCLUSIVE;
        for(var chain:s.graph().structure().getChains())for(var residue:chain.residues())if(chain.id().equals(selected.getFirst().chainId())&&residue.getNumber()==selected.getFirst().residueNumber()&&!residue.getName().equals("PHE"))return UNSUPPORTED;
        var roles=sourceRoles(s,c,selected.getFirst(),"PHE",List.of("N","CA","C","O","CB","CG","CD1","CE1","CZ","CE2","CD2"));
        if(roles==null)return UNKNOWN_INCONCLUSIVE;
        var map=c.correspondenceAlternatives().getFirst();
        var expected=List.of("CG","CD1","CE1","CZ","CE2","CD2").stream().map(n->map.get(roles.get(n))).toList();
        if(!new HashSet<>(expected).equals(new HashSet<>(selected)))return UNKNOWN_INCONCLUSIVE;
        var context=new Context(c,coverage);if(!context.backbone(roles)||!context.phe(roles))return UNSUPPORTED;
        var isotopes=selected.stream().map(a->c.chemistry().atom(HalogenCarbonylRules.sourceId(c,a)).orElseThrow().isotope()).toList();
        if(isotopes.stream().anyMatch(i->!Objects.equals(i,isotopes.getFirst())))return UNSUPPORTED;
        var sources=new HbondCandidateSources(s,m,request,List.copyOf(inputs.values()));var ring=sources.report(c,"ATHENA.G03.SOURCE.CARBON_RING");
        if(ring==null||!ring.complete())return UNKNOWN_INCONCLUSIVE;
        return ringMatch(ring,selected)?SUPPORTED_PRESENT:UNSUPPORTED;
    }
    private static boolean ringMatch(HbondCandidateSources.Report r,List<AtomReference> selected){var map=r.component().correspondenceAlternatives().getFirst();for(var occurrence:r.payload().path("occurrences"))for(var roles:occurrence.path("roleCorrespondenceAlternatives")){var found=new HashSet<AtomReference>();for(var id:roles.path("ringMembers"))found.add(map.get(id.asText()));if(found.equals(new HashSet<>(selected)))return true;}return false;}
    private static boolean sameResidue(AtomReference a,AtomReference b){return a.chainId().equals(b.chainId())&&a.residueNumber()==b.residueNumber()&&a.insertionCode()==b.insertionCode();}
    private static Map<String,String> sourceRoles(SystemStateView s,SystemStateView.Component c,AtomReference selected,String label,List<String> names) {
        var roles=new TreeMap<String,String>();int count=0;
        for(var chain:s.graph().structure().getChains())for(var residue:chain.residues())if(chain.id().equals(selected.chainId())&&residue.getNumber()==selected.residueNumber()&&Objects.equals(residue.getInsertionCode(),selected.insertionCode()==' '?null:selected.insertionCode())) {
            count++;if(!label.equals(residue.getName()))return null;
            for(var name:names){var atoms=residue.getAtoms().stream().filter(a->a.getName().equals(name)).toList();if(atoms.size()!=1)return null;
                var ids=c.correspondenceAlternatives().getFirst().entrySet().stream().filter(e->sameResidue(e.getValue(),selected)&&e.getValue().atomName().equals(name)).map(Map.Entry::getKey).toList();if(ids.size()!=1)return null;roles.put(name,ids.getFirst());}
        }return count==1?roles:null;
    }
    /** Exact finite source-graph admission, without valence, peptide-sequence or geometry inference. */
    private record Context(SystemStateView.Component component,JsonNode coverage) {
        totah.lab.athena.design.backend.MolecularGraph.Atom atom(String id){return component.chemistry().atom(id).orElseThrow();}
        Map<String,String> neighbors(String id){var n=new TreeMap<String,String>();for(var b:component.chemistry().bonds()){String other=b.firstAtomId().equals(id)?b.secondAtomId():b.secondAtomId().equals(id)?b.firstAtomId():null;if(other!=null)n.put(other,b.aromatic()&&!b.order().name().equals("AROMATIC")?"NONCANONICAL_AROMATIC_SOURCE":b.order().name());}return n;}
        boolean fact(String id,String element,boolean aromatic,int h){var a=atom(id);return a.element().equals(element)&&a.formalCharge()==0&&a.aromatic()==aromatic&&coverage.path("atomState").path(id).path("implicitHydrogenCount").asInt(-1)==h;}
        boolean pattern(String id,String e,boolean aromatic,int h,Map<String,String> neighbors){return fact(id,e,aromatic,h)&&neighbors(id).equals(neighbors);}
        boolean single(String id,String e,int h,String... n){var expected=new TreeMap<String,String>();for(var x:n)expected.put(x,"SINGLE");return pattern(id,e,false,h,expected);}
        boolean phe(Map<String,String> r){if(!single(r.get("CB"),"C",2,r.get("CA"),r.get("CG")))return false;var names=List.of("CG","CD1","CE1","CZ","CE2","CD2");for(int i=0;i<6;i++){var n=new TreeMap<String,String>();n.put(r.get(names.get((i+5)%6)),"AROMATIC");n.put(r.get(names.get((i+1)%6)),"AROMATIC");if(i==0)n.put(r.get("CB"),"SINGLE");if(!pattern(r.get(names.get(i)),"C",true,i==0?0:1,n))return false;}return true;}
        boolean backbone(Map<String,String> r){String n=r.get("N"),ca=r.get("CA"),c=r.get("C"),o=r.get("O");
            if(!single(ca,"C",1,n,r.get("CB"),c)||!pattern(o,"O",false,0,Map.of(c,"DOUBLE"))||!fact(n,"N",false,1)||!fact(c,"C",false,0))return false;
            var nn=neighbors(n);var cn=neighbors(c);if(nn.size()!=2||!"SINGLE".equals(nn.get(ca))||cn.size()!=3||!"SINGLE".equals(cn.get(ca))||!"DOUBLE".equals(cn.get(o)))return false;
            String previous=nn.keySet().stream().filter(x->!x.equals(ca)).findFirst().orElseThrow();String following=cn.keySet().stream().filter(x->!x.equals(ca)&&!x.equals(o)).findFirst().orElseThrow();
            var map=component.correspondenceAlternatives().getFirst();if(sameResidue(map.get(previous),map.get(n))||sameResidue(map.get(following),map.get(c))||!"SINGLE".equals(nn.get(previous))||!"SINGLE".equals(cn.get(following))||!fact(previous,"C",false,0))return false;
            var pn=neighbors(previous);if(pn.size()!=3||!"SINGLE".equals(pn.get(n)))return false;var oxygen=pn.entrySet().stream().filter(e->e.getValue().equals("DOUBLE")&&atom(e.getKey()).element().equals("O")).map(Map.Entry::getKey).toList();if(oxygen.size()!=1)return false;String po=oxygen.getFirst();
            if(!sameResidue(map.get(previous),map.get(po))||!pattern(po,"O",false,0,Map.of(previous,"DOUBLE")))return false;
            String pc=pn.keySet().stream().filter(x->!x.equals(n)&&!x.equals(po)).findFirst().orElseThrow();var a=atom(pc);if(!a.element().equals("C")||a.aromatic()||a.formalCharge()!=0||!"SINGLE".equals(pn.get(pc)))return false;
            var fn=neighbors(following);if(fn.size()<1||fn.size()>3||!fact(following,"N",false,3-fn.size()))return false;for(var e:fn.entrySet())if(!e.getValue().equals("SINGLE")||!atom(e.getKey()).element().equals("C"))return false;return true;
        }
    }
}
