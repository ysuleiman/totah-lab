package totah.lab.web.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnnotationFlagsTest {

    @Test
    void enzymeRequiresAnEcNumber() {
        assertTrue(derive(facts(null, List.of(), List.of("1.1.1.1"), ""))
                .enzyme());
        assertFalse(derive(facts(null, List.of(), List.of(), ""))
                .enzyme());
    }

    @Test
    void transferaseFromEcClassOrKeyword() {
        assertTrue(derive(facts(null, List.of(), List.of("2.7.11.1"), ""))
                .transferase());
        assertTrue(derive(facts(null, List.of("Transferase"), List.of(), ""))
                .transferase());
        assertFalse(derive(facts(null, List.of(), List.of("1.1.1.1"), ""))
                .transferase());
    }

    @Test
    void methyltransferaseFromEcNameOrKeyword() {
        assertTrue(derive(facts(null, List.of(), List.of("2.1.1.43"), ""))
                .methyltransferase());
        assertTrue(derive(facts(
                "Protein-lysine methyltransferase",
                List.of(),
                List.of(),
                ""
        )).methyltransferase());
        assertTrue(derive(facts(null, List.of("Methyltransferase"), List.of(), ""))
                .methyltransferase());
        assertFalse(derive(facts(null, List.of(), List.of("2.7.11.1"), ""))
                .methyltransferase());
    }

    @Test
    void membraneProteinFromKeywords() {
        assertTrue(derive(facts(null, List.of("Cell membrane"), List.of(), ""))
                .membraneProtein());
        assertTrue(derive(facts(null, List.of("Transmembrane"), List.of(), ""))
                .membraneProtein());
        assertFalse(derive(facts(null, List.of("Cytoplasm"), List.of(), ""))
                .membraneProtein());
    }

    @Test
    void samBindingMatchesLigandNamesNotSubstrings() {
        assertTrue(derive(facts(null, List.of(), List.of(),
                "binds s-adenosyl-l-methionine")).bindsSam());
        assertTrue(derive(facts(null, List.of(), List.of(),
                "sam-dependent methyltransferase")).bindsSam());
        assertFalse(derive(facts(null, List.of(), List.of(),
                "sample annotation text")).bindsSam());
    }

    @Test
    void rossmannFoldMatchesFamilyNames() {
        assertTrue(derive(facts(null, List.of(), List.of(),
                "ipr029063 rossmann-like fold")).rossmannLikeFold());
        assertFalse(derive(facts(null, List.of(), List.of(),
                "pf08241 methyltransf_12")).rossmannLikeFold());
    }

    @Test
    void evidenceFlagsPassThrough() {
        AnnotationFlags flags = derive(new AnnotationFacts(
                null,
                List.of(),
                List.of(),
                true,
                true,
                true,
                ""
        ));

        assertTrue(flags.ligandBindingProtein());
        assertTrue(flags.catalyticResidues());
        assertTrue(flags.experimentalStructure());
    }

    private static AnnotationFlags derive(AnnotationFacts facts) {
        return AnnotationFlags.derive(facts);
    }

    private static AnnotationFacts facts(
            String proteinName,
            List<String> keywords,
            List<String> ecNumbers,
            String annotationText
    ) {
        return new AnnotationFacts(
                proteinName,
                keywords,
                ecNumbers,
                false,
                false,
                false,
                annotationText
        );
    }
}
