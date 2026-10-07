package totah.lab.athena.system.rules;

import com.fasterxml.jackson.databind.JsonNode;
import totah.lab.athena.design.backend.MolecularGraph;
import java.util.*;

/** Approved S1 formal source-graph model. No coordinates, toolkit assignment or inferred H. */
final class S1NitrogenPredicate {
    static final String PROTOCOL="ATHENA.I03.N_SP3.SATURATED_CARBON_ATTACHMENTS/1";
    static final String RADICAL="athena.ocl.atomRadicalState/1";
    private static final Set<String> ELEMENTS=Set.of("H","C","N","O","S","F","Cl","Br","I");
    enum Decision { SP3, NOT_SP3, UNSUPPORTED, UNKNOWN }
    record Result(Decision decision,List<String> reasons) { }
    private S1NitrogenPredicate() { }
    static Result evaluate(MolecularGraph graph,String nitrogen,JsonNode coverage,boolean admittedRole,
                           boolean completeSourceScope) {
        graph.validateTopology(false);
        var n=graph.atom(nitrogen).orElseThrow(()->new IllegalArgumentException("selected source atom absent"));
        var unknown=new TreeSet<String>();var outside=new TreeSet<String>();var conflict=new TreeSet<String>();
        if(!n.element().equals("N"))outside.add("selected atom is not nitrogen");
        if(!coverage.path("completeGraph").asText().equals("SUPPORTED_PRESENT"))unknown.add("component topology not complete");
        if(!completeSourceScope)unknown.add("lossless connection and electronic source scope not established");
        var implicit=new HashMap<String,Integer>();
        for(var a:graph.atoms()) {
            String id=a.id();var facts=coverage.path("atomState").path(id);
            if(!ELEMENTS.contains(a.element()))outside.add("unsupported component element: "+id);
            String radical=a.properties().get(RADICAL);
            if(radical==null||!Set.of("NONE","S","D","T").contains(radical))unknown.add("explicit source electronic state unresolved: "+id);
            else if(!radical.equals("NONE"))outside.add("component is not explicitly closed shell: "+id);
            if(!facts.path("chargeStatus").asText().equals("SUPPORTED_PRESENT")||(!facts.path("formalCharge").isIntegralNumber()||!facts.path("formalCharge").canConvertToInt()))unknown.add("source charge unresolved: "+id);
            else if(facts.path("formalCharge").asInt()!=a.formalCharge())conflict.add("source charge conflict: "+id);
            if(!facts.path("aromaticityStatus").asText().equals("SUPPORTED_PRESENT")||!facts.path("aromaticityModel").asText().equals("OCL/2026.7.2"))unknown.add("source aromaticity unresolved: "+id);
            String mode=facts.path("hydrogenMode").asText();var count=facts.path("implicitHydrogenCount");
            if(!Set.of("EXPLICIT_GRAPH","AUTHORITATIVE_IMPLICIT").contains(mode)||!count.isIntegralNumber()||!count.canConvertToInt()||count.asInt()<0)unknown.add("source H inventory unresolved: "+id);
            else implicit.put(id,count.asInt());
            var actual=new TreeSet<String>();for(var b:incident(graph,id)) {var other=graph.atom(other(b,id)).orElseThrow();if(other.element().equals("H"))actual.add(other.id());}
            var listed=new TreeSet<String>();boolean valid=facts.path("explicitHydrogenAtomIds").isArray();
            for(var h:facts.path("explicitHydrogenAtomIds"))valid &=h.isTextual()&&listed.add(h.asText());
            if(!valid||!actual.equals(listed))conflict.add("explicit H topology/coverage conflict: "+id);
            if(a.element().equals("H")) {
                var edges=incident(graph,id);
                if(a.formalCharge()!=0||a.aromatic()||edges.size()!=1||edges.stream().anyMatch(b->!single(b))||implicit.getOrDefault(id,0)!=0)
                    outside.add("explicit H outside neutral terminal single-bond model: "+id);
            }
        }
        if(!conflict.isEmpty())return result(Decision.UNKNOWN,conflict);
        // Known exclusions do not invent unresolved facts; conflicts are resolved above first.
        if(n.formalCharge()!=0&&knownCharge(coverage,n.id()))outside.add("charged N outside S1");
        if(!outside.isEmpty())return result(Decision.UNSUPPORTED,outside);
        var edges=incident(graph,n.id());
        boolean boundedNegative=n.aromatic()||edges.stream().anyMatch(b->b.order()==MolecularGraph.BondOrder.DOUBLE||b.order()==MolecularGraph.BondOrder.TRIPLE);
        if(boundedNegative) {
            if(!unknown.isEmpty())return result(Decision.UNKNOWN,unknown);
            return result(Decision.NOT_SP3,Set.of("complete neutral closed-shell N has aromatic or multiple-bond source witness; bounded S1 incompatibility only"));
        }
        int heavy=0;
        for(var b:edges) {
            var a=graph.atom(other(b,n.id())).orElseThrow();
            if(!single(b))outside.add("N bond outside ordinary nonaromatic SINGLE model: "+b.id());
            if(a.element().equals("H"))continue;
            heavy++;
            if(!a.element().equals("C")){outside.add("noncarbon heavy attachment: "+a.id());continue;}
            if(a.formalCharge()!=0&&knownCharge(coverage,a.id()))outside.add("charged direct carbon: "+a.id());
            if(a.aromatic()&&knownAromaticity(coverage,a.id()))outside.add("aromatic direct carbon: "+a.id());
            var carbonEdges=incident(graph,a.id());
            if(carbonEdges.stream().anyMatch(e->!single(e)))outside.add("unsaturated direct carbon: "+a.id());
            if(implicit.containsKey(a.id())&&carbonEdges.size()+implicit.get(a.id())!=4)outside.add("direct carbon is not four-single-bond saturated carbon: "+a.id());
        }
        if(heavy<1||heavy>3)outside.add("N carbon attachment count outside 1..3");
        if(implicit.containsKey(n.id())&&edges.size()+implicit.get(n.id())!=3)outside.add("N does not have exactly three source single bonds including H");
        if(!outside.isEmpty())return result(Decision.UNSUPPORTED,outside);
        if(!admittedRole)unknown.add("no independently replayed occurrence in the five unchanged admission roles");
        if(!unknown.isEmpty())return result(Decision.UNKNOWN,unknown);
        return result(Decision.SP3,Set.of("S1 formal graph contract satisfied; no geometry or physical hybridization claim"));
    }
    private static boolean knownCharge(JsonNode c,String id){return c.path("atomState").path(id).path("chargeStatus").asText().equals("SUPPORTED_PRESENT");}
    private static boolean knownAromaticity(JsonNode c,String id){var f=c.path("atomState").path(id);return f.path("aromaticityStatus").asText().equals("SUPPORTED_PRESENT")&&f.path("aromaticityModel").asText().equals("OCL/2026.7.2");}
    private static boolean single(MolecularGraph.Bond b){return b.order()==MolecularGraph.BondOrder.SINGLE&&!b.aromatic();}
    private static List<MolecularGraph.Bond> incident(MolecularGraph g,String id){return g.bonds().stream().filter(b->b.firstAtomId().equals(id)||b.secondAtomId().equals(id)).toList();}
    private static String other(MolecularGraph.Bond b,String id){return b.firstAtomId().equals(id)?b.secondAtomId():b.firstAtomId();}
    private static Result result(Decision decision,Collection<String> reasons){return new Result(decision,List.copyOf(reasons));}
}
