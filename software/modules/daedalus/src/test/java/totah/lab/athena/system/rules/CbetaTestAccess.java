package totah.lab.athena.system.rules;
import java.util.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
/** Test-only access to V11's private numerical contract; not a production API. */
public final class CbetaTestAccess {
 private CbetaTestAccess(){}
 static CbetaReferenceGeometry.V v(double[] p){return new CbetaReferenceGeometry.V(p[0],p[1],p[2]);}
 public static ObjectNode calculate(String id,double[][] p){return EventPayload.JSON.valueToTree(CbetaReferenceGeometry.calculate(id,v(p[0]),v(p[1]),v(p[2]),v(p[3])));}
 public static double[] ideal(String id){var r=CbetaReferenceGeometry.calculate(id,v(new double[]{0,0,0}),v(new double[]{1,0,0}),v(new double[]{1,1,0}),v(new double[]{1,0,1}));return new double[]{r.ideal().x(),r.ideal().y(),r.ideal().z()};}
 public static String category(double d){return CbetaReferenceGeometry.category(d);}
 public static boolean normalizable(double d){return CbetaReferenceGeometry.normalizable(d);}
 public static String zeroMean(){return CbetaReferenceGeometry.finish(v(new double[]{1,0,0}),v(new double[]{-1,0,0}),v(new double[]{0,0,0}),v(new double[]{1,1,1}),1.53).degeneracy();}
 public static totah.lab.mnemosyne.EvidenceInterpretation.Status domain(totah.lab.athena.system.SystemStateView s,totah.lab.mnemosyne.EvidenceEnvelope b,List<totah.lab.mnemosyne.EvidenceEnvelope> inputs)throws Exception{var c=ResidueContextSource.check(s,b,EventPayload.index(inputs));return c.status()==totah.lab.mnemosyne.EvidenceInterpretation.Status.SUPPORTED_PRESENT?ResidueValidationRules.rotamerDomain(c,c.identity("central")):c.status();}
}
