package totah.lab.athena.design.feature;

import org.junit.jupiter.api.Test;
import totah.lab.gaia.geometry.Point3D;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class LineageAwareFeatureMapperTest {
    @Test void inheritsOnlyChemicallyCompatibleSurvivingFeatureAndReturnsNovelAmbiguity() {
        var features=List.of(feature("o:hba",LigandFeature.Type.H_BOND_ACCEPTOR,"newO"),
                feature("n:hba",LigandFeature.Type.H_BOND_ACCEPTOR,"newN"));
        var parents=List.of(new LineageAwareFeatureMapper.ParentAssociation("arbitrary-feature",
                LigandFeature.Type.H_BOND_ACCEPTOR,Set.of("oldO")),new LineageAwareFeatureMapper.ParentAssociation(
                "destroyed-feature",LigandFeature.Type.HYDROPHOBE,Set.of("oldC")));
        var result=new LineageAwareFeatureMapper().map(parents,features,Map.of("oldO","newO"),
                Map.of("arbitrary-feature",Set.of(LigandFeature.Type.H_BOND_ACCEPTOR),
                        "destroyed-feature",Set.of(LigandFeature.Type.HYDROPHOBE)));
        assertThat(result.inherited()).extracting(LineageAwareFeatureMapper.Assignment::candidateFeatureId).containsExactly("o:hba");
        assertThat(result.novel()).extracting(LineageAwareFeatureMapper.Assignment::candidateFeatureId).contains("n:hba");
        assertThat(result.invalidated()).extracting(LineageAwareFeatureMapper.Assignment::grammarFeatureId).containsExactly("destroyed-feature");
    }
    @Test void alignsAnUnrelatedToyConfigurationWithoutTargetKnowledge(){
        var candidate=Map.of("x",new Point3D(1,0,0),"y",new Point3D(2,0,0),"z",new Point3D(1,1,0));
        var template=Map.of("x",new Point3D(0,0,0),"y",new Point3D(1,0,0),"z",new Point3D(0,1,0));
        var result=new InvariantFrameAligner().align(candidate,template);
        assertThat(result.aligned()).isTrue();assertThat(result.rmsd()).isLessThan(1e-9);
    }
    @Test void rejectsCollinearInvariantCorrespondencesAsRankDeficient(){
        var points=Map.of("x",new Point3D(0,0,0),"y",new Point3D(1,0,0),"z",new Point3D(2,0,0));
        var result=new InvariantFrameAligner().align(points,points);
        assertThat(result.aligned()).isFalse();assertThat(result.symmetryAmbiguous()).isTrue();
        assertThat(result.reason()).contains("rank-deficient");
    }
    @Test void rejectsCoincidentInvariantCorrespondencesAsRankDeficient(){
        var points=Map.of("x",new Point3D(1,1,1),"y",new Point3D(1,1,1),"z",new Point3D(1,1,1));
        assertThat(new InvariantFrameAligner().align(points,points).aligned()).isFalse();
    }
    private static LigandFeature feature(String id,LigandFeature.Type type,String atom){return new LigandFeature(id,type,Set.of(atom),new Point3D(0,0,0),Map.of());}
}
