package totah.lab.web.service;

import totah.lab.hermes.uniprot.UniProtAnnotation;
import totah.lab.hermes.uniprot.UniProtCrossReference;
import totah.lab.hermes.uniprot.UniProtEntry;

import java.util.List;
import java.util.Locale;

/**
 * The annotation evidence that the flag derivation rules operate on,
 * adapted uniformly from a full {@link UniProtEntry} (hit list) or a
 * compact {@link UniProtAnnotation} (database background).
 */
record AnnotationFacts(
        String proteinName,
        List<String> keywords,
        List<String> ecNumbers,
        boolean ligandBindingEvidence,
        boolean catalyticEvidence,
        boolean experimentalStructure,
        String annotationText
) {
    static AnnotationFacts from(UniProtEntry entry) {
        return new AnnotationFacts(
                entry.proteinName(),
                entry.keywords(),
                entry.ecNumbers(),
                !entry.bindingLigands().isEmpty()
                        || !entry.cofactors().isEmpty(),
                !entry.activeSites().isEmpty()
                        || !entry.catalyticActivities().isEmpty(),
                !entry.pdbIds().isEmpty(),
                joinLowerCase(
                        entry.bindingLigands(),
                        entry.cofactors(),
                        referenceNames(entry.pfam()),
                        referenceNames(entry.interPro())
                )
        );
    }

    static AnnotationFacts from(UniProtAnnotation annotation) {
        return new AnnotationFacts(
                annotation.proteinName(),
                annotation.keywords(),
                annotation.ecNumbers(),
                isPresent(annotation.bindingSites())
                        || isPresent(annotation.cofactors()),
                isPresent(annotation.activeSites())
                        || isPresent(annotation.catalyticActivity()),
                isPresent(annotation.pdbIds()),
                joinLowerCase(
                        annotation.bindingSites(),
                        annotation.cofactors(),
                        annotation.pfam(),
                        annotation.interPro()
                )
        );
    }

    private static List<String> referenceNames(
            List<UniProtCrossReference> references
    ) {
        return references.stream()
                .map(reference -> reference.name() == null
                        ? reference.id()
                        : reference.id() + " " + reference.name())
                .toList();
    }

    private static boolean isPresent(String value) {
        return value != null && !value.isBlank();
    }

    private static String joinLowerCase(List<String>... groups) {
        StringBuilder text = new StringBuilder();

        for (List<String> group : groups) {
            for (String value : group) {
                if (!text.isEmpty()) {
                    text.append('\n');
                }
                text.append(value);
            }
        }

        return text.toString().toLowerCase(Locale.ROOT);
    }

    private static String joinLowerCase(String... values) {
        StringBuilder text = new StringBuilder();

        for (String value : values) {
            if (value == null) {
                continue;
            }
            if (!text.isEmpty()) {
                text.append('\n');
            }
            text.append(value);
        }

        return text.toString().toLowerCase(Locale.ROOT);
    }
}
