package totah.lab.athena.design.backend;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class MolecularGraphDeltaTest {
    @Test void completeBeforeAfterReplayIncludesEveryChemicalDimensionAndDeletion() {
        var before=new MolecularGraph(List.of(atom("a","C",null,0,0,false,"R",0),atom("b","N",14,1,2,false,"S",1)),List.of(bond("ab","a","b",MolecularGraph.BondOrder.SINGLE,false,"E")),Map.of("source","parent"));
        var after=new MolecularGraph(List.of(atom("a","N",15,-1,1,true,"S",2),atom("c","C",13,0,0,true,"R",3)),List.of(bond("ab","c","a",MolecularGraph.BondOrder.AROMATIC,true,"Z"),bond("new","a","c",MolecularGraph.BondOrder.DOUBLE,false,"NONE")),Map.of("source","child"));
        // Delta represents even invalid/rejected intermediate graphs; validation is a separate stage.
        var delta=MolecularGraph.Delta.between(before,after);
        assertEquals(after,delta.replay(before)); assertEquals(before,delta.before());
        assertEquals(3,delta.atoms().size());assertEquals(2,delta.bonds().size());assertEquals(3,delta.coordinates().size());
        var change=delta.atoms().getFirst();
        assertEquals(0,change.before().formalCharge());assertEquals(-1,change.after().formalCharge());
        assertEquals(15,change.after().isotope());assertEquals(1,change.after().explicitHydrogens());
        assertTrue(change.after().aromatic());assertEquals("S",change.after().stereochemistry());
        assertNull(change.after().coordinates());
        assertNull(delta.atoms().get(1).after());assertNull(delta.atoms().get(2).before());
        assertEquals("c",delta.bonds().getFirst().after().firstAtomId());
        assertEquals("Z",delta.bonds().getFirst().after().stereochemistry());
        assertThrows(IllegalArgumentException.class,()->delta.replay(after));
        assertThrows(IllegalArgumentException.class,()->new MolecularGraph.Delta(before,after,List.of(),List.of(),List.of()));
    }
    @Test void coordinatesAreSeparateFromChemicalGraphChanges() {
        var a=new MolecularGraph(List.of(atom("a","C",null,0,0,false,"NONE",0)),List.of(),Map.of());
        var b=new MolecularGraph(List.of(atom("a","C",null,0,0,false,"NONE",1)),List.of(),Map.of());
        var delta=MolecularGraph.Delta.between(a,b);
        assertFalse(delta.chemicalGraphChanged());assertEquals(1,delta.coordinates().size());assertEquals(b,delta.replay(a));
    }
    @Test void inPlaceAtomAndBondEditsHaveCompleteDeltasAndPreserveChargeStereoAndHydrogens() {
        var graph=new MolecularGraph(List.of(atom("a","C",13,1,2,false,"R",0),atom("b","C",null,0,0,false,"S",1)),List.of(bond("ab","a","b",MolecularGraph.BondOrder.SINGLE,false,"E")),Map.of());
        var engine=new GraphEditTransactionEngine();
        var atomEdit=new GraphEdit("atom","v",GraphEdit.Type.ATOM_SUBSTITUTION,Set.of("a"),Set.of(),null,null,"N",null,Map.of());
        var auth=new GraphEditTransactionEngine.Authorization("v",Set.of(GraphEdit.Type.ATOM_SUBSTITUTION,GraphEdit.Type.AUTHORIZED_BOND_MODIFICATION),Set.of("a","b"),Set.of(),Set.of());
        var result=engine.apply(graph,atomEdit,auth);
        assertEquals(1,result.receipt().delta().atoms().size());assertEquals(result.product(),result.receipt().delta().replay(graph));
        var retained=result.product().atom("a").orElseThrow();assertEquals(1,retained.formalCharge());assertEquals(13,retained.isotope());assertEquals(2,retained.explicitHydrogens());assertEquals("R",retained.stereochemistry());
        var bondEdit=new GraphEdit("bond","v",GraphEdit.Type.AUTHORIZED_BOND_MODIFICATION,Set.of(),Set.of("ab"),null,null,null,MolecularGraph.BondOrder.DOUBLE,Map.of());
        var modified=engine.apply(result.product(),bondEdit,auth);
        assertEquals(1,modified.receipt().delta().bonds().size());assertEquals("E",modified.product().bond("ab").orElseThrow().stereochemistry());
        assertEquals(MolecularGraph.BondOrder.SINGLE,modified.receipt().delta().bonds().getFirst().before().order());
        assertEquals(MolecularGraph.BondOrder.DOUBLE,modified.receipt().delta().bonds().getFirst().after().order());
    }
    @Test void bondDeletionIsExplicit() {
        var before=new MolecularGraph(List.of(atom("a","C",null,0,0,false,"NONE",0),atom("b","C",null,0,0,false,"NONE",1)),List.of(bond("ab","a","b",MolecularGraph.BondOrder.SINGLE,false,"NONE")),Map.of());
        var after=new MolecularGraph(before.atoms(),List.of(),Map.of());
        assertNull(MolecularGraph.Delta.between(before,after).bonds().getFirst().after());
    }
    private static MolecularGraph.Atom atom(String id,String element,Integer isotope,int charge,int h,boolean aromatic,String stereo,double x){return new MolecularGraph.Atom(id,element,isotope,charge,h,aromatic,stereo,new MolecularGraph.Coordinates(x,0,0),Map.of());}
    private static MolecularGraph.Bond bond(String id,String a,String b,MolecularGraph.BondOrder order,boolean aromatic,String stereo){return new MolecularGraph.Bond(id,a,b,order,aromatic,stereo,Map.of());}
}
