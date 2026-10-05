import java.nio.file.*;
import totah.lab.athena.system.rules.*;
import totah.lab.athena.system.SystemGraphCertificate;
public class ManifestCheck {
 public static void main(String[] args)throws Exception {
  var r=RuleRegistry.load(Path.of("software/modules/athena/src/main/resources/totah/lab/athena/system/rules/geometry-foundation-v1"));
  if(r.manifests().size()!=1)throw new AssertionError("exactly one candidate manifest");
  var m=r.manifests().values().iterator().next();
  if(m.qualification()!=SystemGraphCertificate.Status.NOT_EVALUATED)throw new AssertionError("must not self-certify");
  System.out.println(m.key()+" "+RuleRegistry.digest(m)+" "+m.qualification());
 }
}
