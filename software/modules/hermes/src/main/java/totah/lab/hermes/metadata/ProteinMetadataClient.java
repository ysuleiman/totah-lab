package totah.lab.hermes.metadata;

import java.io.IOException;
import java.util.Optional;

public interface ProteinMetadataClient {

    Optional<ProteinMetadata> fetch(String accession)
            throws IOException, InterruptedException;
}
