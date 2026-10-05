package totah.lab.athena.system.rules;
import org.junit.jupiter.api.Test;
import totah.lab.athena.design.backend.MolecularGraph;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class ChargeGroupSumTest {
    private MolecularGraph graph(int a,int b) {
        return new MolecularGraph(List.of(new MolecularGraph.Atom("a","N",0,a,0,false,"",null,Map.of()),
                new MolecularGraph.Atom("b","N",0,b,0,false,"",null,Map.of())),List.of(),Map.of());
    }
    @Test void distinctMembersCountExactlyOnce(){assertEquals(1,ChargeGroupRules.total(graph(1,0),List.of("a","a","b")));}
    @Test void overflowFails(){assertThrows(ArithmeticException.class,()->ChargeGroupRules.total(graph(Integer.MAX_VALUE,1),List.of("a","b")));}
    @Test void underflowFails(){assertThrows(ArithmeticException.class,()->ChargeGroupRules.total(graph(Integer.MIN_VALUE,-1),List.of("a","b")));}
    @Test void missingSourceMemberFails(){assertThrows(NoSuchElementException.class,()->ChargeGroupRules.total(graph(0,0),List.of("missing")));}
}
