package totah.lab.athena.system.rules.research;

import java.io.IOException;
import java.time.Instant;

/** Application trust boundary. Must authenticate context/time; equality of caller-supplied values is insufficient. */
@FunctionalInterface
public interface ResearchTimeAuthority {
    void verifyCurrent(RulePolicyContext context,Instant evaluatedAt)throws IOException;
}
