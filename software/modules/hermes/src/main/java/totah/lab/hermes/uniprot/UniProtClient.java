package totah.lab.hermes.uniprot;

import java.util.Optional;

/** Retrieves protein metadata from UniProt. */
public interface UniProtClient {

    Optional<UniProtEntry> fetch(String accession)
            throws UniProtException, InterruptedException;
}
