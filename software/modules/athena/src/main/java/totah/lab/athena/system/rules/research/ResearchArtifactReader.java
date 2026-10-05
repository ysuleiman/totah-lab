package totah.lab.athena.system.rules.research;

import totah.lab.athena.system.rules.RuleManifest;
import java.io.IOException;

/** Resolve only explicit admitted pins in the selected snapshot; never scan or fetch implicitly. */
@FunctionalInterface
public interface ResearchArtifactReader {
    byte[] read(RuleManifest.Source source) throws IOException;
}
