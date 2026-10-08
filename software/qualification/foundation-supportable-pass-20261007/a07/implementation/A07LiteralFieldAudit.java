import com.actelion.research.chem.*;
import com.actelion.research.chem.ugly.PainsDetector;
import java.util.*;
public class A07LiteralFieldAudit {
 public static void main(String[] args)throws Exception {
  var f=PainsDetector.class.getDeclaredField("PAINS");f.setAccessible(true);var rows=(String[][])f.get(null);
  for(int i:new int[]{113,159,162,169,202,207,840}){
   var q=new IDCodeParser(false).getCompactMolecule(rows[i][0]);var mass=new TreeSet<Integer>();var radical=new TreeSet<Integer>();var valence=new TreeSet<Integer>();var bond=new TreeSet<Integer>();
   for(int a=0;a<q.getAllAtoms();a++){mass.add(q.getAtomMass(a));radical.add(q.getAtomRadical(a));valence.add(q.getAtomAbnormalValence(a));}
   for(int b=0;b<q.getAllBonds();b++)bond.add(q.getBondType(b));
   System.out.println(i+" mass="+mass+" radical="+radical+" abnormalValence="+valence+" bondType="+bond);
  }
 }
}
