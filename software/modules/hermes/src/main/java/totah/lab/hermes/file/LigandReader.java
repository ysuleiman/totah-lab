package totah.lab.hermes.file;

import totah.lab.gaia.molecule.Ligand;

import java.io.IOException;
import java.nio.file.Path;

public interface LigandReader {

    Ligand read(Path path) throws IOException;
}
