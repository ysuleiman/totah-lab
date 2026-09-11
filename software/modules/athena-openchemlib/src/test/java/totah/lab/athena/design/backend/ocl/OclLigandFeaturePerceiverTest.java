package totah.lab.athena.design.backend.ocl;

import org.junit.jupiter.api.Test;
import totah.lab.athena.design.backend.MolecularGraph;
import totah.lab.athena.design.feature.LigandFeature;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class OclLigandFeaturePerceiverTest {
    @Test void perceivesChemistryWithoutTargetOrResidueIdentifiers() throws Exception {
        var atoms=List.of(atom("C", "C",false,0,0,0),atom("O","O",false,1.2,0,0),atom("H","H",false,2,0,0));
        var bonds=List.of(bond("b1","C","O"),bond("b2","O","H"));
        var result=new OclLigandFeaturePerceiver().perceive(new MolecularGraph(atoms,bonds,Map.of()));
        assertThat(result.features()).extracting(LigandFeature::type)
                .contains(LigandFeature.Type.H_BOND_ACCEPTOR,LigandFeature.Type.H_BOND_DONOR,LigandFeature.Type.HYDROPHOBE);
        assertThat(result.features()).allMatch(feature -> feature.evidence().values().stream().noneMatch(v -> v.matches(".*\\d{2,3}.*")));
    }
    private static MolecularGraph.Atom atom(String id,String e,boolean aromatic,double x,double y,double z){return new MolecularGraph.Atom(id,e,null,0,0,aromatic,"UNSPECIFIED",new MolecularGraph.Coordinates(x,y,z),Map.of());}
    private static MolecularGraph.Bond bond(String id,String a,String b){return new MolecularGraph.Bond(id,a,b,MolecularGraph.BondOrder.SINGLE,false,"UNSPECIFIED",Map.of());}
}
