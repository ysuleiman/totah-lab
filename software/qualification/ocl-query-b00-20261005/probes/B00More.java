package totah.lab.athena.design.backend.ocl;
import totah.lab.athena.design.backend.*;import java.util.*;
public class B00More {
 public static void main(String[]args)throws Exception{
 var b=new OclMolecularBackend();
 for(var e:Map.of("n",B00Probe.graph("N",0,false,MolecularGraph.BondOrder.SINGLE),"n+",B00Probe.graph("N",1,false,MolecularGraph.BondOrder.SINGLE),"ring",B00Probe.graph("CCCCCC",0,true,MolecularGraph.BondOrder.AROMATIC),"explicit",B00Probe.graph("COH",0,false,MolecularGraph.BondOrder.SINGLE)).entrySet())
 for(String q:List.of("[N]","[N+]","[N+0]","[NH3]","[NH4+]","[H]","[OH1]","[A]","[a]","[C;!a]","[C;r6]"))try{System.out.println(e.getKey()+" "+q+" "+b.match(q,e.getValue()).queryToTargetAtomIds());}catch(Exception ex){System.out.println("ERROR "+ex);}
 var atom=new MolecularGraph.Atom("c","C",null,0,1,false,"UNSPECIFIED",null,Map.of());
 System.out.println("bad-H "+b.match("[CH4]",new MolecularGraph(List.of(atom),List.of(),Map.of())).queryToTargetAtomIds());
 }
}
