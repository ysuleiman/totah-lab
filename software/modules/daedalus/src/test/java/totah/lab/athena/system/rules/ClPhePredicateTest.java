package totah.lab.athena.system.rules;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class ClPhePredicateTest {
    @Test void strictDistanceBoundary(){assertTrue(ClPheCandidateRules.predicate(Math.nextDown(4.5),90));assertFalse(ClPheCandidateRules.predicate(4.5,90));assertFalse(ClPheCandidateRules.predicate(Math.nextUp(4.5),90));}
    @Test void strictThetaBoundary(){assertTrue(ClPheCandidateRules.predicate(4,Math.nextDown(140.0)));assertFalse(ClPheCandidateRules.predicate(4,140));assertFalse(ClPheCandidateRules.predicate(4,Math.nextUp(140.0)));}
    @Test void characterizationHasItsOwnInclusiveBoundary(){assertEquals("FACE_ON",ClPheCandidateRules.characterization(Math.nextDown(.3)));assertEquals("FACE_ON",ClPheCandidateRules.characterization(.3));assertEquals("EDGE_ON",ClPheCandidateRules.characterization(Math.nextUp(.3)));}
    @Test void bothIndependentBoundsRequired(){assertFalse(ClPheCandidateRules.predicate(5,0));assertFalse(ClPheCandidateRules.predicate(1,150));assertTrue(ClPheCandidateRules.predicate(4,100));}
    @Test void invalidQuantitiesCannotQualify(){for(double d:new double[]{0,-1,Double.NaN,Double.POSITIVE_INFINITY})assertFalse(ClPheCandidateRules.predicate(d,90));for(double a:new double[]{-1,Double.NaN,Double.POSITIVE_INFINITY,180})assertFalse(ClPheCandidateRules.predicate(3,a));}
}
