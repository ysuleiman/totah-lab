package totah.lab.athena.system.rules;
import totah.lab.athena.system.SystemStateView;
import totah.lab.mnemosyne.*;
import java.util.List;
/** Test-only access to factual status, deliberately distinct from governed scientific admission. */
public final class ResidueContextTestAccess {
 private ResidueContextTestAccess() { }
 public static EvidenceInterpretation.Status factualStatus(SystemStateView state,EvidenceEnvelope binding,List<EvidenceEnvelope> inputs)throws Exception {
  return ResidueContextSource.check(state,binding,EventPayload.index(inputs)).status();
 }
}
