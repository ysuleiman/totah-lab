package totah.lab.web.service;

import org.junit.jupiter.api.Test;
import totah.lab.web.service.PocketComparisonReportView.AlignmentSection;
import totah.lab.web.service.PocketComparisonReportView.HypothesisView;
import totah.lab.web.service.PocketComparisonReportView.InterpretationSection;
import totah.lab.web.service.PocketComparisonReportView.KeyResidueSection;
import totah.lab.web.service.PocketComparisonReportView.LigandContactSection;
import totah.lab.web.service.PocketComparisonReportView.ResidueComparisonSection;
import totah.lab.web.service.PocketComparisonReportView.RetrievalSection;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The Markdown evidence report renders every evidence dimension as
 * its own section, with absent ligand evidence reported explicitly.
 */
class PocketComparisonReportMarkdownTest {

    @Test
    void rendersAllSections() {
        String markdown = PocketComparisonReportMarkdown.render(
                report(true)
        );

        assertThat(markdown).contains("## Retrieval");
        assertThat(markdown).contains("## Alignment");
        assertThat(markdown).contains("## Residue evidence");
        assertThat(markdown).contains("## Functional evidence");
        assertThat(markdown).contains("## Assessment");
        assertThat(markdown).contains("PCA+ICP hypothesis");
        assertThat(markdown).contains("Sequence-seeded hypothesis");
        assertThat(markdown).contains("Not computed (no usable"
                + " sequence seed)");
        assertThat(markdown).contains("STRONG_FUNCTIONAL_MATCH");
        assertThat(markdown).contains("- Ligand: SAM");
        assertThat(markdown).contains("GLOBAL_SHAPE");
        assertThat(markdown).contains("rank 3");
    }

    @Test
    void rendersAbsentLigandEvidenceExplicitly() {
        String markdown = PocketComparisonReportMarkdown.render(
                report(false)
        );

        assertThat(markdown).contains("Not available: no BioHub"
                + " ligand-contact evidence");
        assertThat(markdown).doesNotContain("- Ligand:");
    }

    private static PocketComparisonReportView report(boolean ligand) {
        return new PocketComparisonReportView(
                42L,
                7L,
                new RetrievalSection(
                        false,
                        List.of("GLOBAL_SHAPE"),
                        true,
                        3,
                        0.42,
                        false,
                        null,
                        null,
                        null,
                        null,
                        null
                ),
                new AlignmentSection(
                        "PCA_ICP",
                        "PCA_ICP selected: no usable sequence seed",
                        0,
                        0,
                        0.0,
                        false,
                        false,
                        new HypothesisView(
                                true,
                                true,
                                0.9,
                                0.95,
                                0.93,
                                0.4,
                                0.5,
                                0.45,
                                1.2,
                                0,
                                8
                        ),
                        unavailableHypothesis()
                ),
                new ResidueComparisonSection(
                        8,
                        8,
                        8,
                        0,
                        0,
                        8,
                        0,
                        0,
                        0,
                        1.0,
                        1.0,
                        1.0,
                        1.0,
                        0.0,
                        1.0,
                        1.0,
                        0,
                        0.0,
                        List.of()
                ),
                new ChemistryAssessmentView(
                        1.0,
                        1.0,
                        1.0,
                        0.0,
                        8,
                        0,
                        0,
                        0,
                        8,
                        8,
                        8,
                        1.0,
                        0,
                        "STRONG_SIMILARITY",
                        0.99,
                        1.0,
                        1.0
                ),
                new KeyResidueSection(
                        List.of("LEU30"),
                        1,
                        1,
                        1,
                        1
                ),
                ligand
                        ? new LigandContactSection(
                                "AVAILABLE",
                                "SAM",
                                "BIOHUB",
                                2,
                                2,
                                2,
                                0,
                                0,
                                0,
                                0,
                                2,
                                1.0,
                                1.0,
                                1.0,
                                1.0,
                                List.of()
                        )
                        : LigandContactSection.notAvailable(),
                new InterpretationSection(
                        "STRONG_FUNCTIONAL_MATCH",
                        "All evidence dimensions agree"
                )
        );
    }

    private static HypothesisView unavailableHypothesis() {
        return new HypothesisView(
                false, false, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0, 0
        );
    }
}
