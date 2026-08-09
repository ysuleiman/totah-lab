package totah.lab.web.assembly;

import totah.lab.hermes.file.mmcif.UniProtSequenceReference;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

public interface ExperimentalTargetSequenceSourceLoader {
    List<UniProtSequenceReference> load(Path entryMmcif) throws IOException;
}
