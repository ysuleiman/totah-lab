package totah.lab.web.assembly;

import totah.lab.athena.pocket.component.PocketSphere;
import totah.lab.hermes.file.mmcif.BoundComponentOccurrence;
import totah.lab.hermes.file.pocket.FpocketAtomObservation;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

public interface ComponentPocketSourceLoader {
    List<BoundComponentOccurrence> components(Path sourceMmcif, String pdbId,
            String assemblyId) throws IOException;

    List<PocketSource> pockets(Path fpocketOutput) throws IOException;

    record PocketSource(int pocketNumber,
            List<FpocketAtomObservation> atoms, List<PocketSphere> spheres) {
        public PocketSource {
            atoms = List.copyOf(atoms);
            spheres = List.copyOf(spheres);
        }
    }
}
