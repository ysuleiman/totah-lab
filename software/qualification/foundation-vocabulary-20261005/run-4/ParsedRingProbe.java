import com.actelion.research.chem.*;
public class ParsedRingProbe {
 public static void main(String[] args)throws Exception {
  var target=new StereoMolecule();new SmilesParser().parse(target,"c1ccccc1");
  for(String q:new String[]{"[*]1~[*]~[*]~[*]~[*]~[*]~1","[c]1:[c]:[c]:[c]:[c]:[c]:1","[*]1~[*]~[*]~[*]~[*]:[*]~1"}) {
   var f=new StereoMolecule();new SmilesParser(SmilesParser.SMARTS_MODE_IS_SMARTS).parse(f,q);var search=new SSSearcher();search.setMol(f,target);int n=search.findFragmentInMolecule(SSSearcher.cCountModeRigorous,SSSearcher.cDefaultMatchMode);
   System.out.println(q+" n="+n+" fragment="+f.isFragment());
   for(int i=0;i<f.getAllAtoms();i++)System.out.println("atom "+i+" qf="+f.getAtomQueryFeatures(i)+" arom="+f.isAromaticAtom(i)+" match="+search.areAtomsSimilar(0,i));
   for(int i=0;i<f.getAllBonds();i++)System.out.println("bond "+i+" qf="+f.getBondQueryFeatures(i)+" type="+f.getBondType(i)+" arom="+f.isAromaticBond(i)+" match="+search.areBondsSimilar(0,i));
  }
 }
}
