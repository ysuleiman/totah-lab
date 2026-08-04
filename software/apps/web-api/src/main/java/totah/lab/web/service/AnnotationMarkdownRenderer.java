package totah.lab.web.service;

import java.util.List;
import java.util.Locale;

/**
 * Renders an {@link AnnotationReport} as Markdown: summary counts,
 * the enrichment table against the database background, and the
 * per-accession annotation table.
 */
public final class AnnotationMarkdownRenderer {

    private AnnotationMarkdownRenderer() {
    }

    public static String render(AnnotationReport report) {
        StringBuilder markdown = new StringBuilder();

        FlagTally hits = report.hitTally();
        FlagTally background = report.backgroundTally();

        markdown.append("# Top Hits Annotation\n\n");
        markdown.append("- Accessions requested: ")
                .append(report.requested()).append('\n');
        markdown.append("- Entries found in UniProt: ")
                .append(report.found()).append('\n');
        markdown.append("- Database background: ")
                .append(background.total())
                .append(" annotated receptors\n\n");

        markdown.append("## Summary statistics\n\n");
        markdown.append("| Category | Hits |\n");
        markdown.append("| --- | --- |\n");
        summaryRow(markdown, "Enzymes", hits.enzymes(), hits.total());
        summaryRow(markdown, "Transferases",
                hits.transferases(), hits.total());
        summaryRow(markdown, "Methyltransferases",
                hits.methyltransferases(), hits.total());
        summaryRow(markdown, "Rossmann-like folds",
                hits.rossmannLikeFolds(), hits.total());
        summaryRow(markdown, "Ligand-binding proteins",
                hits.ligandBindingProteins(), hits.total());
        summaryRow(markdown, "Proteins binding SAM",
                hits.samBinders(), hits.total());
        summaryRow(markdown, "Membrane proteins",
                hits.membraneProteins(), hits.total());
        summaryRow(markdown, "Catalytic residues annotated",
                hits.catalyticResidues(), hits.total());
        summaryRow(markdown, "Experimental structures",
                hits.experimentalStructures(), hits.total());

        markdown.append("\n## Enrichment vs database background\n\n");
        markdown.append("| Category | Hits | Background")
                .append(" | Fold enrichment | Fisher p-value |\n");
        markdown.append("| --- | --- | --- | --- | --- |\n");

        for (AnnotationEnrichment row : report.enrichment()) {
            markdown.append("| ").append(row.category())
                    .append(" | ")
                    .append(row.hitsFlagged())
                    .append('/')
                    .append(row.hitsTotal())
                    .append(" | ")
                    .append(row.backgroundFlagged())
                    .append('/')
                    .append(row.backgroundTotal())
                    .append(" | ")
                    .append(row.foldEnrichment() == null
                            ? "—"
                            : String.format(
                                    Locale.ROOT,
                                    "%.2f",
                                    row.foldEnrichment()
                            ))
                    .append(" | ")
                    .append(String.format(
                            Locale.ROOT,
                            "%.3g",
                            row.pValue()
                    ))
                    .append(" |\n");
        }

        markdown.append(
                "\n*Rossmann-like fold enrichment is not reported:"
                        + " the compact background annotation does not"
                        + " include Pfam/InterPro family names.*\n"
        );

        markdown.append("\n## Annotations\n\n");
        markdown.append("| Accession | Protein | Gene | Organism")
                .append(" | Reviewed | EC | GO molecular function")
                .append(" | Ligand binding | Cofactors | Pfam")
                .append(" | InterPro | PDB | AlphaFold |\n");
        markdown.append("| --- | --- | --- | --- | --- | --- | ---")
                .append(" | --- | --- | --- | --- | --- | --- |\n");

        for (AnnotatedProtein hit : report.hits()) {
            markdown.append("| ").append(hit.accession())
                    .append(" | ").append(cell(hit.proteinName()))
                    .append(" | ").append(cell(hit.geneName()))
                    .append(" | ").append(cell(hit.organism()))
                    .append(" | ").append(hit.found()
                            ? (hit.reviewed() ? "yes" : "no")
                            : "not found")
                    .append(" | ").append(cell(hit.ecNumbers()))
                    .append(" | ").append(cell(hit.goMolecularFunctions()))
                    .append(" | ").append(cell(hit.bindingLigands()))
                    .append(" | ").append(cell(hit.cofactors()))
                    .append(" | ").append(cell(hit.pfam()))
                    .append(" | ").append(cell(hit.interPro()))
                    .append(" | ").append(cell(hit.pdbIds()))
                    .append(" | ").append(cell(hit.alphaFoldIds()))
                    .append(" |\n");
        }

        return markdown.toString();
    }

    private static void summaryRow(
            StringBuilder markdown,
            String category,
            int flagged,
            int total
    ) {
        markdown.append("| ").append(category)
                .append(" | ").append(flagged)
                .append('/').append(total)
                .append(" |\n");
    }

    private static String cell(String value) {
        return value == null ? "" : value.replace("|", "\\|");
    }

    private static String cell(List<String> values) {
        return values.isEmpty()
                ? ""
                : String.join("; ", values).replace("|", "\\|");
    }
}
