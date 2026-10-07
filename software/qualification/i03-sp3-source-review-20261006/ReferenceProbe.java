// External OpenChemLib characterization only; no Athena classifier or authority.
import com.actelion.research.chem.*;
import java.nio.file.*;
public class ReferenceProbe {
    public static void main(String[] args) throws Exception {
        System.out.println("id\tstatus\tatomIndex\tpi\tflatNitrogen\tcharge\taromatic\ttotalH\tradicalToken\tmetalNeighbors");
        for(String line:Files.readAllLines(Path.of(args[0]))) {
            var row=line.split("\t");
            try {
                var m=new StereoMolecule();new SmilesParser(SmilesParser.SMARTS_MODE_IS_SMILES).parse(m,row[1]);m.ensureHelperArrays(Molecule.cHelperRings);
                int found=-1;for(int a=0;a<m.getAllAtoms();a++)if(m.getAtomMapNo(a)==1){if(found!=-1)throw new IllegalArgumentException("duplicate map");found=a;}
                if(found<0)throw new IllegalArgumentException("target map missing");
                System.out.println(row[0]+"\tPARSED\t"+found+"\t"+m.getAtomPi(found)+"\t"+m.isFlatNitrogen(found)+"\t"+m.getAtomCharge(found)+"\t"+m.isAromaticAtom(found)+"\t"+m.getAllHydrogens(found)+"\t"+m.getAtomRadical(found)+"\t"+m.getMetalBondedConnAtoms(found));
            } catch(Exception e){System.out.println(row[0]+"\tERROR\t"+e.toString().replace('\n',' '));}
        }
    }
}
