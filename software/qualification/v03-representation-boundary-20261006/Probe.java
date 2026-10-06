import com.actelion.research.chem.*;
import totah.lab.athena.design.backend.ocl.*;
public class Probe { public static void main(String[] args)throws Exception {
for(String s:new String[]{"C","[CH3]","[CH2]","[O]","[13CH4]","[Na+]","*"}){
var m=new SmilesParser(SmilesParser.SMARTS_MODE_IS_SMILES).parseMolecule(s);
System.out.print(s+" OCL radical="+m.getAtomRadical(0)+" mass="+m.getAtomMass(0));
try {var g=new OclMolecularBackend().decodeStructure("SMILES",s);System.out.println(" decoded="+g.atoms());}catch(Exception e){System.out.println(" ERROR="+e.getMessage());}
}}
}
