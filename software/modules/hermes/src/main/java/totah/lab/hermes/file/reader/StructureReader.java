package totah.lab.hermes.file.reader;

import totah.lab.gaia.structure.Structure;

import java.io.IOException;
import java.nio.file.Path;

public interface StructureReader {

    Structure read(Path path) throws IOException;

    boolean supports(Path path);
}
