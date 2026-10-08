package totah.lab.athena.system.rules;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;
class RestraintNumericalTest {
 @ParameterizedTest @ValueSource(strings={"target","below","above","negative","positive","negative-zero","zero-esd","negative-esd","nan","infinite","overflow","subnormal"})
 void exactTwoOperations(String name){double t=1.453,s=.010,x=t;switch(name){case "below"->x=Math.nextDown(t);case "above"->x=Math.nextUp(t);case "negative"->x=-1000;case "positive"->x=1000;case "negative-zero"->{x=-0.;t=0.;}case "zero-esd"->s=0;case "negative-esd"->s=-1;case "nan"->x=Double.NaN;case "infinite"->s=Double.POSITIVE_INFINITY;case "overflow"->{x=Double.MAX_VALUE;t=-Double.MAX_VALUE;}case "subnormal"->{x=Double.MIN_VALUE;t=0;s=1;}}var q=RestraintDeviationRules.residual(x,t,s);if(java.util.Set.of("zero-esd","negative-esd","nan","infinite","overflow").contains(name)){assertNull(q);return;}assertNotNull(q);assertEquals(Double.doubleToRawLongBits(x-t),Double.doubleToRawLongBits(q[0]));assertEquals(Double.doubleToRawLongBits((x-t)/s),Double.doubleToRawLongBits(q[1]));}
 @ParameterizedTest @ValueSource(strings={"KNOWN_UNSPECIFIED","EXPLICIT","UNKNOWN"})
 void isotopeDomainAfterIndependentSourceValidation(String value)throws Exception {var atoms=EventPayload.JSON.readTree("[{\"isotopeStatus\":\""+value+"\"}]");assertEquals(value.equals("EXPLICIT")?totah.lab.mnemosyne.EvidenceInterpretation.Status.UNSUPPORTED:value.equals("UNKNOWN")?totah.lab.mnemosyne.EvidenceInterpretation.Status.UNKNOWN_INCONCLUSIVE:totah.lab.mnemosyne.EvidenceInterpretation.Status.SUPPORTED_PRESENT,RestraintDeviationRules.isotopeDomain(atoms));}
}
