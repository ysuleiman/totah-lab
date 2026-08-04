package totah.lab.web.service;

import java.util.List;

/**
 * One annotated hit-list row: the UniProt metadata requested for the
 * report plus the derived flags. {@code found} is false when the
 * accession has no UniProt entry; all metadata is then empty.
 */
public record AnnotatedProtein(
        String accession,
        boolean found,
        String proteinName,
        String geneName,
        String organism,
        boolean reviewed,
        List<String> ecNumbers,
        List<String> goMolecularFunctions,
        List<String> catalyticActivities,
        List<String> bindingLigands,
        List<String> cofactors,
        List<String> pfam,
        List<String> interPro,
        List<String> pdbIds,
        List<String> alphaFoldIds,
        AnnotationFlags flags
) {
    static AnnotatedProtein notFound(String accession) {
        return new AnnotatedProtein(
                accession,
                false,
                null,
                null,
                null,
                false,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                new AnnotationFlags(
                        false, false, false, false, false,
                        false, false, false, false
                )
        );
    }
}
