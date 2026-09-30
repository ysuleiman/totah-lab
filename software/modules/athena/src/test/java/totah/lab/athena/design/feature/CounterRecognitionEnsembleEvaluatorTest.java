package totah.lab.athena.design.feature;

import org.junit.jupiter.api.Test;
import totah.lab.gaia.geometry.Point3D;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class CounterRecognitionEnsembleEvaluatorTest {
    @Test void evaluatesEveryTemplateAndRetainsAmbiguousCompatibleFeatures() {
        var feature1=new LigandFeature("f1", LigandFeature.Type.HYDROPHOBE,Set.of("a"),new Point3D(0,0,0),Map.of("source","test"));
        var feature2=new LigandFeature("f2", LigandFeature.Type.HYDROPHOBE,Set.of("b"),new Point3D(1,0,0),Map.of("source","test"));
        var candidate=new CounterRecognitionEnsembleEvaluator.Candidate(
                Map.of("a",new Point3D(0,0,0),"b",new Point3D(1,0,0),"c",new Point3D(0,1,0)),
                Map.of("a","C","b","C","c","C"),List.of(feature1,feature2),Map.of("a","a","b","b","c","c"));
        var template=new CounterRecognitionEnsembleEvaluator.Template("t","family",
                Map.of("a",new Point3D(0,0,0),"b",new Point3D(1,0,0),"c",new Point3D(0,1,0)),
                Map.of("R:9",List.of(new Point3D(.5,0,0))),.5);
        var result=new CounterRecognitionEnsembleEvaluator(1.0).evaluate(candidate,List.of(template));
        assertEquals(1,result.alignedMatches().size());
        assertEquals(2,result.matches().getFirst().residueMatches().getFirst().compatibleAssignments().size());
    }
}
