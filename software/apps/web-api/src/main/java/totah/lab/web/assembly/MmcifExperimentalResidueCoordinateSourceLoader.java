package totah.lab.web.assembly;

import org.springframework.stereotype.Component;
import totah.lab.hermes.file.mmcif.ResidueCoordinateObservation;
import totah.lab.hermes.file.mmcif.reader.MmcifResidueCoordinateReader;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

@Component
public final class MmcifExperimentalResidueCoordinateSourceLoader
        implements ExperimentalResidueCoordinateSourceLoader {
    private final MmcifResidueCoordinateReader reader =
            new MmcifResidueCoordinateReader();

    @Override
    public List<ResidueCoordinateObservation> load(Path assemblyMmcif)
            throws IOException {
        return reader.read(assemblyMmcif);
    }
}
