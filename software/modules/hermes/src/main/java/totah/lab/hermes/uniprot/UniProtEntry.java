package totah.lab.hermes.uniprot;

import java.util.List;
import java.util.Objects;

public record UniProtEntry(
        String accession,
        String entryName,
        String proteinName,
        String geneName,
        String organism,
        Integer taxonomyId,
        String sequence,
        int sequenceLength,
        String function,
        List<String> keywords,
        List<String> pdbIds,
        List<String> alphaFoldIds,
        List<String> goTerms
) {
    public UniProtEntry {
        Objects.requireNonNull(accession, "accession");
        keywords = keywords == null ? List.of() : List.copyOf(keywords);
        pdbIds = pdbIds == null ? List.of() : List.copyOf(pdbIds);
        alphaFoldIds = alphaFoldIds == null ? List.of() : List.copyOf(alphaFoldIds);
        goTerms = goTerms == null ? List.of() : List.copyOf(goTerms);
    }
}
