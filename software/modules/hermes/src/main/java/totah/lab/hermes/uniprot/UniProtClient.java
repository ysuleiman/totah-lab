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

    /**
     * Searches UniProt with a query in the UniProt query syntax and
     * returns the same compact annotation projection as
     * {@link #fetchAnnotations(Collection)} for every matching entry.
     * Example: {@code "protein_name:methyltransferase AND
     * organism_id:9606 AND reviewed:true"} lists reviewed human
     * methyltransferases; {@code ec:2.1.1.*} matches the
     * methyltransferase EC class. The query is passed through
     * unvalidated; UniProt rejects malformed queries with an HTTP 400
     * surfaced as {@link UniProtException}.
     */
    List<UniProtAnnotation> search(String query)
            throws UniProtException, InterruptedException;
}
