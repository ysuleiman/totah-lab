package totah.lab.hermes.http;

import totah.lab.hermes.metadata.ProteinMetadata;

import java.io.IOException;
import java.util.Optional;

public interface ProteinMetadataClient {

    Optional<ProteinMetadata> fetch(String accession)
            throws IOException, InterruptedException;
}
