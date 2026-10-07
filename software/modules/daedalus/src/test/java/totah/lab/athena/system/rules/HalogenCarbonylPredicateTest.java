package totah.lab.athena.system.rules;
import org.junit.jupiter.api.*;
import java.util.stream.*;
import static org.junit.jupiter.api.Assertions.*;
class HalogenCarbonylPredicateTest {
    @TestFactory Stream<DynamicTest> exactInclusiveSourceBounds(){return Stream.of(new double[]{3.5,130,80,1},new double[]{3.5,180,140,1},new double[]{Math.nextUp(3.5),165,120,0},new double[]{3,Math.nextDown(130.0),120,0},new double[]{3,Math.nextUp(180.0),120,0},new double[]{3,165,Math.nextDown(80.0),0},new double[]{3,165,Math.nextUp(140.0),0},new double[]{Double.NaN,165,120,0},new double[]{0,165,120,0}).map(v->DynamicTest.dynamicTest(java.util.Arrays.toString(v),()->assertEquals(v[3]==1,HalogenCarbonylRules.predicate(v[0],v[1],v[2]))));}
}
