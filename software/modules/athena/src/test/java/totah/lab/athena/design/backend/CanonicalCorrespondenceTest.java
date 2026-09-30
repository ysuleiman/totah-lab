package totah.lab.athena.design.backend;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class CanonicalCorrespondenceTest {
    private final CanonicalIdentityService identity = g -> new CanonicalIdentityService.Result("fixture",null);
    @Test void excessiveSymmetryIsExplicitlyTruncatedAndDeterministic() throws Exception {
        var graph=new MolecularGraph(java.util.stream.IntStream.range(0,7).mapToObj(i->atom("a"+i,"UNSPECIFIED")).toList(),List.of(),Map.of());
        var first=identity.correspondence(graph,graph);
        assertFalse(first.exhaustive());assertTrue(first.ambiguous());assertEquals(256,first.alternatives().size());
        assertEquals(first,identity.correspondence(graph,graph));
    }
    @Test void relativeStereoCannotBeProvedByPlainLabelEquality() {
        var graph=new MolecularGraph(List.of(atom("a","PARITY_1")),List.of(),Map.of());
        assertThrows(MolecularBackendException.class,()->identity.correspondence(graph,graph));
    }
    @Test void compositionRefusesIncompleteIntermediateMapping() {
        var mapping=new CanonicalIdentityService.Mapping(Map.of("x","y"),Map.of());
        assertThrows(IllegalArgumentException.class,()->mapping.composeAtoms(Map.of("parent","missing")));
    }
    private static MolecularGraph.Atom atom(String id,String stereo){return new MolecularGraph.Atom(id,"C",null,0,0,false,stereo,null,Map.of());}
}
