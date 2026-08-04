package totah.lab.hermes.uniprot;

import java.util.List;
import java.util.Objects;

/**
 * Compact annotation projection of a UniProt entry, retrieved in bulk
 * via the UniProt TSV stream endpoint. This is deliberately not a full
 * {@link UniProtEntry}: the stream request does not retrieve sequence,
 * taxonomy, organism, GO terms, or AlphaFold identifiers. Free-text
 * annotation columns (catalytic activity, binding sites, cofactors,
 * Pfam, InterPro, PDB) are kept as the raw cell text; blank cells
 * become null.
 */
public record UniProtAnnotation(
        String accession,
        boolean reviewed,
        String proteinName,
        List<String> ecNumbers,
        List<String> keywords,
        String catalyticActivity,
        String activeSites,
        String bindingSites,
        String cofactors,
        String pfam,
        String interPro,
        String pdbIds
) {
    public UniProtAnnotation {
        Objects.requireNonNull(accession, "accession");
        ecNumbers = ecNumbers == null ? List.of() : List.copyOf(ecNumbers);
        keywords = keywords == null ? List.of() : List.copyOf(keywords);
    }
}
