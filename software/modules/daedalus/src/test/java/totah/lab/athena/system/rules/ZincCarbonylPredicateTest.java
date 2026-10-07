package totah.lab.athena.system.rules;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class ZincCarbonylPredicateTest {
 @Test void exactInclusiveBoundary(){assertTrue(ZincCarbonylRules.predicate(2.8));assertTrue(ZincCarbonylRules.predicate(Math.nextDown(2.8)));assertFalse(ZincCarbonylRules.predicate(Math.nextUp(2.8)));}
 @Test void nonFiniteAndDegenerateAreNeverPositive(){for(double d:new double[]{0,-1,Double.NaN,Double.POSITIVE_INFINITY,Double.NEGATIVE_INFINITY})assertFalse(ZincCarbonylRules.predicate(d));}
}
