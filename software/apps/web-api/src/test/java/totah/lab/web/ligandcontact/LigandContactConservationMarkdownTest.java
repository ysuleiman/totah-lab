package totah.lab.web.ligandcontact;

import org.junit.jupiter.api.Test;
import totah.lab.athena.sequence.AlignedResiduePair;
import totah.lab.athena.sequence.SequenceAlignment;
import totah.lab.hermes.biohub.model.BiohubPocketEvidence;
import totah.lab.hermes.biohub.model.BiohubPocketEvidence.ResidueContact;
import totah.lab.web.ligandcontact.LigandContactConservationAnalyzer
        .LigandContactConservationReport;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LigandContactConservationMarkdownTest {

    private final LigandContactConservationAnalyzer analyzer =
            new LigandContactConservationAnalyzer();

    @Test
    void rendersHeaderRowsAndAggregate() {
        LigandContactConservationReport report = analyzer.analyze(
                "7A",
                "7B",
                evidence(List.of(
                        contact(98, "ASP", 2.54, 64, true),
                        contact(200, "SER", 5.0, 3, false)
                )),
                evidence(List.of(
                        contact(98, "ASP", 2.49, 64, true),
                        contact(210, "LYS", 3.0, 5, true)
                )),
                new SequenceAlignment(
                        0.8,
                        List.of(new AlignedResiduePair(
                                98, 98, "ASP", "ASP"
                        ))
                )
        );

        String markdown = LigandContactConservationMarkdown.render(report);

        assertTrue(markdown.contains(
                "# SAM contact conservation: 7A vs 7B"
        ));
        assertTrue(markdown.contains("Model: esmfold2"));
        assertTrue(markdown.contains("direct contact ≤ 4.5 Å"));
        assertTrue(markdown.contains("alignment identity 0.800"));
        assertTrue(markdown.contains(
                "| 7A residue | 7B residue | 7A contact | 7B contact |"
        ));

        // The aligned pair: both residues, both direct contacts, both
        // distances and atom-pair counts, the identity column.
        assertTrue(markdown.contains(
                "| ASP98 | ASP98 | yes | yes | 2.54 / 2.49 | 64 / 64"
                        + " | identical | yes |"
        ));

        // The shell member is in the shell but not a direct contact.
        assertTrue(markdown.contains(
                "| SER200 | — | no | — | 5.00 / — | 3 / — | — | gap |"
        ));

        // The candidate-only contact renders on the candidate side.
        assertTrue(markdown.contains(
                "| — | LYS210 | — | yes | — / 3.00 | — / 5 | — | gap |"
        ));

        assertTrue(markdown.contains("## Aggregate"));
        assertTrue(markdown.contains("- Query direct-contact count: 1"));
        assertTrue(markdown.contains("- Shared-contact count: 1"));
        assertTrue(markdown.contains(
                "- Candidate direct-contact count: 2"
        ));
        assertTrue(markdown.contains(
                "- Candidate-only contacts (alignment gap): 1"
        ));
        assertTrue(markdown.contains("- Query contact coverage: 1.000"));
        assertTrue(markdown.contains(
                "- Candidate contact coverage: 0.500"
        ));
        assertTrue(markdown.contains(
                "- Mean distance difference (shared): 0.05 Å"
        ));
    }

    @Test
    void rendersOutsideShellWhenASideHasNoEvidence() {
        LigandContactConservationReport report = analyzer.analyze(
                "7A",
                "7B",
                evidence(List.of(contact(10, "ASP", 2.5, 30, true))),
                evidence(List.of()),
                new SequenceAlignment(
                        1.0,
                        List.of(new AlignedResiduePair(
                                10, 10, "ASP", "ASP"
                        ))
                )
        );

        String markdown = LigandContactConservationMarkdown.render(report);

        // The aligned candidate residue has no evidence residue at
        // all: "outside shell", not a fabricated contact or distance.
        assertTrue(markdown.contains("outside shell"));
        assertTrue(markdown.contains(
                "| ASP10 | ASP10 | yes | outside shell | 2.50 / —"
                        + " | 30 / — | identical | yes |"
        ));
        assertFalse(markdown.contains("NaN"));
    }

    private static BiohubPocketEvidence evidence(
            List<ResidueContact> contacts
    ) {
        return new BiohubPocketEvidence(
                "SAM",
                "esmfold2",
                6.0,
                4.5,
                null,
                null,
                contacts
        );
    }

    private static ResidueContact contact(
            int residueNumber,
            String residueName,
            double minimumDistance,
            int atomPairs,
            boolean directContact
    ) {
        return new ResidueContact(
                "A",
                residueNumber,
                residueName,
                minimumDistance,
                atomPairs,
                directContact
        );
    }
}
