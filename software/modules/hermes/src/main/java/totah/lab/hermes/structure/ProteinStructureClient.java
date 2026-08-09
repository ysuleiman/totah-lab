package totah.lab.hermes.structure;

import totah.lab.gaia.structure.Structure;

import java.io.IOException;
import java.util.Optional;

public interface ProteinStructureClient {

    Optional<Structure> fetch(String accession)
            throws IOException, InterruptedException;
}
