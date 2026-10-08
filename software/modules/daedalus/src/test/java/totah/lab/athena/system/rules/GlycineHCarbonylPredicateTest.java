package totah.lab.athena.system.rules;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class GlycineHCarbonylPredicateTest {
 @Test void exactStrictDistanceBoundaries(){
  assertTrue(GlycineHCarbonylRules.predicate(Math.nextDown(3.5),130));assertFalse(GlycineHCarbonylRules.predicate(3.5,130));assertFalse(GlycineHCarbonylRules.predicate(Math.nextUp(3.5),130));
  assertTrue(GlycineHCarbonylRules.predicate(Math.nextDown(3.0),100));assertFalse(GlycineHCarbonylRules.predicate(3.0,100));assertFalse(GlycineHCarbonylRules.predicate(Math.nextUp(3.0),100));
 }
 @Test void exactStrictAngularBoundaries(){
  assertFalse(GlycineHCarbonylRules.predicate(3.2,Math.nextDown(120.0)));assertFalse(GlycineHCarbonylRules.predicate(3.2,120));assertTrue(GlycineHCarbonylRules.predicate(3.2,Math.nextUp(120.0)));
  assertFalse(GlycineHCarbonylRules.predicate(2.8,Math.nextDown(90.0)));assertFalse(GlycineHCarbonylRules.predicate(2.8,90));assertTrue(GlycineHCarbonylRules.predicate(2.8,Math.nextUp(90.0)));assertTrue(GlycineHCarbonylRules.predicate(2.8,120));
 }
 @Test void invalidNumbersDoNotQualify(){for(double x:new double[]{0,-1,Double.NaN,Double.POSITIVE_INFINITY})assertFalse(GlycineHCarbonylRules.predicate(x,130));assertFalse(GlycineHCarbonylRules.predicate(2,Double.NaN));}
}
