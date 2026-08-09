package totah.lab.web.assembly;

import totah.lab.hermes.file.mmcif.ResidueCoordinateObservation;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

public interface ExperimentalResidueCoordinateSourceLoader {
    List<ResidueCoordinateObservation> load(Path assemblyMmcif)
            throws IOException;
}
