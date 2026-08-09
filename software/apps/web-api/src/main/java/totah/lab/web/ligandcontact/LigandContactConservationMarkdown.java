package totah.lab.web.ligandcontact;

import totah.lab.athena.pocket.compare.residue.MatchType;
import totah.lab.web.ligandcontact.LigandContactConservationAnalyzer
        .Aggregate;
import totah.lab.web.ligandcontact.LigandContactConservationAnalyzer
        .LigandContactConservationReport;
import totah.lab.web.ligandcontact.LigandContactConservationAnalyzer
        .Row;
import totah.lab.web.ligandcontact.LigandMoietyConservationAnalyzer
        .LigandMoietyConservationReport;

import java.util.Locale;

/**
 * Markdown rendering of a ligand-contact conservation report.
 */
public final class LigandContactConservationMarkdown {

    private LigandContactConservationMarkdown() {
    }

    public static String render(LigandContactConservationReport report) {
        StringBuilder markdown = new StringBuilder();

        markdown.append("# ").append(report.ligandCcd())
                .append(" contact conservation: ")
                .append(report.queryLabel())
                .append(" vs ")
                .append(report.candidateLabel())
                .append("\n\n");
        markdown.append("Model: ").append(report.model())
                .append(" · direct contact ≤ ")
                .append(String.format(
                        Locale.ROOT, "%.1f",
                        report.directContactCutoff()))
                .append(" Å · alignment identity ")
                .append(String.format(
                        Locale.ROOT, "%.3f",
                        report.alignmentIdentity()))
                .append("\n\n");

        markdown.append("| ").append(report.queryLabel())
                .append(" residue | ")
                .append(report.candidateLabel())
                .append(" residue | ")
                .append(report.queryLabel()).append(" contact | ")
                .append(report.candidateLabel())
                .append(" contact | Distances Å | Atom pairs")
                .append(" | Identity | Aligned |\n");
        markdown.append("|---|---|---|---|---|---|---|---|\n");

        for (Row row : report.rows()) {
            markdown.append("| ")
                    .append(residue(
                            row.queryResidueName(),
                            row.queryResidueNumber()))
                    .append(" | ")
                    .append(residue(
                            row.candidateResidueName(),
                            row.candidateResidueNumber()))
                    .append(" | ")
                    .append(contact(row, true))
                    .append(" | ")
                    .append(contact(row, false))
                    .append(" | ")
                    .append(distances(row))
                    .append(" | ")
                    .append(atomPairs(row))
                    .append(" | ")
                    .append(matchType(row.matchType()))
                    .append(" | ")
                    .append(row.sequenceConsistent() ? "yes" : "gap")
                    .append(" |\n");
        }

        Aggregate aggregate = report.aggregate();
        markdown.append("\n## Aggregate\n\n");
        line(markdown, "Query direct-contact count",
                aggregate.queryContactCount());
        line(markdown, "Matched (aligned) query contacts",
                aggregate.matchedContactCount());
        line(markdown, "Shared-contact count",
                aggregate.sharedContactCount());
        line(markdown, "Identity among shared contacts",
                aggregate.identicalSharedCount());
        line(markdown, "Conservative substitutions among shared",
                aggregate.conservativeSharedCount());
        line(markdown, "Non-conservative substitutions among shared",
                aggregate.nonConservativeSharedCount());
        line(markdown, "Unmatched query contacts (alignment gap)",
                aggregate.unmatchedQueryContactCount());
        line(markdown, "Query contacts aligned to non-contact",
                aggregate.queryContactsAlignedToNonContact());
        line(markdown, "Candidate direct-contact count",
                aggregate.candidateContactCount());
        line(markdown, "Candidate-only contacts (alignment gap)",
                aggregate.candidateOnlyContactCount());
        line(markdown, "Candidate contacts aligned to non-contact",
                aggregate.candidateContactsAlignedToNonContact());
        markdown.append("- Mean distance difference (shared): ")
                .append(String.format(
                        Locale.ROOT, "%.2f",
                        aggregate.meanDistanceDifference()))
                .append(" Å\n");
        markdown.append("- Median distance difference (shared): ")
                .append(String.format(
                        Locale.ROOT, "%.2f",
                        aggregate.medianDistanceDifference()))
                .append(" Å\n");
        markdown.append("- Query contact coverage: ")
                .append(String.format(
                        Locale.ROOT, "%.3f",
                        aggregate.queryContactCoverage()))
                .append('\n');
        markdown.append("- Candidate contact coverage: ")
                .append(String.format(
                        Locale.ROOT, "%.3f",
                        aggregate.candidateContactCoverage()))
                .append('\n');

        return markdown.toString();
    }

    /**
     * Renders the Phase 2 moiety-level section: which part of the
     * ligand each aligned residue faces.
     */
    public static String renderMoietySection(
            LigandMoietyConservationReport report
    ) {
        StringBuilder markdown = new StringBuilder();

        markdown.append("## ").append(report.ligandCcd())
                .append(" moiety conservation (atom level)\n\n");
        markdown.append("| ").append(report.queryLabel())
                .append(" residue | ")
                .append(report.candidateLabel())
                .append(" residue | ")
                .append(report.queryLabel()).append(" faces | ")
                .append(report.candidateLabel())
                .append(" faces | Distances Å | Identity")
                .append(" | Same moiety |\n");
        markdown.append("|---|---|---|---|---|---|---|\n");

        for (LigandMoietyConservationAnalyzer.Row row : report.rows()) {
            markdown.append("| ")
                    .append(residue(
                            row.queryResidueName(),
                            row.queryResidueNumber()))
                    .append(" | ")
                    .append(residue(
                            row.candidateResidueName(),
                            row.candidateResidueNumber()))
                    .append(" | ")
                    .append(moiety(row.queryFacingMoiety(),
                            row.queryDirectContact()))
                    .append(" | ")
                    .append(moiety(row.candidateFacingMoiety(),
                            row.candidateDirectContact()))
                    .append(" | ")
                    .append(distance(row.queryFacingDistance()))
                    .append(" / ")
                    .append(distance(row.candidateFacingDistance()))
                    .append(" | ")
                    .append(matchType(row.matchType()))
                    .append(" | ")
                    .append(!row.sequenceConsistent()
                            ? "gap"
                            : row.sameFacingMoiety() ? "yes" : "no")
                    .append(" |\n");
        }

        LigandMoietyConservationAnalyzer.Aggregate aggregate =
                report.aggregate();
        markdown.append("\n### Moiety aggregate\n\n");
        markdown.append("- Query contacts by moiety: ")
                .append(moietyCounts(aggregate.queryContactsByMoiety()))
                .append('\n');
        markdown.append("- Candidate contacts by moiety: ")
                .append(moietyCounts(
                        aggregate.candidateContactsByMoiety()))
                .append('\n');
        line(markdown, "Shared-contact count",
                aggregate.sharedContactCount());
        line(markdown, "Same-moiety count",
                aggregate.sameMoietyCount());
        line(markdown, "Moiety-switch count",
                aggregate.moietySwitchCount());
        markdown.append("- Mean facing-distance difference: ")
                .append(String.format(
                        Locale.ROOT, "%.2f",
                        aggregate.meanFacingDistanceDifference()))
                .append(" Å\n");
        markdown.append("- Median facing-distance difference: ")
                .append(String.format(
                        Locale.ROOT, "%.2f",
                        aggregate.medianFacingDistanceDifference()))
                .append(" Å\n");

        return markdown.toString();
    }

    private static String moiety(
            SamMoiety moiety,
            boolean directContact
    ) {
        if (!directContact || moiety == null) {
            return "—";
        }
        return moiety.name().toLowerCase(Locale.ROOT);
    }

    private static String moietyCounts(
            java.util.Map<SamMoiety, Integer> counts
    ) {
        if (counts.isEmpty()) {
            return "none";
        }
        StringBuilder text = new StringBuilder();
        for (SamMoiety moiety : SamMoiety.values()) {
            Integer count = counts.get(moiety);
            if (count != null) {
                if (text.length() > 0) {
                    text.append(", ");
                }
                text.append(moiety.name().toLowerCase(Locale.ROOT))
                        .append(' ')
                        .append(count);
            }
        }
        return text.toString();
    }

    private static void line(
            StringBuilder markdown,
            String label,
            int value
    ) {
        markdown.append("- ").append(label).append(": ")
                .append(value).append('\n');
    }

    private static String residue(String name, Integer number) {
        if (number == null) {
            return "—";
        }
        return name + number;
    }

    private static String contact(Row row, boolean querySide) {
        Integer number = querySide
                ? row.queryResidueNumber()
                : row.candidateResidueNumber();
        if (number == null) {
            return "—";
        }
        boolean contact = querySide
                ? row.queryDirectContact()
                : row.candidateDirectContact();
        boolean hasEvidence = querySide
                ? row.queryMinimumDistance() != null
                : row.candidateMinimumDistance() != null;
        if (!hasEvidence) {
            return "outside shell";
        }
        return contact ? "yes" : "no";
    }

    private static String distances(Row row) {
        return distance(row.queryMinimumDistance())
                + " / "
                + distance(row.candidateMinimumDistance());
    }

    private static String distance(Double value) {
        return value == null
                ? "—"
                : String.format(Locale.ROOT, "%.2f", value);
    }

    private static String atomPairs(Row row) {
        return pairs(row.queryAtomPairCount())
                + " / "
                + pairs(row.candidateAtomPairCount());
    }

    private static String pairs(Integer value) {
        return value == null ? "—" : String.valueOf(value);
    }

    private static String matchType(MatchType matchType) {
        if (matchType == null) {
            return "—";
        }
        return switch (matchType) {
            case IDENTICAL -> "identical";
            case CONSERVATIVE -> "conservative";
            case CHEMISTRY_COMPATIBLE -> "chemistry-compatible";
            case DIFFERENT -> "non-conservative";
            case UNMATCHED -> "unmatched";
        };
    }
}
