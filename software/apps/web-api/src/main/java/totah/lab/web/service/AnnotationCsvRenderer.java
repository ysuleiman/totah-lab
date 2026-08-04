package totah.lab.web.service;

import java.util.List;

/**
 * Renders an {@link AnnotationReport} as CSV: one row per requested
 * accession with all fetched metadata and derived flags.
 */
public final class AnnotationCsvRenderer {

    private static final String HEADER =
            "accession,found,protein_name,gene,organism,reviewed,"
                    + "ec_numbers,go_molecular_function,"
                    + "catalytic_activity,ligand_binding,cofactors,"
                    + "pfam,interpro,pdb_ids,alphafold_ids,"
                    + "is_enzyme,is_transferase,is_methyltransferase,"
                    + "is_membrane_protein,is_ligand_binding_protein,"
                    + "has_catalytic_residues,"
                    + "has_experimental_structure,"
                    + "rossmann_like_fold,binds_sam";

    private AnnotationCsvRenderer() {
    }

    public static String render(AnnotationReport report) {
        StringBuilder csv = new StringBuilder(HEADER).append('\n');

        for (AnnotatedProtein hit : report.hits()) {
            AnnotationFlags flags = hit.flags();

            csv.append(row(
                    hit.accession(),
                    Boolean.toString(hit.found()),
                    hit.proteinName(),
                    hit.geneName(),
                    hit.organism(),
                    Boolean.toString(hit.reviewed()),
                    join(hit.ecNumbers()),
                    join(hit.goMolecularFunctions()),
                    join(hit.catalyticActivities()),
                    join(hit.bindingLigands()),
                    join(hit.cofactors()),
                    join(hit.pfam()),
                    join(hit.interPro()),
                    join(hit.pdbIds()),
                    join(hit.alphaFoldIds()),
                    Boolean.toString(flags.enzyme()),
                    Boolean.toString(flags.transferase()),
                    Boolean.toString(flags.methyltransferase()),
                    Boolean.toString(flags.membraneProtein()),
                    Boolean.toString(flags.ligandBindingProtein()),
                    Boolean.toString(flags.catalyticResidues()),
                    Boolean.toString(flags.experimentalStructure()),
                    Boolean.toString(flags.rossmannLikeFold()),
                    Boolean.toString(flags.bindsSam())
            ));
        }

        return csv.toString();
    }

    private static String row(String... cells) {
        StringBuilder row = new StringBuilder();

        for (int index = 0; index < cells.length; index++) {
            if (index > 0) {
                row.append(',');
            }
            row.append(cell(cells[index]));
        }

        return row.append('\n').toString();
    }

    private static String cell(String value) {
        if (value == null) {
            return "";
        }

        if (value.contains(",")
                || value.contains("\"")
                || value.contains("\n")) {
            return '"' + value.replace("\"", "\"\"") + '"';
        }

        return value;
    }

    private static String join(List<String> values) {
        return values.isEmpty() ? null : String.join("; ", values);
    }
}
