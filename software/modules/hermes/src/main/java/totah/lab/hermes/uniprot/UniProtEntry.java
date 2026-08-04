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
        List<String> goTerms,
        boolean reviewed,
        List<String> ecNumbers,
        List<String> goMolecularFunctions,
        List<String> catalyticActivities,
        List<String> bindingLigands,
        List<String> activeSites,
        List<String> cofactors,
        List<UniProtCrossReference> pfam,
        List<UniProtCrossReference> interPro
) {
    public UniProtEntry {
        Objects.requireNonNull(accession, "accession");
        keywords = keywords == null ? List.of() : List.copyOf(keywords);
        pdbIds = pdbIds == null ? List.of() : List.copyOf(pdbIds);
        alphaFoldIds = alphaFoldIds == null ? List.of() : List.copyOf(alphaFoldIds);
        goTerms = goTerms == null ? List.of() : List.copyOf(goTerms);
        ecNumbers = ecNumbers == null ? List.of() : List.copyOf(ecNumbers);
        goMolecularFunctions = goMolecularFunctions == null
                ? List.of()
                : List.copyOf(goMolecularFunctions);
        catalyticActivities = catalyticActivities == null
                ? List.of()
                : List.copyOf(catalyticActivities);
        bindingLigands = bindingLigands == null
                ? List.of()
                : List.copyOf(bindingLigands);
        activeSites = activeSites == null
                ? List.of()
                : List.copyOf(activeSites);
        cofactors = cofactors == null ? List.of() : List.copyOf(cofactors);
        pfam = pfam == null ? List.of() : List.copyOf(pfam);
        interPro = interPro == null ? List.of() : List.copyOf(interPro);
    }
}
