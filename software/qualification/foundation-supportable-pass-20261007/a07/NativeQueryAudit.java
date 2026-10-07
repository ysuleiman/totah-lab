import com.actelion.research.chem.*;
import com.actelion.research.chem.ugly.PainsDetector;
import java.nio.file.*;
import java.util.*;
/** Read-only research inventory; not a production catalog accessor or matcher. */
public class NativeQueryAudit {
 public static void main(String[] args)throws Exception {
  var field=PainsDetector.class.getDeclaredField("PAINS");field.setAccessible(true);
  var rows=new ArrayList<String>();rows.add("index\tcanonical_native_roundtrip\tatoms\texcluded_atoms\tatom_lists\tquery_atoms\tquery_bonds\tenhanced_stereo_atoms\tdummy_atoms\tcomponents");
  var catalog=(String[][])field.get(null);
  for(int i=0;i<catalog.length;i++) {
   var m=new IDCodeParser(false).getCompactMolecule(catalog[i][0]);m.ensureHelperArrays(Molecule.cHelperNeighbours);
   var code=new Canonizer(m).getIDCode();var r=new IDCodeParser(false).getCompactMolecule(code);
   boolean same=code.equals(new Canonizer(r).getIDCode());int excluded=0,lists=0,qa=0,qb=0,esr=0,dummy=0;
   for(int a=0;a<m.getAllAtoms();a++){if(m.isExcludeGroupAtom(a))excluded++;if(m.getAtomList(a)!=null)lists++;if(m.getAtomQueryFeatures(a)!=0)qa++;if(m.getAtomESRType(a)!=Molecule.cESRTypeAbs)esr++;if(m.getAtomicNo(a)==0)dummy++;}
   for(int b=0;b<m.getAllBonds();b++)if(m.getBondQueryFeatures(b)!=0)qb++;
   int[] fragments=new int[m.getAllAtoms()];int components=m.getFragmentNumbers(fragments,false,true);
   rows.add(i+"\t"+same+"\t"+m.getAllAtoms()+"\t"+excluded+"\t"+lists+"\t"+qa+"\t"+qb+"\t"+esr+"\t"+dummy+"\t"+components);
  }
  Files.write(Path.of(args[0]),rows);
 }
}
