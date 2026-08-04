package totah.lab.hermes.uniprot;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface UniProtClient {

    /**
     * Fetches one full UniProt entry as JSON. Empty when the accession
     * has no UniProt entry.
     */
    Optional<UniProtEntry> fetch(String accession)
            throws UniProtException, InterruptedException;

    /**
     * Fetches compact annotation projections for many accessions via
     * the UniProt TSV stream endpoint. Accessions without a UniProt
     * entry are absent from the result.
     */
    List<UniProtAnnotation> fetchAnnotations(
            Collection<String> accessions
    ) throws UniProtException, InterruptedException;
}
