package totah.lab.web.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class AnnotationRendererTest {

    private static final AnnotationReport REPORT = new AnnotationReport(
            List.of(
                    new AnnotatedProtein(
                            "P11111",
                            true,
                            "Protein-lysine methyltransferase",
                            "METTL7A",
                            "Homo sapiens",
                            true,
                            List.of("2.1.1.43"),
                            List.of("methyltransferase activity"),
                            List.of("A + B = C, with commas"),
                            List.of("S-adenosyl-L-methionine"),
                            List.of(),
                            List.of("PF08241 Methyltransf_12"),
                            List.of("IPR029063 Rossmann-like fold"),
                            List.of("8ABC"),
                            List.of("AF-P11111-F1"),
                            new AnnotationFlags(
                                    true, true, true, false, true,
                                    true, true, true, true
                            )
                    ),
                    AnnotatedProtein.notFound("P99999")
            ),
            2,
            1,
            new FlagTally(2, 1, 1, 1, 0, 1, 1, 1, 1, 1),
            new FlagTally(4, 1, 1, 0, 1, 1, 0, 0, 0, 1),
            List.of(new AnnotationEnrichment(
                    "Enzymes",
                    1,
                    2,
                    1,
                    4,
                    2.0,
                    0.75
            ))
    );

    @Test
    void csvContainsHeaderRowsAndEscaping() {
        String csv = AnnotationCsvRenderer.render(REPORT);

        String[] lines = csv.split("\n");

        assertTrue(lines[0].startsWith(
                "accession,found,protein_name,gene,organism,reviewed"
        ));
        assertTrue(lines[0].contains("is_methyltransferase"));
        assertTrue(lines[0].contains("rossmann_like_fold"));
        assertTrue(lines[0].contains("binds_sam"));

        assertTrue(lines[1].startsWith("P11111,true,"));
        // The catalytic activity contains a comma and must be quoted.
        assertTrue(lines[1].contains("\"A + B = C, with commas\""));
        assertTrue(lines[1].contains("2.1.1.43"));
        assertTrue(lines[1].endsWith(
                "true,true,true,false,true,true,true,true,true"
        ));

        assertTrue(lines[2].startsWith("P99999,false,"));
    }

    @Test
    void markdownContainsSummaryEnrichmentAndTable() {
        String markdown = AnnotationMarkdownRenderer.render(REPORT);

        assertTrue(markdown.contains("# Top Hits Annotation"));
        assertTrue(markdown.contains("- Accessions requested: 2"));
        assertTrue(markdown.contains("| Enzymes | 1/2 |"));
        assertTrue(markdown.contains("| Methyltransferases | 1/2 |"));
        assertTrue(markdown.contains("| Rossmann-like folds | 1/2 |"));
        assertTrue(markdown.contains("| Proteins binding SAM | 1/2 |"));
        assertTrue(markdown.contains(
                "| Enzymes | 1/2 | 1/4 | 2.00 | 0.750 |"
        ));
        assertTrue(markdown.contains("| P11111 |"));
        assertTrue(markdown.contains("| P99999 |"));
        assertTrue(markdown.contains("not found"));
        assertTrue(markdown.contains(
                "Rossmann-like fold enrichment is not reported"
        ));
    }
}
