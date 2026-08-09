package totah.lab.web.assembly;

import org.springframework.stereotype.Component;
import totah.lab.hermes.file.mmcif.PolymerResidueMapping;
import totah.lab.hermes.file.mmcif.reader.MmcifPolymerResidueMappingReader;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/** Hermes-backed implementation using only authoritative local mmCIF files. */
@Component
public final class MmcifExperimentalResidueMappingSourceLoader
        implements ExperimentalResidueMappingSourceLoader {
    private final MmcifPolymerResidueMappingReader reader =
            new MmcifPolymerResidueMappingReader();

    @Override
    public List<PolymerResidueMapping> load(Path entryMmcif,
            Path assemblyMmcif) throws IOException {
        return reader.read(entryMmcif, assemblyMmcif);
    }
}
