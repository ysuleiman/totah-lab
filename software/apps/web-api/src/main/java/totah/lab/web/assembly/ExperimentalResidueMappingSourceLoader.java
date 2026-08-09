package totah.lab.web.assembly;

import totah.lab.hermes.file.mmcif.PolymerResidueMapping;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/** Source boundary for entry/assembly mmCIF residue correspondence. */
public interface ExperimentalResidueMappingSourceLoader {
    List<PolymerResidueMapping> load(Path entryMmcif, Path assemblyMmcif)
            throws IOException;
}
