package totah.lab.athena.design.backend.ocl;

import com.actelion.research.chem.Molecule;
import com.actelion.research.chem.RingCollection;
import totah.lab.athena.design.backend.MolecularBackendException;
import totah.lab.athena.design.backend.MolecularGraph;
import totah.lab.athena.design.feature.LigandFeature;
import totah.lab.athena.design.feature.LigandFeaturePerceptionService;
import totah.lab.gaia.geometry.Point3D;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** OpenChemLib-backed, target-neutral ligand feature perception. */
public final class OclLigandFeaturePerceiver implements LigandFeaturePerceptionService {
    private final OclGraphMapper mapper = new OclGraphMapper();
    @Override public Result perceive(MolecularGraph graph) throws MolecularBackendException {
        var mapping=mapper.toOcl(graph);var mol=mapping.molecule();mol.ensureHelperArrays(Molecule.cHelperRings);
        List<LigandFeature> out=new ArrayList<>();
        for(int i=0;i<mol.getAllAtoms();i++){
            String id=mapping.idByMapNumber().get(mol.getAtomMapNo(i));if(id==null)continue;
            int z=mol.getAtomicNo(i),charge=mol.getAtomCharge(i);Point3D p=point(mol,i);Set<String>atoms=Set.of(id);
            if((z==7||z==8||z==16)&&charge<=0&&!amideNitrogen(mol,i))add(out,id,"hba",LigandFeature.Type.H_BOND_ACCEPTOR,atoms,p);
            if((z==7||z==8||z==16)&&(mol.getAllHydrogens(i)>0||explicitHydrogenNeighbor(mol,i)))add(out,id,"hbd",LigandFeature.Type.H_BOND_DONOR,atoms,p);
            if((z==6||z==9||z==17||z==35||z==53)&&charge==0)add(out,id,"hydrophobe",LigandFeature.Type.HYDROPHOBE,atoms,p);
            if(charge>0)add(out,id,"positive",LigandFeature.Type.POSITIVE_CENTER,atoms,p);
            if(charge<0)add(out,id,"negative",LigandFeature.Type.NEGATIVE_CENTER,atoms,p);
            if(z==9||z==17||z==35||z==53)add(out,id,"halogen",LigandFeature.Type.HALOGEN,atoms,p);
        }
        RingCollection rings=mol.getRingSet();
        for(int r=0;r<rings.getSize();r++)if(rings.isAromatic(r)){
            Set<String>ids=new LinkedHashSet<>();double x=0,y=0,z=0;int[]ra=rings.getRingAtoms(r);
            for(int atom:ra){String id=mapping.idByMapNumber().get(mol.getAtomMapNo(atom));if(id!=null)ids.add(id);x+=mol.getAtomX(atom);y+=mol.getAtomY(atom);z+=mol.getAtomZ(atom);}
            Point3D c=new Point3D(x/ra.length,y/ra.length,z/ra.length);String key="ring:"+String.join("-",ids);
            add(out,key,"aromatic",LigandFeature.Type.AROMATIC_RING,ids,c);add(out,key,"pi",LigandFeature.Type.PI_FEATURE,ids,c);
        }
        return new Result(out,OclMolecularBackend.BACKEND,OclMolecularBackend.VERSION,List.of("all ambiguity-preserving feature occurrences returned"));
    }
    private static boolean explicitHydrogenNeighbor(com.actelion.research.chem.StereoMolecule m,int a){for(int i=0;i<m.getAllConnAtoms(a);i++)if(m.getAtomicNo(m.getConnAtom(a,i))==1)return true;return false;}
    private static boolean amideNitrogen(com.actelion.research.chem.StereoMolecule m,int a){if(m.getAtomicNo(a)!=7)return false;for(int i=0;i<m.getConnAtoms(a);i++){int c=m.getConnAtom(a,i);if(m.getAtomicNo(c)!=6)continue;for(int j=0;j<m.getConnAtoms(c);j++){int o=m.getConnAtom(c,j);int b=m.getBond(c,o);if(m.getAtomicNo(o)==8&&m.getBondOrder(b)==2)return true;}}return false;}
    private static Point3D point(com.actelion.research.chem.StereoMolecule m,int i){return new Point3D(m.getAtomX(i),m.getAtomY(i),m.getAtomZ(i));}
    private static void add(List<LigandFeature>x,String id,String suffix,LigandFeature.Type t,Set<String>a,Point3D p){x.add(new LigandFeature(id+":"+suffix,t,a,p,Map.of("perception","OCL_GRAPH_CHEMISTRY")));}
}
