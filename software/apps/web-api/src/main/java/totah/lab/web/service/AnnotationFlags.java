package totah.lab.web.service;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Boolean annotation flags derived from {@link AnnotationFacts}.
 * Rules are deliberately simple and documented per flag.
 */
public record AnnotationFlags(
        boolean enzyme,
        boolean transferase,
        boolean methyltransferase,
        boolean membraneProtein,
        boolean ligandBindingProtein,
        boolean catalyticResidues,
        boolean experimentalStructure,
        boolean rossmannLikeFold,
        boolean bindsSam
) {
    private static final Pattern SAM_WORD =
            Pattern.compile("\\bsam\\b");

    static AnnotationFlags derive(AnnotationFacts facts) {
        return new AnnotationFlags(
                isEnzyme(facts),
                isTransferase(facts),
                isMethyltransferase(facts),
                isMembraneProtein(facts),
                facts.ligandBindingEvidence(),
                facts.catalyticEvidence(),
                facts.experimentalStructure(),
                facts.annotationText().contains("rossmann"),
                facts.annotationText().contains("s-adenosyl")
                        || SAM_WORD.matcher(facts.annotationText()).find()
        );
    }

    // Any EC number present.
    private static boolean isEnzyme(AnnotationFacts facts) {
        return !facts.ecNumbers().isEmpty();
    }

    // EC class 2 (transferases) or an explicit Transferase keyword.
    private static boolean isTransferase(AnnotationFacts facts) {
        return facts.ecNumbers().stream()
                .anyMatch(ec -> ec.startsWith("2."))
                || hasKeyword(facts, "transferase");
    }

    // EC 2.1.1.- (methyltransferases) or the name/keyword says so.
    private static boolean isMethyltransferase(AnnotationFacts facts) {
        return facts.ecNumbers().stream()
                .anyMatch(ec -> ec.startsWith("2.1.1."))
                || containsIgnoreCase(facts.proteinName(), "methyltransferase")
                || hasKeyword(facts, "methyltransferase");
    }

    private static boolean isMembraneProtein(AnnotationFacts facts) {
        return facts.keywords().stream()
                .map(keyword -> keyword.toLowerCase(Locale.ROOT))
                .anyMatch(keyword -> keyword.contains("membrane"));
    }

    private static boolean hasKeyword(
            AnnotationFacts facts,
            String keyword
    ) {
        return facts.keywords().stream()
                .anyMatch(value -> value.equalsIgnoreCase(keyword));
    }

    private static boolean containsIgnoreCase(
            String value,
            String substring
    ) {
        return value != null
                && value.toLowerCase(Locale.ROOT)
                        .contains(substring.toLowerCase(Locale.ROOT));
    }
}
