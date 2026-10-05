package totah.lab.athena.design.backend.ocl;
import totah.lab.athena.design.backend.*;
import java.util.*;
public class B00Probe {
 static MolecularGraph graph(String elements, int charge, boolean ring, MolecularGraph.BondOrder order){
  var atoms=new ArrayList<MolecularGraph.Atom>();var bonds=new ArrayList<MolecularGraph.Bond>();
  for(int i=0;i<elements.length();i++){atoms.add(new MolecularGraph.Atom("id"+i,""+elements.charAt(i),null,charge,0,false,"UNSPECIFIED",null,Map.of()));if(i>0)bonds.add(new MolecularGraph.Bond("b"+i,"id"+(i-1),"id"+i,order,false,"UNSPECIFIED",Map.of()));}
  if(ring)bonds.add(new MolecularGraph.Bond("close","id"+(elements.length()-1),"id0",order,false,"UNSPECIFIED",Map.of()));return new MolecularGraph(atoms,bonds,Map.of("state","synthetic"));
 }
 public static void main(String[]args)throws Exception{
  var b=new OclMolecularBackend();var g=graph("CCO",0,false,MolecularGraph.BondOrder.SINGLE);
  for(String q:List.of("[O]","[OH1]","[OH0]","C","CC","CO","[#6]~[O]","[$(C-O)]","[C;R]","[C;D1]","[O;+0]","C.O","[C:17]","[z2]","[C;Q]","C(","","[H]","[!#6]"))try{System.out.println(q+" => "+b.match(q,g).queryToTargetAtomIds());}catch(Exception e){System.out.println(q+" => ERROR "+e.getMessage());}
  for(String q:List.of("C","CC","CCC","C.C","[CH3]","[CH2]"))System.out.println("propane "+q+" => "+b.match(q,graph("CCC",0,false,MolecularGraph.BondOrder.SINGLE)).queryToTargetAtomIds());
  for(String q:List.of("[c]","[C]","[c;R]","[c;r6]","[c;r5]","cc","c:c"))System.out.println("benzene "+q+" => "+b.match(q,graph("CCCCCC",0,true,MolecularGraph.BondOrder.AROMATIC)).queryToTargetAtomIds());
 }
}
