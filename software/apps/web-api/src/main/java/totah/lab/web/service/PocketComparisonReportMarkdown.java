package totah.lab.web.service;

import totah.lab.athena.pocket.evidence.LigandContact;

import java.util.Locale;

/**
 * Human-readable Markdown rendering of the structured pocket
 * comparison report ({@link PocketComparisonReportView}), one section
 * per evidence dimension: Retrieval, Alignment, Residue, Functional,
 * Assessment. Renders the web DTO, never the athena records.
 *
 * <p>A dedicated renderer lives here (rather than in lab-report)
 * because lab-report's {@code PocketMarkdownReportRenderer} targets
 * the unrelated {@code CompletePocketReport} aggregate; mapping the
 * evidence bundle into it would be invasive and semantically wrong.
 * Numbers are rendered as recorded; absent evidence is reported
 * explicitly, never zeroed.</p>
 */
public final class PocketComparisonReportMarkdown {

    private PocketComparisonReportMarkdown() {
    }

    public static String render(PocketComparisonReportView report) {
        StringBuilder out = new StringBuilder();

        out.append("# Pocket comparison evidence: pocket ")
                .append(report.queryPocketId())
                .append(" vs pocket ")
                .append(report.candidatePocketId())
                .append("\n\n");

        retrieval(out, report.retrieval());
        alignment(out, report.alignment());
        residues(out, report.residueComparison());
        functional(out, report);
        assessment(out, report.interpretation());

        return out.toString();
    }

    private static void retrieval(
            StringBuilder out,
            PocketComparisonReportView.RetrievalSection retrieval
    ) {
        out.append("## Retrieval\n\n");
        out.append("- Candidate sources: ")
                .append(retrieval.candidateSources().isEmpty()
                        ? "none (direct pairwise comparison)"
                        : String.join(", ", retrieval.candidateSources()))
                .append("\n");
        out.append("- Chosen reference: ")
                .append(retrieval.chosenReference() ? "yes" : "no")
                .append("\n");
        if (retrieval.globalShapeEvaluated()) {
            out.append("- Global shape: evaluated, rank ")
                    .append(retrieval.globalShapeRank())
                    .append(", distance ")
                    .append(format(retrieval.globalShapeDistance()))
                    .append("\n");
        } else {
            out.append("- Global shape: not evaluated (no search was"
                    + " run for this pairwise comparison)\n");
        }
        if (retrieval.pocketMatchEvaluated()) {
            out.append("- PocketMatch: evaluated, symmetric rank ")
                    .append(retrieval.pocketMatchSymmetricRank())
                    .append(", query-coverage rank ")
                    .append(retrieval.pocketMatchQueryCoverageRank())
                    .append(", symmetric score ")
                    .append(format(retrieval.pocketMatchSymmetricScore()))
                    .append(", query coverage ")
                    .append(format(retrieval.pocketMatchQueryCoverage()))
                    .append(", candidate coverage ")
                    .append(format(
                            retrieval.pocketMatchCandidateCoverage()))
                    .append("\n");
        } else {
            out.append("- PocketMatch: not evaluated (no search was"
                    + " run for this pairwise comparison)\n");
        }
        out.append("\n");
    }

    private static void alignment(
            StringBuilder out,
            PocketComparisonReportView.AlignmentSection alignment
    ) {
        out.append("## Alignment\n\n");
        out.append("- Selected initialization: ")
                .append(alignment.selectedInitialization())
                .append("\n");
        out.append("- Selection reason: ")
                .append(alignment.selectionReason())
                .append("\n");
        out.append("- Sequence-consistent correspondences: ")
                .append(alignment.sequenceConsistentCorrespondenceCount())
                .append(" of ")
                .append(alignment.sequenceSeedPairCount())
                .append(" seed pairs (fraction ")
                .append(format(alignment
                        .sequenceConsistentCorrespondenceFraction()))
                .append(")\n\n");

        hypothesis(out, "PCA+ICP", alignment.pcaIcp());
        hypothesis(out, "Sequence-seeded", alignment.sequenceSeeded());
    }

    private static void hypothesis(
            StringBuilder out,
            String name,
            PocketComparisonReportView.HypothesisView hypothesis
    ) {
        out.append("### ").append(name).append(" hypothesis\n\n");
        if (!hypothesis.available()) {
            out.append("Not computed (no usable sequence seed).\n\n");
            return;
        }
        out.append("- Selected: ")
                .append(hypothesis.accepted() ? "yes" : "no")
                .append("\n");
        out.append("- Geometry similarity: ")
                .append(format(hypothesis.geometrySimilarity()))
                .append("\n");
        out.append("- Coverage (query / candidate): ")
                .append(format(hypothesis.forwardCoverage()))
                .append(" / ")
                .append(format(hypothesis.reverseCoverage()))
                .append("\n");
        out.append("- Mean distances (query→candidate / "
                        + "candidate→query / bidirectional): ")
                .append(format(hypothesis.forwardMeanDistance()))
                .append(" / ")
                .append(format(hypothesis.reverseMeanDistance()))
                .append(" / ")
                .append(format(hypothesis.bidirectionalDistance()))
                .append(" Å\n");
        out.append("- Worst nearest-neighbour distance: ")
                .append(format(hypothesis.maximumNearestNeighborDistance()))
                .append(" Å\n");
        out.append("- Residue correspondences: ")
                .append(hypothesis.residueCorrespondenceCount())
                .append(" (sequence-consistent: ")
                .append(hypothesis.sequenceConsistentPairCount())
                .append(")\n\n");
    }

    private static void residues(
            StringBuilder out,
            PocketComparisonReportView.ResidueComparisonSection residues
    ) {
        out.append("## Residue evidence\n\n");
        out.append("- Residues (query / candidate / matched): ")
                .append(residues.queryResidueCount())
                .append(" / ")
                .append(residues.candidateResidueCount())
                .append(" / ")
                .append(residues.matchedResidueCount())
                .append("\n");
        out.append("- Identical / conservative / chemistry-compatible"
                        + " / incompatible: ")
                .append(residues.identicalCount())
                .append(" / ")
                .append(residues.conservativeSubstitutionCount())
                .append(" / ")
                .append(residues.chemistryCompatibleCount())
                .append(" / ")
                .append(residues.incompatibleReplacementCount())
                .append("\n");
        out.append("- Identity fraction: ")
                .append(format(residues.identityFraction()))
                .append("\n");
        out.append("- Substitution similarity (BLOSUM62): ")
                .append(format(residues.substitutionSimilarity()))
                .append("\n");
        out.append("- Chemistry similarity: ")
                .append(format(residues.chemistrySimilarity()))
                .append("\n");
        out.append("- Residue coverage (query / candidate): ")
                .append(format(residues.queryResidueCoverage()))
                .append(" / ")
                .append(format(residues.candidateResidueCoverage()))
                .append("\n");
        out.append("- Sequence-consistent pairs: ")
                .append(residues.sequenceConsistentPairCount())
                .append(" (fraction ")
                .append(format(residues.sequenceConsistentFraction()))
                .append(")\n\n");

        if (!residues.correspondences().isEmpty()) {
            out.append("| Query | Candidate | Match | Distance (Å) |"
                    + " Chemistry | Substitution |\n");
            out.append("|---|---|---|---|---|---|\n");
            for (PocketComparisonReportView.ResiduePairView pair
                    : residues.correspondences()) {
                out.append("| ")
                        .append(pair.queryResidueName())
                        .append(" ")
                        .append(pair.queryResidueNumber())
                        .append(pair.queryInsertionCode())
                        .append(" (").append(pair.queryChainId())
                        .append(") | ")
                        .append(pair.candidateResidueName())
                        .append(" ")
                        .append(pair.candidateResidueNumber())
                        .append(pair.candidateInsertionCode())
                        .append(" (").append(pair.candidateChainId())
                        .append(") | ")
                        .append(pair.matchType())
                        .append(" | ")
                        .append(format(pair.distanceAngstroms()))
                        .append(" | ")
                        .append(format(pair.chemistryScore()))
                        .append(" | ")
                        .append(format(pair.substitutionScore()))
                        .append(" |\n");
            }
            out.append("\n");
        }
    }

    private static void functional(
            StringBuilder out,
            PocketComparisonReportView report
    ) {
        out.append("## Functional evidence\n\n");

        PocketComparisonReportView.KeyResidueSection keyResidues =
                report.keyResidueComparison();
        out.append("### Key residues\n\n");
        if (keyResidues.configuredKeyResidues().isEmpty()) {
            out.append("No key residues configured for the query"
                    + " target.\n\n");
        } else {
            out.append("- Configured: ")
                    .append(String.join(", ",
                            keyResidues.configuredKeyResidues()))
                    .append("\n");
            out.append("- Present in query pocket: ")
                    .append(keyResidues.totalKeyResidueCount())
                    .append(", matched: ")
                    .append(keyResidues.matchedKeyResidueCount())
                    .append(", identical: ")
                    .append(keyResidues.identicalKeyResidueCount())
                    .append(", chemically compatible: ")
                    .append(keyResidues
                            .chemistryCompatibleKeyResidueCount())
                    .append("\n\n");
        }

        PocketComparisonReportView.LigandContactSection ligand =
                report.ligandContactConservation();
        out.append("### Ligand contacts\n\n");
        if (!"AVAILABLE".equals(ligand.status())) {
            out.append("Not available: no BioHub ligand-contact"
                    + " evidence for this structure pair.\n\n");
            return;
        }
        out.append("- Ligand: ").append(ligand.ligandCcd())
                .append(" (evidence source: ")
                .append(ligand.evidenceSource())
                .append(")\n");
        out.append("- Query contact residues: ")
                .append(ligand.queryContactResidueCount())
                .append(", matched: ")
                .append(ligand.matchedQueryContactResidueCount())
                .append(", coverage: ")
                .append(format(ligand.contactCoverage()))
                .append("\n");
        out.append("- Identical / conservative / compatible /"
                        + " incompatible contacts: ")
                .append(ligand.identicalContactCount())
                .append(" / ")
                .append(ligand.conservativeContactCount())
                .append(" / ")
                .append(ligand.chemistryCompatibleContactCount())
                .append(" / ")
                .append(ligand.incompatibleContactCount())
                .append("\n");
        out.append("- Contact chemistry similarity: ")
                .append(format(ligand.contactChemistrySimilarity()))
                .append("\n\n");

        if (!ligand.contacts().isEmpty()) {
            out.append("| Pocket | Residue | Type | Distance (Å) |\n");
            out.append("|---|---|---|---|\n");
            for (LigandContact contact : ligand.contacts()) {
                out.append("| ")
                        .append(contact.pocketReference())
                        .append(" | ")
                        .append(contact.residue().residueName())
                        .append(" ")
                        .append(contact.residue().residueNumber())
                        .append(" (")
                        .append(contact.residue().chainId())
                        .append(") | ")
                        .append(contact.contactType() == null
                                ? "—"
                                : contact.contactType().name())
                        .append(" | ")
                        .append(format(contact.minimumDistance()))
                        .append(" |\n");
            }
            out.append("\n");
        }
    }

    private static void assessment(
            StringBuilder out,
            PocketComparisonReportView.InterpretationSection assessment
    ) {
        out.append("## Assessment\n\n");
        out.append("**").append(assessment.verdict()).append("**\n\n");
        out.append(assessment.reason()).append("\n");
    }

    private static String format(Double value) {
        return value == null
                ? "—"
                : String.format(Locale.ROOT, "%.3f", value);
    }
}
