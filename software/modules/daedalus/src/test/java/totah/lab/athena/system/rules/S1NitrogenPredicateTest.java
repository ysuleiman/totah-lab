package totah.lab.athena.system.rules;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.*;
import totah.lab.athena.design.backend.MolecularGraph;
import java.util.*;
import java.util.stream.*;
import static org.junit.jupiter.api.Assertions.*;
import static totah.lab.athena.system.rules.EventPayload.JSON;

/** Hand-authored formal graph oracles, not toolkit hybridization assignments. */
class S1NitrogenPredicateTest {
    record Example(MolecularGraph graph,ObjectNode coverage) { }
    static Example graph(String elements,String edges,String h) {
        var as=new ArrayList<MolecularGraph.Atom>();var bs=new ArrayList<MolecularGraph.Bond>();var hs=h.split(",");var es=elements.split(",");
        for(int i=0;i<es.length;i++)as.add(new MolecularGraph.Atom("a"+i,es[i],null,0,0,false,null,null,Map.of(S1NitrogenPredicate.RADICAL,"NONE")));
        int bi=0;for(var edge:edges.split(" "))if(!edge.isEmpty()){var v=edge.split("[-=#~]");var order=edge.contains("=")?MolecularGraph.BondOrder.DOUBLE:edge.contains("#")?MolecularGraph.BondOrder.TRIPLE:edge.contains("~")?MolecularGraph.BondOrder.AROMATIC:MolecularGraph.BondOrder.SINGLE;bs.add(new MolecularGraph.Bond("b"+bi++,"a"+v[0],"a"+v[1],order,order==MolecularGraph.BondOrder.AROMATIC,null,Map.of()));}
        var g=new MolecularGraph(as,bs,Map.of("source","hand-authored formal S1 oracle"));var c=JSON.createObjectNode().put("completeGraph","SUPPORTED_PRESENT");var states=c.putObject("atomState");
        for(int i=0;i<es.length;i++) {var v=states.putObject("a"+i);v.put("chargeStatus","SUPPORTED_PRESENT").put("formalCharge",0).put("hydrogenMode","AUTHORITATIVE_IMPLICIT").put("implicitHydrogenCount",Integer.parseInt(hs[i])).put("aromaticityStatus","SUPPORTED_PRESENT").put("aromaticityModel","OCL/2026.7.2");var ids=v.putArray("explicitHydrogenAtomIds");for(var b:bs){String other=b.firstAtomId().equals("a"+i)?b.secondAtomId():b.secondAtomId().equals("a"+i)?b.firstAtomId():null;if(other!=null&&g.atom(other).orElseThrow().element().equals("H"))ids.add(other);}}
        return new Example(g,c);
    }
    static Example atom(Example f,int index,Integer charge,Boolean aromatic,String radical,Integer isotope,MolecularGraph.Coordinates coords) {
        var as=new ArrayList<>(f.graph.atoms());var a=as.get(index);var props=new TreeMap<>(a.properties());if(radical!=null){if(radical.equals("MISSING"))props.remove(S1NitrogenPredicate.RADICAL);else props.put(S1NitrogenPredicate.RADICAL,radical);}
        as.set(index,new MolecularGraph.Atom(a.id(),a.element(),isotope,charge==null?a.formalCharge():charge,a.explicitHydrogens(),aromatic==null?a.aromatic():aromatic,a.stereochemistry(),coords,props));
        if(charge!=null)((ObjectNode)f.coverage.path("atomState").path(a.id())).put("formalCharge",charge);
        return new Example(new MolecularGraph(as,f.graph.bonds(),f.graph.properties()),f.coverage);
    }
    static Example primary(){return graph("N,C","0-1","2,3");}
    static String decision(Example f){return S1NitrogenPredicate.evaluate(f.graph,"a0",f.coverage,true,true).decision().name();}
    @TestFactory Stream<DynamicTest> exactSourceGraphMatrix() {
        return Stream.of(
            new String[]{"S1-001 primary","N,C","0-1","2,3","SP3"},
            new String[]{"S1-002 secondary","N,C,C","0-1 0-2","1,3,3","SP3"},
            new String[]{"S1-003 tertiary","N,C,C,C","0-1 0-2 0-3","0,3,3,3","SP3"},
            new String[]{"S1-011 allylamine","N,C,C,C","0-1 1-2 2=3","2,2,1,2","SP3"},
            new String[]{"S1-012 alpha-carbonyl","N,C,C,O","0-1 1-2 2=3","2,2,1,0","SP3"},
            new String[]{"S1-013 beta-heteroatom","N,C,C,O","0-1 1-2 2-3","2,2,2,1","SP3"},
            new String[]{"S1-014 aziridine","N,C,C","0-1 1-2 2-0","1,2,2","SP3"},
            new String[]{"S1-015 pyrrolidine","N,C,C,C,C","0-1 1-2 2-3 3-4 4-0","1,2,2,2,2","SP3"},
            new String[]{"S1-016 piperidine","N,C,C,C,C,C","0-1 1-2 2-3 3-4 4-5 5-0","1,2,2,2,2,2","SP3"},
            new String[]{"S1-017 quinuclidine cage","N,C,C,C,C,C,C,C","0-1 1-2 2-7 0-3 3-4 4-7 0-5 5-6 6-7","0,2,2,2,2,2,2,1","SP3"},
            new String[]{"S1-018 explicit H","N,C,H,H","0-1 0-2 0-3","0,3,0,0","SP3"},
            new String[]{"S1-019 mixed H","N,C,H","0-1 0-2","1,3,0","SP3"},
            new String[]{"S1-022 imine","N,C","0=1","1,2","NOT_SP3"},
            new String[]{"S1-023 nitrile","N,C","0#1","0,1","NOT_SP3"},
            new String[]{"S1-026 amide","N,C,O,C","0-1 1=2 1-3","2,0,0,3","UNSUPPORTED"},
            new String[]{"S1-027 carbamate","N,C,O,O,C","0-1 1=2 1-3 3-4","2,0,0,0,3","UNSUPPORTED"},
            new String[]{"S1-028 urea","N,C,O,N","0-1 1=2 1-3","2,0,0,2","UNSUPPORTED"},
            new String[]{"S1-029 thioamide","N,C,S,C","0-1 1=2 1-3","2,0,0,3","UNSUPPORTED"},
            new String[]{"S1-030 amidine amino N","N,C,N,C","0-1 1=2 1-3","2,0,1,3","UNSUPPORTED"},
            new String[]{"S1-031 guanidine amino N","N,C,N,N","0-1 1=2 1-3","2,0,1,2","UNSUPPORTED"},
            new String[]{"S1-032 sulfonamide","N,S,O,O,C","0-1 1=2 1=3 1-4","2,0,0,0,3","UNSUPPORTED"},
            new String[]{"S1-036 enamine","N,C,C","0-1 1=2","2,1,2","UNSUPPORTED"},
            new String[]{"S1-037 ynamine","N,C,C","0-1 1#2","2,0,1","UNSUPPORTED"},
            new String[]{"S1-042 hydrazine","N,N","0-1","2,2","UNSUPPORTED"},
            new String[]{"S1-043 hydroxylamine","N,O","0-1","2,1","UNSUPPORTED"},
            new String[]{"S1-044 boron","N,B","0-1","2,2","UNSUPPORTED"},
            new String[]{"S1-045 ammonia","N","","3","UNSUPPORTED"},
            new String[]{"S1-046 four sigma bonds","N,C,C,C,C","0-1 0-2 0-3 0-4","0,3,3,3,3","UNSUPPORTED"},
            new String[]{"S1-047 unusual valence","N,C","0-1","1,3","UNSUPPORTED"},
            new String[]{"S1-053 component metal","N,C,Zn","0-1 1-2","2,2,0","UNSUPPORTED"}
        ).map(v->DynamicTest.dynamicTest(v[0],()->assertEquals(v[4],decision(graph(v[1],v[2],v[3])))));
    }
    @TestFactory Stream<DynamicTest> chargeAndElectronicExclusions(){return Stream.of("N+","N-","C+","C-","N_D","C_S","C_T").map(v->DynamicTest.dynamicTest(v,()->{var f=primary();int index=v.startsWith("N")?0:1;f=atom(f,index,v.contains("+")?Integer.valueOf(1):v.contains("-")?Integer.valueOf(-1):null,null,v.contains("_")?v.substring(2):null,null,null);assertEquals("UNSUPPORTED",decision(f));}));}
    @TestFactory Stream<DynamicTest> missingFacts(){return Stream.of("N_H","C_H","N_aromaticity","C_aromaticity","N_charge","C_charge","N_NONE","C_NONE","topology","scope","role").map(v->DynamicTest.dynamicTest(v,()->{var f=primary();int index=v.startsWith("C")?1:0;var fact=(ObjectNode)f.coverage.path("atomState").path("a"+index);if(v.endsWith("_H"))fact.put("hydrogenMode","UNKNOWN").putNull("implicitHydrogenCount");if(v.endsWith("aromaticity"))fact.put("aromaticityStatus","UNKNOWN_INCONCLUSIVE");if(v.endsWith("charge"))fact.put("chargeStatus","UNKNOWN_INCONCLUSIVE").putNull("formalCharge");if(v.endsWith("NONE"))f=atom(f,index,null,null,"MISSING",null,null);if(v.equals("topology"))f.coverage.put("completeGraph","UNKNOWN_INCONCLUSIVE");assertEquals(S1NitrogenPredicate.Decision.UNKNOWN,S1NitrogenPredicate.evaluate(f.graph,"a0",f.coverage,!v.equals("role"),!v.equals("scope")).decision());}));}
    @Test void completeScopeCannotInsertNone(){var f=atom(primary(),0,null,null,"MISSING",null,null);assertEquals("UNKNOWN",decision(f));}
    @Test void sourceChargeContradictionPrecedesKnownExclusion(){var f=atom(primary(),0,1,null,null,null,null);((ObjectNode)f.coverage.path("atomState").path("a0")).put("formalCharge",0);assertEquals("UNKNOWN",decision(f));}
    @Test void hydrogenListConflict(){var f=primary();((ObjectNode)f.coverage.path("atomState").path("a0")).putArray("explicitHydrogenAtomIds").add("a1");assertEquals("UNKNOWN",decision(f));}
    @Test void isotopeIdentityRetained(){var f=graph("N,C,H,H","0-1 0-2 0-3","0,3,0,0");f=atom(f,0,null,null,null,15,null);f=atom(f,2,null,null,null,2,null);assertEquals("SP3",decision(f));assertEquals(2,f.graph.atoms().get(2).isotope());}
    @Test void noCoordinatesOrGeometryInfluence(){for(double z:new double[]{0,1,100}){var f=atom(primary(),0,null,null,null,null,new MolecularGraph.Coordinates(0,0,z));assertEquals("SP3",decision(f));}assertEquals("SP3",decision(primary()));}
    @Test void aromaticNitrogenOnlyBoundedNegative(){var f=graph("N,C,C,C,C,C","0~1 1~2 2~3 3~4 4~5 5~0","0,1,1,1,1,1");for(int i=0;i<6;i++)f=atom(f,i,null,true,null,null,null);assertEquals("NOT_SP3",decision(f));}
    @Test void anilineAndBenzylamineRemainDifferent(){var aniline=graph("N,C,C,C,C,C,C","0-1 1~2 2~3 3~4 4~5 5~6 6~1","2,0,1,1,1,1,1");for(int i=1;i<7;i++)aniline=atom(aniline,i,null,true,null,null,null);assertEquals("UNSUPPORTED",decision(aniline));var benzyl=graph("N,C,C,C,C,C,C,C","0-1 1-2 2~3 3~4 4~5 5~6 6~7 7~2","2,2,0,1,1,1,1,1");for(int i=2;i<8;i++)benzyl=atom(benzyl,i,null,true,null,null,null);assertEquals("SP3",decision(benzyl));}
    @Test void reorderDoesNotChangeDecision(){var f=primary();var a=new ArrayList<>(f.graph.atoms());Collections.reverse(a);assertEquals(decision(f),decision(new Example(new MolecularGraph(a,f.graph.bonds(),f.graph.properties()),f.coverage)));}
    @Test void pyrroleAromaticBoundary(){var f=graph("N,C,C,C,C","0~1 1~2 2~3 3~4 4~0","1,1,1,1,1");for(int i=0;i<5;i++)f=atom(f,i,null,true,null,null,null);assertEquals("NOT_SP3",decision(f));}
    @Test void ammoniumQuaternaryAndOxideAreOutOfDomain(){for(var f:List.of(graph("N,C","0-1","3,3"),graph("N,C,C,C,C","0-1 0-2 0-3 0-4","0,3,3,3,3"),graph("N,C,C,C,O","0-1 0-2 0-3 0-4","0,3,3,3,0")))assertEquals("UNSUPPORTED",decision(atom(f,0,1,null,null,null,null)));}
    @Test void remoteChargeDoesNotDisqualifyNeutralSaturatedAttachment(){var f=graph("N,C,C,O","0-1 1-2 2-3","2,2,2,0");assertEquals("SP3",decision(atom(f,3,-1,null,null,null,null)));}
    @Test void unsupportedPAttachment(){assertEquals("UNSUPPORTED",decision(graph("N,P","0-1","2,2")));}
    @Test void constrainedAmideCannotBeRescuedByGeometry(){var f=graph("N,C,O,C,C","0-1 1=2 1-3 3-4 4-0","1,0,0,2,2");for(double z:List.of(0.0,3.0,100.0))assertEquals("UNSUPPORTED",decision(atom(f,0,null,null,null,null,new MolecularGraph.Coordinates(0,0,z))));}
    @Test void hinderedAnilineStillExcluded(){var f=graph("N,C,C,C,C,C,C,C,C","0-1 1~2 2~3 3~4 4~5 5~6 6~1 2-7 6-8","2,0,0,1,1,1,0,3,3");for(int i=1;i<=6;i++)f=atom(f,i,null,true,null,null,null);assertEquals("UNSUPPORTED",decision(f));}
    @Test void toolkitHybridizationPropertiesCannotRescueYnamine(){var f=graph("N,C,C","0-1 1#2","2,0,1");var as=new ArrayList<>(f.graph.atoms());var a=as.getFirst();as.set(0,new MolecularGraph.Atom(a.id(),a.element(),a.isotope(),a.formalCharge(),0,a.aromatic(),null,null,Map.of(S1NitrogenPredicate.RADICAL,"NONE","RDKit.hybridization","SP3","OCL.flatNitrogen","false")));assertEquals("UNSUPPORTED",decision(new Example(new MolecularGraph(as,f.graph.bonds(),Map.of()),f.coverage)));}
    @Test void unresolvedChargeCannotOverflowIntoNeutral(){var f=primary();((ObjectNode)f.coverage.path("atomState").path("a0")).put("formalCharge",4294967296L);assertEquals("UNKNOWN",decision(f));}

    @Test void missingBondOrderCoverageCannotBeRecoveredFromOrdinaryProjection(){var f=primary();f.coverage.put("completeGraph","UNKNOWN_INCONCLUSIVE");assertEquals("UNKNOWN",decision(f));}

}
